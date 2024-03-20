package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.enums.Locations.inGrandExchange;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.tts.MagicUsernames;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Slf4j
public class SpeechEventHandler {

	private final Client client;
	private final NaturalSpeechConfig config;
	private final TextToSpeech textToSpeech;
	private final VoiceManager voiceManager;

	private final ClientThread clientThread;

	@Inject
	public SpeechEventHandler(Client client, TextToSpeech textToSpeech, NaturalSpeechConfig config,
							  VoiceManager voiceManager, ClientThread clientThread) {
		this.client = client;
		this.textToSpeech = textToSpeech;
		this.config = config;
		this.voiceManager = voiceManager;

		this.clientThread = clientThread;
	}

	@Subscribe(priority=-2)
	private void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (textToSpeech.activePiperProcessCount() == 0) return;
		log.debug("Message received: " + message.toString());

		if (!TextUtil.containAlphaNumeric(message.getMessage())) {
			return;
		}

		String username;
		int distance;
		VoiceID voiceId;
		String text = Text.sanitizeMultilineText(message.getMessage());

		if (isChatMessageMuted(message)) return;

		try {
			if (isChatInnerVoice(message)) {
				username = MagicUsernames.LOCAL_USER;
				distance = 0;
				voiceId = voiceManager.getVoiceIDFromUsername(username);
				text = textToSpeech.expandShortenedPhrases(text);

				log.debug("Inner voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else if (isChatOtherPlayerVoice(message)) {
				username = Text.standardize(message.getName());
				distance = config.distanceFadeEnabled()? getDistance(username) : 0;
				voiceId = voiceManager.getVoiceIDFromUsername(username);
				text = textToSpeech.expandShortenedPhrases(text);

				log.debug("Player voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else if (isChatSystemVoice(message.getType())) {
				username = MagicUsernames.SYSTEM;
				distance = 0;
				voiceId = voiceManager.getVoiceIDFromUsername(username);
				text = message.getMessage();

				log.debug("System voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else {
				log.debug("ChatMessage ignored, didn't match innerVoice, otherPlayerVoice, or SystemVoice. name:{} type:{} message:{}",
					message.getName(), message.getType(), message.getMessage());
				return;
			}
		} catch (VoiceSelectionOutOfOption e) {
			log.error("Voice Selection ran out of options. No suitable active voice found name:{} type:{} message:{}",
				message.getName(), message.getType(), message.getMessage());
			return;
		}

		textToSpeech.speak(voiceId, text, distance, username);
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == InterfaceID.DIALOG_PLAYER) {
			// InvokeAtTickEnd to wait until the text has loaded in
			clientThread.invokeAtTickEnd(() -> {
				Widget textWidget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
				if (textWidget == null || textWidget.getText() == null) {
					log.error("Player dialog textWidget or textWidget.getText() is null");
					return;
				}
				log.debug("Player dialog textWidget detected:{}", textWidget.getText());
				String text = Text.sanitizeMultilineText(textWidget.getText());
				VoiceID voiceID;
				try {
					voiceID = voiceManager.getVoiceIDFromUsername(MagicUsernames.LOCAL_USER);
				} catch (VoiceSelectionOutOfOption e) {
					throw new RuntimeException(e);
				}
				textToSpeech.speak(voiceID, text, 0, MagicUsernames.LOCAL_USER);
			});
		} else if (event.getGroupId() == InterfaceID.DIALOG_NPC) {
			// InvokeAtTickEnd to wait until the text has loaded in
			clientThread.invokeAtTickEnd(() -> {
				Widget textWidget = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
				Widget headModelWidget = client.getWidget(ComponentID.DIALOG_NPC_HEAD_MODEL);
				Widget npcNameWidget = client.getWidget(ComponentID.DIALOG_NPC_NAME);

				if (textWidget == null || textWidget.getText() == null) {
					log.error("NPC dialog textWidget or textWidget.getText() is null");
					return;
				}
				if (headModelWidget == null) {
					log.error("NPC head model textWidget is null");
					return;
				}
				if (npcNameWidget == null) {
					log.error("NPC name textWidget is null");
					return;
				}
				log.debug("NPC dialog textWidget detected:{}", textWidget.getText());

				String text = Text.sanitizeMultilineText(textWidget.getText());
				String npcName = npcNameWidget.getName();
				int npcCompId = headModelWidget.getModelId();
				VoiceID voiceID;
				try {
					voiceID = voiceManager.getVoiceIDFromNPCId(npcCompId, npcName);
				} catch (VoiceSelectionOutOfOption e) {
					throw new RuntimeException(e);
				}

				textToSpeech.speak(voiceID, text, 0, npcName);
			});
		}
	}

	@Subscribe(priority=-1)
	private void onOverheadTextChanged(OverheadTextChanged event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;

		if (event.getActor() instanceof NPC) {
			if (!config.npcOverheadEnabled()) return;
			NPC npc = (NPC) event.getActor();

			if (PluginHelper.isBlockedOrNotAllowed(npc.getName())) return;

			int distance = PluginHelper.getActorDistance(event.getActor());

			VoiceID voiceID = null;
			try {
				voiceID = voiceManager.getVoiceIDFromNPCId(npc.getId(), npc.getName());
				textToSpeech.speak(voiceID, event.getOverheadText(), distance, npc.getName());
			} catch (VoiceSelectionOutOfOption e) {
				log.error(
					"Voice Selection ran out of options for NPC. No suitable active voice found NPC ID:{} NPC name:{}",
					npc.getId(), npc.getName());
			}
		}
	}

	public static boolean isChatInnerVoice(ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
				return Objects.equals(Text.standardize(message.getName()), getLocalPlayerUsername());
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
			case TRADEREQ:
				return true;
			default:
				return false;
		}
	}

