package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.enums.Locations.inGrandExchange;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.tts.AudioLineNames;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.engine.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.VolumeManager;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.Objects;
import java.util.function.Supplier;
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
	private final VolumeManager volumeManager;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;
	private final SpamDetection spamDetection;
	private final ClientThread clientThread;
	private final LastDialogMessage lastDialogMessage = new LastDialogMessage();


	static class LastDialogMessage {
		public String message = "";
		public long timestamp = 0;
	}

	@Inject
	public SpeechEventHandler(
		Client client,
		TextToSpeech textToSpeech,
		NaturalSpeechConfig config,
		VolumeManager volumeManager,
		VoiceManager voiceManager,
		MuteManager muteManager,
		SpamDetection spamDetection,
		ClientThread clientThread
	) {
		this.client = client;
		this.textToSpeech = textToSpeech;
		this.config = config;
		this.volumeManager = volumeManager;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.spamDetection = spamDetection;

		this.clientThread = clientThread;
	}

	@Subscribe(priority=-100)
	private void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (!textToSpeech.isStarted()) return;

		String username;
		String lineName;
		VoiceID voiceId;
		username = Text.standardize(message.getName());
		message.setName(username);
		String text = Text.sanitizeMultilineText(message.getMessage());
		Supplier<Float> volume;

		if (isChatMessageMuted(message)) return;

		try {
			if (isChatInnerVoice(message)) {
				username = AudioLineNames.LOCAL_USER;
				lineName = AudioLineNames.LOCAL_USER;
				voiceId = voiceManager.getVoiceIDFromUsername(username);
				text = textToSpeech.expandAbbreviations(text);
				volume = volumeManager.localplayer();

				log.debug("Inner voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else if (isChatOtherPlayerVoice(message)) {
				// avoid rare (and expensive) usernames colliding with NPC names.
				lineName = AudioLineNames.Username(username);

				Player player = findPlayerWithUsername(username);
				if (isFriend(username)) {
					volume = volumeManager.friend(player);
				}
				else {
					volume = volumeManager.overhead(player);
				}

				voiceId = voiceManager.getVoiceIDFromUsername(username);
				text = textToSpeech.expandAbbreviations(text);

				log.debug("Player voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else if (isChatSystemVoice(message.getType())) {
				username = AudioLineNames.SYSTEM;
				lineName = AudioLineNames.SYSTEM;
				volume = volumeManager.system();

				voiceId = voiceManager.getVoiceIDFromUsername(username);

				long currentTime = System.currentTimeMillis();
				if (lastDialogMessage.message.equals(text)) {
					if ((currentTime - lastDialogMessage.timestamp) < 5000) return;
				}
				lastDialogMessage.timestamp = currentTime;
				lastDialogMessage.message = text;
				log.debug("System voice {} used for {} for {}. ", voiceId, message.getType(), username);

			}
			else {
				log.debug(
					"ChatMessage ignored, didn't match innerVoice, otherPlayerVoice, or SystemVoice. name:{} type:{} message:{}",
					message.getName(), message.getType(), message.getMessage());
				return;
			}
		} catch (VoiceSelectionOutOfOption e) {
			log.error("Voice Selection ran out of options. No suitable active voice found name:{} type:{} message:{}",
				message.getName(), message.getType(), message.getMessage());
			return;
		}

		textToSpeech.speak(voiceId, text, volume, lineName);
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event) {
		if(!config.dialogEnabled())return;
		if (event.getGroupId() == InterfaceID.DIALOG_PLAYER) {
			// InvokeAtTickEnd to wait until the text has loaded in
			clientThread.invokeAtTickEnd(() -> {
				textToSpeech.silence((lineName) -> lineName.equals(AudioLineNames.DIALOG));

				Widget textWidget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
				if (textWidget == null || textWidget.getText() == null) {
					log.error("Player dialog textWidget or textWidget.getText() is null");
					return;
				}
				log.trace("Player dialog textWidget detected:{}", textWidget.getText());
				String text = Text.sanitizeMultilineText(textWidget.getText());
				VoiceID voiceID;
				try {
					voiceID = voiceManager.getVoiceIDFromUsername(AudioLineNames.LOCAL_USER);
				} catch (VoiceSelectionOutOfOption e) {
					throw new RuntimeException(e);
				}

				if (PluginHelper.getConfig().useNpcCustomAbbreviations()) text = textToSpeech.expandAbbreviations(text);
				textToSpeech.speak(voiceID, text, volumeManager.dialog(), AudioLineNames.DIALOG);
			});
		}
		else if (event.getGroupId() == InterfaceID.DIALOG_NPC) {
			// InvokeAtTickEnd to wait until the text has loaded in
			clientThread.invokeAtTickEnd(() -> {
				textToSpeech.silence((lineName) -> lineName.equals(AudioLineNames.DIALOG));

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
				log.trace("NPC dialog textWidget detected:{}", textWidget.getText());


				String text = Text.sanitizeMultilineText(textWidget.getText());
				String npcName = npcNameWidget.getText();
				int npcCompId = headModelWidget.getModelId();

				if (!muteManager.isNpcIdAllowed(npcCompId)) {
					log.debug("NPC Dialogue is muted. CompId:{} NPC name:{}", npcCompId, npcName);
					return;
				}

				VoiceID voiceID;
				try {
					voiceID = voiceManager.getVoiceIDFromNPCId(npcCompId, npcName);
				} catch (VoiceSelectionOutOfOption e) {
					throw new RuntimeException(e);
				}

				if (PluginHelper.getConfig().useNpcCustomAbbreviations()) text = textToSpeech.expandAbbreviations(text);
				textToSpeech.speak(voiceID, text, volumeManager.dialog(), AudioLineNames.DIALOG);
			});
		}
	}

	@Subscribe(priority=-1)
	private void onOverheadTextChanged(OverheadTextChanged event) {
		if (!textToSpeech.isStarted()) return;

		if (event.getActor() instanceof NPC) {
			if (!config.npcOverheadEnabled()) return;
			NPC npc = (NPC) event.getActor();
			if (!muteManager.isNpcAllowed(npc)) return;

			String npcName = Text.standardize(npc.getName());
			String lineName = AudioLineNames.NPCName(npcName);
			Supplier<Float> volume = volumeManager.npc(npc);

			VoiceID voiceID;
			try {
				voiceID = voiceManager.getVoiceIDFromNPCId(npc.getId(), npcName);
				textToSpeech.speak(voiceID, event.getOverheadText(), volume, lineName);
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
			case MESBOX:
				return true;
			default:
				return false;
		}
	}

	public boolean isChatMessageMuted(ChatMessage message) {

		if (config.friendsOnlyMode()) {
			switch (message.getType()) {
				case PUBLICCHAT:
				case PRIVATECHAT:
				case CLAN_CHAT:
				case CLAN_GUEST_CHAT:
				case MODCHAT:
					if (!PluginHelper.isFriend(message.getName())) {
						return true;
					}
			}
		}
		if (message.getType() == ChatMessageType.AUTOTYPER) return true;
		// dialog messages are handled in onWidgetLoad
		if (message.getType() == ChatMessageType.DIALOG) return true;

		// example: "::::::))))))" (no alpha numeric, muted)
		if (!TextUtil.containAlphaNumeric(message.getMessage())) {
			log.trace("Muting message. No alpha numeric characters. Message:{}", message.getMessage());
			return true;
		}
		// console messages seems to be errors and warnings from other plugins, mute
		if (message.getType() == ChatMessageType.CONSOLE) {
			log.trace("Muting console message. Message:{}", message.getMessage());
			return true;
		}

		if (isMessageTypeDisabledInConfig(message)) {
			log.trace("Muting message. Disabled message type {}. Message:{}", message.getType(), message.getMessage());
			return true;
		}

		if (isTooCrowded()) return true;

		if (message.getType() == ChatMessageType.PUBLICCHAT && isAreaDisabled()) {
			log.trace("Muting message. Area is disabled. Message:{}", message.getMessage());
			return true;
		}

		if (isSelfMuted(message)) {
			log.trace("Muting message. Self muted. Message:{}", message.getMessage());
			return true;
		}

		if (isMutingOthers(message)) {
			log.trace("Muting message. Muting others. Message:{}", message.getMessage());
			return true;
		}

		if (checkMuteLevelThreshold(message)) {
			log.trace("Muting message. Mute level threshold. Message:{}", message.getMessage());
			return true;
		}

		if (!muteManager.isUsernameAllowed(Text.standardize(Text.removeTags(message.getName())))) {
			log.trace("Muting message. Username is muted. Message:{}", message.getMessage());
			return true;
		}

		if (spamDetection.isSpam(message.getName(), message.getMessage())) {
			log.trace("Muting message. Spam detected. Message:{}", message.getMessage());
			return true;
		}

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
		log.trace("Number of players around: " + count);
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
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
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

		return config.muteOthers() && !Text.standardize(message.getName()).equals(getLocalPlayerUsername());
	}

	private boolean checkMuteLevelThreshold(ChatMessage message) {
		if (isNPCChatMessage(message)) return false;
		if (Objects.equals(AudioLineNames.LOCAL_USER, message.getName())) return false;
		if (message.getType() == ChatMessageType.PRIVATECHAT) return false;
		if (message.getType() == ChatMessageType.PRIVATECHATOUT) return false;
		if (message.getType() == ChatMessageType.CLAN_CHAT) return false;
		if (message.getType() == ChatMessageType.CLAN_GUEST_CHAT) return false;
		//noinspection RedundantIfStatement
		if (getLevel(message.getName()) < config.muteLevelThreshold()) return true;


		return false;
	}

}
