package dev.phyce.naturalspeech.utils;

import com.google.common.base.Optional;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.SpamDetection;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import static dev.phyce.naturalspeech.utils.Locations.inGrandExchange;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
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
	private final VolumeManager volumeManager;

	private final Map<String, String> builtinReplacements = new HashMap<>();
	private final Map<String, String> userReplacements = new HashMap<>();


	@Inject
	public ChatHelper(
		Client client,
		ClientHelper clientHelper,
		SpamDetection spamDetection,
		NaturalSpeechConfig config,
		MuteManager muteManager,
		VolumeManager volumeManager
	) {
		this.client = client;
		this.clientHelper = clientHelper;
		this.spamDetection = spamDetection;
		this.config = config;
		this.muteManager = muteManager;
		this.volumeManager = volumeManager;

		try {
			Arrays.stream(PluginResources.BUILT_IN_ABBREVIATIONS)
				.forEach(entry -> builtinReplacements.put(entry.acronym, entry.sentence));
		} catch (JsonSyntaxException e) {
			log.error("Failed to parse built-in abbreviations from Resources.", e);
		}
	}

	public EntityID getEntityID(ChatMessage message) {
		final ChatType chatType = getChatType(message);
		final EntityID eid;
		switch (chatType) {
			case User:
				eid = EntityID.USER;
				break;
			case OtherPlayers:
				eid = EntityID.name(message.getName());
				break;
			case System:
				eid = EntityID.SYSTEM;
				break;
			case Unknown:
			default:
				eid = EntityID.SYSTEM;
		}
		return eid;
	}

	public ChatType getChatType(ChatMessage message) {
		final EntityID nameEID = EntityID.name(message.getName());
		if (isChatSystemVoice(message.getType())) {
			return ChatType.System;
		}
		else if (isPlayerChat(message.getType())) {
			if (clientHelper.isLocalPlayer(nameEID)) {
				return ChatType.User;
			}
			else {
				return ChatType.OtherPlayers;
			}
		}
		else if (isInnerVoice(message.getType())) {
			return ChatType.User;
		}
		else {
			return ChatType.Unknown;
		}
	}

	private static boolean isChatSystemVoice(@NonNull ChatMessageType messageType) {
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

	private static boolean isInnerVoice(@NonNull ChatMessageType messageType) {
		switch (messageType) {
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				return true;
			default:
				return false;
		}
	}

	private static boolean isPlayerChat(@NonNull ChatMessageType messageType) {
		switch (messageType) {
			case PUBLICCHAT:
			case MODCHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
			case TRADEREQ:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
			case FRIENDSCHAT:
				return true;
			default:
				return false;
		}
	}

	public boolean isMuted(@NonNull ChatMessage message) {

		ChatType chatType = getChatType(message);
		EntityID eid = getEntityID(message);

		// example: "::::::))))))" (no alpha numeric, muted)
		if (!Texts.containAlphaNumeric(message.getMessage())) {
			log.trace("Muting message. No alpha numeric characters. Message:{}", message);
			return true;
		}

		if (chatType == ChatType.Unknown) return true;

		if (eid.isUser()) {
			return config.muteSelf();
		}

		if (config.friendsOnlyMode() && chatType == ChatType.OtherPlayers && !clientHelper.isFriend(eid)) {
			return true;
		}

		if (isMessageDisabled(message.getType())) {
			log.trace("Muting message. Disabled message type. Message:{}", message);
			return true;
		}

		if (isTooCrowded()) return true;

		if (chatType == ChatType.OtherPlayers && isAreaDisabled()) {
			log.trace("Muting message. Area is disabled. Message:{}", message);
			return true;
		}

		if (config.muteOthers() && chatType == ChatType.OtherPlayers) {
			log.trace("Muting message. Muting others. Message:{}", message);
			return true;
		}

		if (clientHelper.getLevel(eid) < config.muteLevelThreshold()) {
			log.trace("Muting message. Mute level threshold. Message:{}", message);
			return true;
		}

		if (!muteManager.isAllowed(eid)) {
			log.trace("Muting message. Username is muted. Message:{}", message);
			return true;
		}

		if (message.getType() == ChatMessageType.PUBLICCHAT &&
			spamDetection.isSpam(message.getName(), message.getMessage())) {
			log.trace("Muting message. Spam detected. Message:{}", message);
			return true;
		}

		// Optimization: Ignore speech that are too quiet with distance fade
		if (config.distanceFadeEnabled()) {
			Optional<Actor> actor = clientHelper.getActor(eid);
			if (actor.isPresent()) {
				float gain = volumeManager.overhead(actor.get()).get();
				if (gain <= VolumeManager.NOISE_FLOOR) {
					log.trace("(Distance Fade: {}db) {} is too quiet to be audible, ignoring.", gain, eid);
					return true;
				}
			}
		}

		return false;
	}

	private boolean isMessageDisabled(@NonNull ChatMessageType messageType) {
		switch (messageType) {
			case PUBLICCHAT:
				if (!config.publicChatEnabled()) return true;
				break;
			case PRIVATECHAT:
				if (!config.privateChatEnabled()) return true;
				break;
			case PRIVATECHATOUT:
				if (!config.privateOutChatEnabled()) return true;
				break;
			//			case FRIENDSCHAT:
			//				if (!config.friendsChatEnabled()) return true;
			//				break;
			case CLAN_CHAT:
				if (!config.clanChatEnabled()) return true;
				break;
			case CLAN_GUEST_CHAT:
				if (!config.clanGuestChatEnabled()) return true;
				break;
			case CLAN_GIM_CHAT:
				if (!config.groupIronmanChatEnabled()) return true;
				break;
			case OBJECT_EXAMINE:
				if (!config.examineChatEnabled()) return true;
				break;
			case WELCOME:
			case LOGINLOGOUTNOTIFICATION:
			case GAMEMESSAGE:
			case CLAN_MESSAGE:
			case CLAN_GIM_MESSAGE:
			case CLAN_GUEST_MESSAGE:
				if (!config.systemMesagesEnabled()) return true;
				break;
			case TRADEREQ:
			case CHALREQ_CLANCHAT:
			case CHALREQ_FRIENDSCHAT:
			case CHALREQ_TRADE:
				if (!config.requestsEnabled()) return true;
				break;
			case CONSOLE: //
				return true;
		}
		return false;
	}


	private boolean isTooCrowded() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;

		int count = (int) client.getPlayers().stream()
			// Exclude the local player themselves
			.filter(player -> player != localPlayer)
			// For example, within 15 tiles
			.filter(player -> player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <= 15)
			.count();

		return config.muteCrowds() > 0 && config.muteCrowds() < count;
	}

	private boolean isAreaDisabled() {
		if (client.getLocalPlayer() == null) return false;
		//noinspection RedundantIfStatement
		if (config.muteGrandExchange() && inGrandExchange(client.getLocalPlayer().getWorldLocation())) return true;

		return false;
	}

	@NonNull
	public String standardizeChatMessageText(@NonNull ChatMessage message) {
		String text = renderReplacements(message.getMessage());
		text = Texts.renderLargeNumbers(text);
		return text;
	}

	@NonNull
	public String standardizeWidgetText(@NonNull Widget widget, boolean replace) {
		String text = Text.sanitizeMultilineText(widget.getText());
		if (replace) {
			text = renderReplacements(text);
		}
		return text;

	}

	@NonNull
	public String standardizeOverheadText(@NonNull OverheadTextChanged overhead) {
		return renderReplacements(Text.sanitizeMultilineText(overhead.getOverheadText()));
	}

	@NonNull
	public String renderReplacements(String text) {
		// apply user replacements first
		text = Texts.renderReplacements(text, userReplacements);

		if (config.useBuiltInReplacements()) {
			text = Texts.renderReplacements(text, builtinReplacements);
		}

		return text;
	}

	/**
	 * In method so we can load again when user changes config
	 */
	public void loadUserReplacements() {

		String[] lines = config.customTextReplacements().split("\n");
		for (String line : lines) {
			// last index of = instead of string split by =
			// This support "=_=" to "squint face" replacement

			int index = line.lastIndexOf("=");
			if (index == -1) continue;

			// we use Jagex style escapes for special characters because this is what ChatMessage will contain
			// For example <3 will become <lt>3 in ChatMessage
			String match = Text.escapeJagex(line.substring(0, index)).trim();
			if (match.isEmpty()) continue;

			// index + 1 to skip the "="
			String replace = line.substring(index + 1).trim();
			// replacement allowed to be empty, so user can replace with empty (removal).
			// if (replace.isEmpty()) continue;

			userReplacements.put(match, replace);
		}
	}

	public enum ChatType {
		User,
		OtherPlayers,
		System,
		Unknown
	}
}