	public static boolean isChatOtherPlayerVoice(ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
				return !Objects.equals(Text.standardize(message.getName()), getLocalPlayerUsername());
			case MODCHAT:
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
				//			case TRADEREQ:
				return true;
			default:
				return false;
		}
	}

	public static boolean isChatSystemVoice(ChatMessageType messageType) {
		switch (messageType) {
			case ENGINE:
			case LOGINLOGOUTNOTIFICATION:
			case BROADCAST:
			case IGNORENOTIFICATION:
			case CLAN_MESSAGE:
			case CONSOLE:
			case TRADE:
			case PLAYERRELATED:
			case TENSECTIMEOUT:
			case WELCOME:
			case CLAN_CREATION_INVITATION:
			case CLAN_GIM_FORM_GROUP:
			case CLAN_GIM_GROUP_WITH:
			case GAMEMESSAGE:
				return true;
			default:
				return false;
		}
	}

	public boolean isChatMessageMuted(ChatMessage message) {
		if (message.getType() == ChatMessageType.AUTOTYPER) return true;
		// console messages seems to be errors and warnings from other plugins, mute

		if (message.getType() == ChatMessageType.CONSOLE) return true;
		// dialog messages are handled in onGameTick

		if (message.getType() == ChatMessageType.DIALOG) return true;

		if (isMessageTypeDisabledInConfig(message)) return true;

		if (isTooCrowded()) return true;

		if (checkMuteAllowAndBlockList(message)) return true;

		if (message.getType() == ChatMessageType.PUBLICCHAT && isAreaDisabled()) return true;

		if (isSelfMuted(message)) return true;

		if (isMutingOthers(message)) return true;

		//noinspection RedundantIfStatement
		if (checkMuteLevelThreshold(message)) return true;

		return false;
	}

	private boolean isTooCrowded() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;

		int count = (int) client.getPlayers().stream()
			.filter(player -> player != localPlayer) // Exclude the local player themselves
			.filter(player -> player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <=
				15) // For example, within 15 tiles
			.count();

		if (PluginHelper.getConfig().muteCrowds() > 0 && PluginHelper.getConfig().muteCrowds() < count) return true;
		log.debug("Number of players around: " + count);
		return false;
	}

	private boolean isAreaDisabled() {
		if (client.getLocalPlayer() == null) return false;
		//noinspection RedundantIfStatement
		if (config.muteGrandExchange() && inGrandExchange(client.getLocalPlayer().getWorldLocation())) return true;

		return false;
	}

	public boolean isMessageTypeDisabledInConfig(ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
				if (!config.publicChatEnabled()) return true;
				break;
			case PRIVATECHAT:
				if (!config.privateChatEnabled()) return true;
				break;
			case PRIVATECHATOUT:
				if (!config.privateOutChatEnabled()) return true;
				break;
			case FRIENDSCHAT:
				if (!config.friendsChatEnabled()) return true;
				break;
			case CLAN_CHAT:
				if (!config.clanChatEnabled()) return true;
				break;
			case CLAN_GUEST_CHAT:
				if (!config.clanGuestChatEnabled()) return true;
				break;
			case OBJECT_EXAMINE:
				if (!config.examineChatEnabled()) return true;
				break;
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
				if (!config.systemMesagesEnabled()) return true;
				break;
			case TRADEREQ:
			case CHALREQ_CLANCHAT:
			case CHALREQ_FRIENDSCHAT:
			case CHALREQ_TRADE:
				if (!config.requestsEnabled()) return true;
				break;
		}
		return false;
	}

	private boolean isSelfMuted(ChatMessage message) {
		//noinspection RedundantIfStatement
		if (config.muteSelf() && message.getName().equals(MagicUsernames.LOCAL_USER)) return true;
		return false;
	}

	private boolean isMutingOthers(ChatMessage message) {
		if (isNPCChatMessage(message)) return false;

		return config.muteOthers() && !message.getName().equals(MagicUsernames.LOCAL_USER);
	}

	private boolean checkMuteLevelThreshold(ChatMessage message) {
		if (isNPCChatMessage(message)) return false;
		if (Objects.equals(MagicUsernames.LOCAL_USER, message.getName())) return false;
		if (message.getType() == ChatMessageType.PRIVATECHAT) return false;
		if (message.getType() == ChatMessageType.PRIVATECHATOUT) return false;
		if (message.getType() == ChatMessageType.CLAN_CHAT) return false;
		if (message.getType() == ChatMessageType.CLAN_GUEST_CHAT) return false;
		//noinspection RedundantIfStatement
		if (getLevel(message.getName()) < config.muteLevelThreshold()) return true;


		return false;
	}


	private static int getGroupId(int component) {
		return component >> 16;
	}

	private static int getChildId(int component) {
		return component & '\uffff';
	}

}
