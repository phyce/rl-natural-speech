package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.enums.Locations.inGrandExchange;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.spamdetection.MessageDuplicateSuppressor;
import dev.phyce.naturalspeech.tts.MagicUsernames;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.WidgetClosed;
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
	private final MuteManager muteManager;
	private final SpamDetection spamDetection;
	private final MessageDuplicateSuppressor duplicateSuppressor;

	private final ClientThread clientThread;

	@Inject
	public SpeechEventHandler(Client client, TextToSpeech textToSpeech, NaturalSpeechConfig config,
							  VoiceManager voiceManager, MuteManager muteManager, SpamDetection spamDetection,
							  MessageDuplicateSuppressor duplicateSuppressor, ClientThread clientThread) {
		this.client = client;
		this.textToSpeech = textToSpeech;
		this.config = config;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.spamDetection = spamDetection;
		this.duplicateSuppressor = duplicateSuppressor;

		this.clientThread = clientThread;
	}

	@Subscribe(priority=-100)
	private void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (!textToSpeech.isAnyEngineRunning()) return;
		log.debug("Message received: " + message.toString());

		String username;
		int distance;
		int volumeBoost = 0;
		VoiceID voiceId;
		username = Text.standardize(message.getName());
		message.setName(username);
		String text = TextUtil.stripChatTags(message.getMessage());

		if (isChatMessageMuted(message)) return;

		SpeechEngine engine = engineFor(message);

		try {
			if (isTwitchMessage(message)) {
				if (!config.twitchVoice().isEmpty()) {
					username = MagicUsernames.TWITCH;
				}
				distance = 0;
				voiceId = voiceManager.getVoiceIDFromUsername(username, engine);
				text = textToSpeech.expandShortenedPhrases(text);

				log.debug("Twitch voice {} used for {}. ", voiceId, username);
			}
			else if (isChatInnerVoice(message)) {
				username = MagicUsernames.LOCAL_USER;
				distance = 0;
				voiceId = voiceManager.getVoiceIDFromUsername(username, engine);
				text = textToSpeech.expandShortenedPhrases(text);
				text = TextUtil.renderLargeNumbers(text);

				log.debug("Inner voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else if (isChatOtherPlayerVoice(message)) {
				distance = config.distanceFadeEnabled()? getDistance(username) : 0;
				if (config.friendsVolumeBoost() > 0 && PluginHelper.isFriend(username)) {
					volumeBoost = config.friendsVolumeBoost();
				}
				voiceId = voiceManager.getVoiceIDFromUsername(username, engine);
				text = textToSpeech.expandShortenedPhrases(text);
				text = TextUtil.renderLargeNumbers(text);

				log.debug("Player voice {} used for {} for {}. ", voiceId, message.getType(), username);
			}
			else if (isChatSystemVoice(message.getType())) {
				username = MagicUsernames.SYSTEM;
				distance = 0;
				text = Text.standardize(text);
				text = TextUtil.renderLargeNumbers(text);
				voiceId = voiceManager.getVoiceIDFromUsername(username, engine);

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

		text = TextUtil.removeNumericCommas(text);
		textToSpeech.speak(voiceId, text, distance, volumeBoost, username);
	}

	@Subscribe(priority=-100)
	private void onWidgetLoaded(WidgetLoaded event) {
		if (!textToSpeech.isAnyEngineRunning()) return;

		if (event.getGroupId() == InterfaceID.DIALOG_PLAYER) {
			SpeechEngine engine = config.playerDialog();
			if (engine.isOff()) return;
			// InvokeAtTickEnd to wait until the text has loaded in
			clientThread.invokeAtTickEnd(() -> {
				if (config.cutOffDialogOnSkip()) {
					textToSpeech.silenceQueue(MagicUsernames.DIALOG);
				}
				Widget textWidget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
				if (textWidget == null || textWidget.getText() == null) {
					log.error("Player dialog textWidget or textWidget.getText() is null");
					return;
				}
				log.trace("Player dialog textWidget detected:{}", textWidget.getText());
				String text = Text.sanitizeMultilineText(textWidget.getText());
				if (config.dialogTextReplacementsEnabled()) {
					text = textToSpeech.expandShortenedPhrases(text);
				}
				VoiceID voiceID;
				try {
					voiceID = voiceManager.getVoiceIDFromUsername(MagicUsernames.LOCAL_USER, engine);
				} catch (VoiceSelectionOutOfOption e) {
					// Nothing to speak with, ex ElevenLabs selected before its voices have loaded.
					// Skipping the line is the intended behaviour; it must not take the tick down.
					log.debug("No {} voice available for player dialog, skipping", engine);
					return;
				}
				textToSpeech.speak(voiceID, text, 0, MagicUsernames.DIALOG);
			});
		} else if (event.getGroupId() == InterfaceID.DIALOG_NPC) {
			SpeechEngine engine = config.npcDialog();
			if (engine.isOff()) return;
			// InvokeAtTickEnd to wait until the text has loaded in
			clientThread.invokeAtTickEnd(() -> {
				if (config.cutOffDialogOnSkip()) {
					textToSpeech.silenceQueue(MagicUsernames.DIALOG);
				}
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
				if (config.dialogTextReplacementsEnabled()) {
					text = textToSpeech.expandShortenedPhrases(text);
				}
				String npcName = npcNameWidget.getText();
				int npcCompId = headModelWidget.getModelId();

				if (!muteManager.isNpcIdAllowed(npcCompId)) {
					log.debug("NPC Dialogue is muted. CompId:{} NPC name:{}", npcCompId, npcName);
					return;
				}

				VoiceID voiceID;
				try {
					voiceID = voiceManager.getVoiceIDFromNPCId(npcCompId, npcName, engine);
				}
				catch (VoiceSelectionOutOfOption e) {
					// Nothing to speak with, ex ElevenLabs selected before its voices have loaded.
					// Skipping the line is the intended behaviour; it must not take the tick down.
					log.debug("No {} voice available for NPC dialog (CompId:{} name:{}), skipping",
						engine, npcCompId, npcName);
					return;
				}

				textToSpeech.speak(voiceID, text, 0, MagicUsernames.DIALOG);
			});
		}
	}

	@Subscribe
	private void onWidgetClosed(WidgetClosed event) {
		if (!config.cutOffDialogOnSkip()) return;
		int group = event.getGroupId();
		if (group == InterfaceID.DIALOG_NPC || group == InterfaceID.DIALOG_PLAYER) {
			textToSpeech.silenceQueue(MagicUsernames.DIALOG);
		}
	}

	@Subscribe(priority=-1)
	private void onOverheadTextChanged(OverheadTextChanged event) {
		if (!textToSpeech.isAnyEngineRunning()) return;

		if (event.getActor() instanceof NPC) {
			SpeechEngine engine = config.npcOverhead();
			if (engine.isOff()) return;
			if (isAreaDisabled()) return;
			NPC npc = (NPC) event.getActor();
			if (!muteManager.isNpcAllowed(npc)) return;
			if (duplicateSuppressor.shouldSuppress("npc:" + npc.getName(), event.getOverheadText())) return;

			int distance = PluginHelper.getActorDistance(event.getActor());

			String text = TextUtil.stripChatTags(event.getOverheadText());
			if (config.dialogTextReplacementsEnabled()) {
				text = textToSpeech.expandShortenedPhrases(text);
			}

			VoiceID voiceID = null;
			try {
				voiceID = voiceManager.getVoiceIDFromNPCId(npc.getId(), npc.getName(), engine);
				textToSpeech.speak(voiceID, text, distance, npc.getName());
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
			case CLAN_GIM_CHAT:
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
			case CLAN_GIM_MESSAGE:
			case CLAN_GUEST_MESSAGE:
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

	private boolean isFriend(ChatMessage message) {
		String name = Text.standardize(message.getName());
		return !name.isEmpty() && client.isFriended(name, false);
	}

	private static boolean isTwitchMessage(ChatMessage message) {
		return "Twitch".equals(message.getSender());
	}

	private static String duplicateSourceKey(ChatMessage message) {
		switch (message.getType()) {
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				return "&examine";
			case TRADEREQ:
				return "tradereq:" + Text.standardize(message.getName());
			default:
				return MagicUsernames.SYSTEM;
		}
	}

	private static final Set<ChatMessageType> ALWAYS_MUTED_TYPES = Collections.unmodifiableSet(EnumSet.of(
		ChatMessageType.ENGINE,
		ChatMessageType.BROADCAST,
		ChatMessageType.IGNORENOTIFICATION,
		ChatMessageType.TRADE,
		ChatMessageType.PLAYERRELATED,
		ChatMessageType.TENSECTIMEOUT,
		ChatMessageType.CLAN_CREATION_INVITATION,
		ChatMessageType.CLAN_GIM_FORM_GROUP,
		ChatMessageType.CLAN_GIM_GROUP_WITH
	));

	public static boolean isAlwaysMuted(ChatMessageType type) {
		return ALWAYS_MUTED_TYPES.contains(type);
	}

	public boolean isChatMessageMuted(ChatMessage message) {
		if (message.getType() == ChatMessageType.AUTOTYPER) return true;
		// dialog messages are handled in onWidgetLoad
		if (message.getType() == ChatMessageType.DIALOG) return true;
		if (isAlwaysMuted(message.getType())) return true;

		if (config.friendsOnlyMode() && isChatOtherPlayerVoice(message) && !isFriend(message)) {
			log.trace("Muting message. Friends-only mode and sender is not a friend. Message:{}", message.getMessage());
			return true;
		}

		if (isTwitchMessage(message) && config.twitchChat().isOff()) return true;

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
			log.debug("Muting message. Disabled message type {}. Message:{}", message.getType(), message.getMessage());
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

		//noinspection RedundantIfStatement
		if ((PluginHelper.isNPCChatMessage(message) || PluginHelper.isSystemMessage(message))
			&& duplicateSuppressor.shouldSuppress(duplicateSourceKey(message), message.getMessage())) {
			return true;
		}

		return false;
	}

	private boolean isTooCrowded() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;

		long count = java.util.stream.StreamSupport.stream(
				client.getTopLevelWorldView().players().spliterator(), false)
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
		return engineFor(message).isOff();
	}

	public SpeechEngine engineFor(ChatMessage message) {
		if (isTwitchMessage(message)) return config.twitchChat();

		switch (message.getType()) {
			case PUBLICCHAT:
			case MODCHAT:
				return config.publicChat();
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				return config.privateChat();
			case PRIVATECHATOUT:
				return config.privateOutChat();
			case FRIENDSCHAT:
				return config.friendsChat();
			case CLAN_CHAT:
				return config.clanChat();
			case CLAN_GUEST_CHAT:
				return config.clanGuestChat();
			case CLAN_GIM_CHAT:
				return config.groupIronmanChat();
			case CLAN_GIM_MESSAGE:
				return SpeechEngine.gated(config.groupIronmanChat(), config.systemMessages());
			case CLAN_MESSAGE:
				return SpeechEngine.gated(config.clanChat(), config.systemMessages());
			case CLAN_GUEST_MESSAGE:
				return SpeechEngine.gated(config.clanGuestChat(), config.systemMessages());
			case LOGINLOGOUTNOTIFICATION:
				return SpeechEngine.gated(config.systemMessages(), config.loginLogout());
			case OBJECT_EXAMINE:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
				return config.examineChat();
			case TRADEREQ:
			case CHALREQ_CLANCHAT:
			case CHALREQ_FRIENDSCHAT:
			case CHALREQ_TRADE:
				return config.requests();
			default:
				return config.systemMessages();
		}
	}

	private boolean isSelfMuted(ChatMessage message) {
		//noinspection RedundantIfStatement
		if (config.muteSelf() && message.getName().equals(PluginHelper.getLocalPlayerUsername())) return true;
		return false;
	}

	private boolean isMutingOthers(ChatMessage message) {
		if (isNPCChatMessage(message) || isSystemMessage(message)) return false;
		return config.muteOthers() && !message.getName().equals(PluginHelper.getLocalPlayerUsername());
	}

	private boolean checkMuteLevelThreshold(ChatMessage message) {
		if (isNPCChatMessage(message) || isSystemMessage(message)) return false;
		if (Objects.equals(MagicUsernames.LOCAL_USER, message.getName())) return false;
		if (message.getType() == ChatMessageType.PRIVATECHAT) return false;
		if (message.getType() == ChatMessageType.PRIVATECHATOUT) return false;
		if (message.getType() == ChatMessageType.CLAN_CHAT) return false;
		if (message.getType() == ChatMessageType.CLAN_GUEST_CHAT) return false;
		if (message.getType() == ChatMessageType.CLAN_GIM_CHAT) return false;
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
