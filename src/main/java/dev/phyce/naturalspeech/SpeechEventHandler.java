package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.enums.Locations.inGrandExchange;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.MagicUsernames;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Slf4j
public class SpeechEventHandler {

	private final Client client;
	private final NaturalSpeechConfig config;
	private final TextToSpeech textToSpeech;
	private final VoiceManager voiceManager;


	private String lastNpcDialogText = "";
	private String lastPlayerDialogText = "";
	private Actor actorInteractedWith = null;

	@Inject
	public SpeechEventHandler(Client client, TextToSpeech textToSpeech, NaturalSpeechConfig config,
							  VoiceManager voiceManager) {
		this.client = client;
		this.textToSpeech = textToSpeech;
		this.config = config;
		this.voiceManager = voiceManager;

	}

	@Subscribe(priority=-2)
	private void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (textToSpeech.activePiperProcessCount() == 0) return;
		log.debug("Message received: " + message.toString());

		patchAndSanitizeChatMessage(message);

		if (isMessageMuted(message)) return;

		VoiceID voiceId;
		int distance;
		String text;

		try {

			if (isChatInnerVoice(message.getType())) {
				distance = 0;
				voiceId = voiceManager.getVoiceIDFromUsername(MagicUsernames.LOCAL_USER);
				text = textToSpeech.expandShortenedPhrases(message.getMessage());
				log.debug("Inner voice {} used for {} for {}. ", voiceId, message.getType(), message.getName());
			}
			else if (isChatOtherPlayerVoice(message.getType())) {
				if (config.distanceFadeEnabled()) {
					distance = getDistance(message.getName());
				}
				else {
					distance = 0;
				}
				voiceId = voiceManager.getVoiceIDFromUsername(message.getName());
				text = textToSpeech.expandShortenedPhrases(message.getMessage());
				log.debug("Player voice {} used for {} for {}. ", voiceId, message.getType(), message.getName());
			}
			else if (isChatSystemVoice(message.getType())) {
				distance = 0;
				voiceId = voiceManager.getSystemVoiceID();

				text = message.getMessage();
				log.debug("System voice {} used for {} for {}. ", voiceId, message.getType(), message.getName());
			}
			else {
//				log.error("Unsupported ChatMessageType for text to speech found: " + message.getType());
//				throw new RuntimeException(
//					"Unsupported ChatMessageType for text to speech found: " + message.getType());
				// Silent mute otherwise
				return;
			}
		} catch (VoiceSelectionOutOfOption e) {
			log.error("Voice Selection ran out of options. No suitable active voice found name:{} type:{}",
				message.getName(), message.getType());
			return;
		}
		if(!text.isEmpty()) textToSpeech.speak(voiceId, text, distance, message.getName());
	}

	@Subscribe(priority=-1)
	private void onGameTick(GameTick event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (!config.dialogEnabled() || actorInteractedWith == null) return;

		int playerGroupId = getGroupId(ComponentID.DIALOG_PLAYER_TEXT);
		//		int playerGroupId = WidgetInfo.DIALOG_PLAYER_TEXT.getGroupId();
		Widget playerDialogTextWidget = client.getWidget(playerGroupId, getChildId(ComponentID.DIALOG_PLAYER_TEXT));

		if (playerDialogTextWidget != null) {
			String dialogText = Text.sanitizeMultilineText(playerDialogTextWidget.getText());
			if (!dialogText.equals(lastPlayerDialogText)) {
				lastPlayerDialogText = dialogText;

				VoiceID voiceID = null;
				try {
					voiceID = voiceManager.getVoiceIdForLocalPlayer();
				} catch (VoiceSelectionOutOfOption e) {
					log.error("Voice Selection ran out of options. No suitable active voice found for: {}", dialogText);
					return;
				}
				textToSpeech.speak(voiceID, dialogText, 0, MagicUsernames.LOCAL_USER);
			}
		}
		else if (!lastPlayerDialogText.isEmpty()) lastPlayerDialogText = "";

		//		int npcGroupId = WidgetInfo.DIALOG_NPC_TEXT.getGroupId();
		int npcGroupId = getGroupId(ComponentID.DIALOG_NPC_TEXT);
		Widget npcDialogTextWidget = client.getWidget(npcGroupId, getChildId(ComponentID.DIALOG_NPC_TEXT));

		if (npcDialogTextWidget != null) {
			String dialogText = Text.sanitizeMultilineText(npcDialogTextWidget.getText());
			if (!dialogText.equals(lastNpcDialogText)) {
				lastNpcDialogText = dialogText;
				Widget nameTextWidget = client.getWidget(npcGroupId, getChildId(ComponentID.DIALOG_NPC_NAME));
				Widget modelWidget = client.getWidget(npcGroupId, getChildId(ComponentID.DIALOG_NPC_HEAD_MODEL));

				if (nameTextWidget != null && modelWidget != null) {
					String npcName = nameTextWidget.getText().toLowerCase();
					int modelId = modelWidget.getModelId();

					VoiceID voiceID = null;
					try {
						voiceID = voiceManager.getVoiceIDFromNPCId(modelId, npcName);
					} catch (VoiceSelectionOutOfOption e) {
						log.error(
							"Voice Selection ran out of options. No suitable active voice found for NPC ID: {} NPC Name:{}",
							modelId, npcName);
						return;
					}
					textToSpeech.speak(voiceID, dialogText, 1, dialogText);
				}
			}
		}
		else if (!lastNpcDialogText.isEmpty()) lastNpcDialogText = "";


	}

	@Subscribe
	private void onInteractingChanged(InteractingChanged event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (event.getTarget() == null || event.getSource() != client.getLocalPlayer()) {
			return;
		}
		// Reset dialog text on new interactions to indicate no active dialog
		lastNpcDialogText = "";
		lastPlayerDialogText = "";

		actorInteractedWith = event.getTarget();
	}

	@Subscribe(priority=-1)
	private void onOverheadTextChanged(OverheadTextChanged event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;

		if (event.getActor() instanceof NPC) {
			if (!config.npcOverheadEnabled()) return;
			NPC npc = (NPC) event.getActor();
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

	/**
	 * EXAMINE has null for name field<br>
	 * DIALOG has name in `name|message` format with null for name field<br>
	 * GAMEMESSAGE & CONSOLE can sometimes have tags which need to be removed<br>
	 * <p>
	 * This method takes in message reference and patches the name field with correct value<br>
	 *
	 * @param message reference passed in and modified
	 */
	private void patchAndSanitizeChatMessage(ChatMessage message) {
		String text;

		text = TextUtil.filterString(message.getMessage());
		text = Text.removeTags(text);
		message.setMessage(text);

		switch (message.getType()) {
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				message.setName(MagicUsernames.LOCAL_USER);
				break;
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
				message.setName(MagicUsernames.SYSTEM);
				break;
			case MODCHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case PUBLICCHAT:
				String standardize_username = Text.standardize(message.getName());

				// replace local player's username with &localuser
				if (Objects.equals(standardize_username, getLocalPlayerUsername())) {
					standardize_username = MagicUsernames.LOCAL_USER;
				}
				message.setName(standardize_username);
				break;
		}
	}

	public static boolean isChatInnerVoice(ChatMessageType messageType) {
		switch (messageType) {
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

	public static boolean isChatOtherPlayerVoice(ChatMessageType messageType) {
		switch (messageType) {
			case MODCHAT:
			case PUBLICCHAT:
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

	public boolean isMessageMuted(ChatMessage message) {
		if (message.getType() == ChatMessageType.AUTOTYPER) return true;
		// console messages seems to be errors and warnings from other plugins, mute
		if (message.getType() == ChatMessageType.CONSOLE) return true;
		// dialog messages are handled in onGameTick
		if (message.getType() == ChatMessageType.DIALOG) return true;
		log.debug("message handled by onMessage");
		if (isMessageTypeDisabledInConfig(message)) return true;
		log.debug("message type not disabled");
		if (isTooCrowded()) return true;
		log.debug("it's not too crowded");
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
			.filter(player -> player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <= 15) // For example, within 15 tiles
			.count();

		if(PluginHelper.getConfig().muteCrowds() > 0 && PluginHelper.getConfig().muteCrowds() < count) return true;
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
			case DIALOG:
				return true;
			//				if (!config.dialogEnabled()) return true;
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
