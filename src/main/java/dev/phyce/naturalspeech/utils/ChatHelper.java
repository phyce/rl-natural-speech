package dev.phyce.naturalspeech.utils;

import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.SpamDetection;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import static dev.phyce.naturalspeech.utils.Locations.inGrandExchange;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

@Slf4j
@PluginSingleton
public class ChatHelper {

	private final Client client;
	private final ClientHelper clientHelper;
	private final SpamDetection spamDetection;
	private final NaturalSpeechConfig config;
	private final MuteManager muteManager;

	private final Map<String, String> abbreviations = new HashMap<>();


	@Inject
	public ChatHelper(
		Client client,
		ClientHelper clientHelper,
		SpamDetection spamDetection,
		NaturalSpeechConfig config,
		MuteManager muteManager
	) {
		this.client = client;
		this.clientHelper = clientHelper;
		this.spamDetection = spamDetection;
		this.config = config;
		this.muteManager = muteManager;
	}

	public boolean isMuted(@NonNull ChatMessage message) {

		if (config.friendsOnlyMode()) {
			switch (message.getType()) {
				case PUBLICCHAT:
				case PRIVATECHAT:
				case CLAN_CHAT:
				case CLAN_GUEST_CHAT:
				case MODCHAT:
					if (!clientHelper.isFriend(message.getName())) {
						return true;
					}
			}
		}
		if (message.getType() == ChatMessageType.AUTOTYPER) return true;
		// dialog messages are handled in onWidgetLoad
		if (message.getType() == ChatMessageType.DIALOG) return true;

		// example: "::::::))))))" (no alpha numeric, muted)
		if (!Texts.containAlphaNumeric(message.getMessage())) {
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

	public static boolean isNPCChatMessage(@NonNull ChatMessage message) {
		// From NPC
		switch (message.getType()) {
			case DIALOG:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
			case MESBOX:
				return true;
		}
		return false;
	}

	public boolean isChatInnerVoice(@NonNull ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
				return Standardize.equals(message.getName(), client.getLocalPlayer());
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

	public boolean isChatOtherPlayerVoice(@NonNull ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
				return Standardize.equals(message.getName(), client.getLocalPlayer());
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

	public boolean isMessageTypeDisabledInConfig(@NonNull ChatMessage message) {
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

	public static boolean isChatSystemVoice(@NonNull ChatMessageType messageType) {
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


	private boolean isTooCrowded() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;

		int count = (int) client.getPlayers().stream()
			.filter(player -> player != localPlayer) // Exclude the local player themselves
			.filter(player -> player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <=
				15) // For example, within 15 tiles
			.count();

		return config.muteCrowds() > 0 && config.muteCrowds() < count;
	}

	private boolean isAreaDisabled() {
		if (client.getLocalPlayer() == null) return false;
		//noinspection RedundantIfStatement
		if (config.muteGrandExchange() && inGrandExchange(client.getLocalPlayer().getWorldLocation())) return true;

		return false;
	}

	private boolean isSelfMuted(ChatMessage message) {
		//noinspection RedundantIfStatement
		if (config.muteSelf() && Standardize.equals(message.getName(), client.getLocalPlayer())) return true;
		return false;
	}

	private boolean isMutingOthers(ChatMessage message) {
		if (ChatHelper.isNPCChatMessage(message)) return false;

		return config.muteOthers() && !Standardize.equals(message.getName(), client.getLocalPlayer());
	}

	private boolean checkMuteLevelThreshold(ChatMessage message) {
		if (ChatHelper.isNPCChatMessage(message)) return false;
		if (Objects.equals(Names.LOCAL_USER, message.getName())) return false;
		if (message.getType() == ChatMessageType.PRIVATECHAT) return false;
		if (message.getType() == ChatMessageType.PRIVATECHATOUT) return false;
		if (message.getType() == ChatMessageType.CLAN_CHAT) return false;
		if (message.getType() == ChatMessageType.CLAN_GUEST_CHAT) return false;
		//noinspection RedundantIfStatement
		if (clientHelper.getLevel(message.getName()) < config.muteLevelThreshold()) return true;


		return false;
	}

	@NonNull
	public String standardizeChatText(@NonNull ChatMessage message) {
		return expandAbbreviations(Text.sanitizeMultilineText(message.getMessage()));
	}

	@NonNull
	public String standardizeWidgetText(@NonNull Widget widget, boolean expand) {
		if (expand) {
			return expandAbbreviations(Text.sanitizeMultilineText(widget.getText()));
		} else {
			return Text.sanitizeMultilineText(widget.getText());
		}
	}

	@NonNull
	public String standardizeOverheadText(@NonNull OverheadTextChanged overhead) {
		return expandAbbreviations(Text.sanitizeMultilineText(overhead.getOverheadText()));
	}

	@NonNull
	public String standardizeText(@NonNull String message) {
		return expandAbbreviations(Text.sanitizeMultilineText(message));
	}

	@NonNull
	public String expandAbbreviations(String text) {
		return Texts.expandAbbreviations(text, abbreviations);
	}

	private void loadBuiltinAbbreviations() {
		try {
			Arrays.stream(PluginResources.BUILT_IN_ABBREVIATIONS)
				.forEach(entry -> abbreviations.put(entry.acronym, entry.sentence));
		} catch (JsonSyntaxException e) {
			log.error("Failed to parse built-in abbreviations from Resources.", e);
		}
	}

	/**
	 * In method so we can load again when user changes config
	 */
	public void loadAbbreviations() {

		if (config.useCommonAbbreviations()) {
			loadBuiltinAbbreviations();
		}

		String phrases = config.customAbbreviations();
		String[] lines = phrases.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) abbreviations.put(parts[0].trim(), parts[1].trim());
		}
	}


	@NonNull
	public Standardize.SID getSID(@NonNull ChatMessage message) {
		if (isChatInnerVoice(message)) {
			return Standardize.LOCAL_PLAYER_SID;
		}
		else if (isChatOtherPlayerVoice(message)) {
			return new Standardize.SID(Standardize.name(message));
		}
		else if (ChatHelper.isChatSystemVoice(message.getType())) {
			return Standardize.SYSTEM_SID;
		}

		throw new RuntimeException("Unknown ChatVoiceType");
	}


	public VoiceType getVoiceType(@NonNull ChatMessage message) {
		if (isChatInnerVoice(message)) {
			return VoiceType.InnerVoice;
		}
		else if (isChatOtherPlayerVoice(message)) {
			return VoiceType.OtherPlayerVoice;
		}
		else if (ChatHelper.isChatSystemVoice(message.getType())) {
			return VoiceType.SystemVoice;
		}
		else {
			return VoiceType.Unknown;
		}
	}

	public enum VoiceType {
		InnerVoice,
		OtherPlayerVoice,
		SystemVoice,
		Unknown
	}
}
