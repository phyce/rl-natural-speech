package dev.phyce.naturalspeech.utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.configs.ReplacementsJSON;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.SpamDetection;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import static dev.phyce.naturalspeech.utils.LocationUtil.inGrandExchange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.Text;

@Slf4j
@PluginSingleton
public class ChatHelper implements PluginModule {

	private final Client client;
	private final ClientHelper clientHelper;
	private final SpamDetection spamDetection;
	private final NaturalSpeechConfig config;
	private final MuteManager muteManager;
	private final VolumeManager volumeManager;

	public enum ChatType {
		User,
		OtherPlayers,
		System,
		Unknown;
	}

	@Value
	@AllArgsConstructor
	private static class Replacement {
		String match;
		String replacement;
	}

	private final List<Replacement> builtinReplacements = new ArrayList<>();
	private final List<Replacement> customReplacements = new ArrayList<>();

	// for replacement
	private final static List<Character> VALID_MATCH_TAILS = List.of(' ', ',', '!', '.', '?');

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

		loadBuiltInReplacement(PluginResources.BUILT_IN_REPLACEMENTS);
	}

	@Override
	public void startUp() {
		loadCustomReplacements();
	}

//	@Override
//	public void shutDown() {
//	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		if (event.getKey().equals(ConfigKeys.CUSTOM_TEXT_REPLACEMENTS)) {
			log.trace("Detected abbreviation changes, reloading into TextToSpeech");
			loadCustomReplacements();
		}
	}

	public EntityID getEntityID(ChatMessage message) {
		final ChatType chatType = getChatType(message);
		final EntityID eid;
		switch (chatType) {
			case User:
				eid = EntityID.LOCAL_PLAYER;
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
//			case CLAN_MESSAGE:
//				if (config.clanChatEnabled()) return true;
		}
	}

	private static boolean isInnerVoice(@NonNull ChatMessageType messageType) {
		switch (messageType) {
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
			case ITEM_EXAMINE:
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
		if (!TextUtil.containAlphaNumeric(message.getMessage())) {
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

		if (config.muteOtherPlayers() && chatType == ChatType.OtherPlayers) {
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
			case MODCHAT:
				if (!config.publicChatEnabled()) return true;
				break;
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case FRIENDSCHAT:
				if (!config.privateChatEnabled()) return true;
				break;
			case PRIVATECHATOUT:
				if (!config.privateOutChatEnabled()) return true;
				break;

			case OBJECT_EXAMINE:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
				if (!config.examineChatEnabled()) return true;
				break;

			case CLAN_GUEST_CHAT:
				if (!config.clanGuestChatEnabled()) return true;
				break;
			case CLAN_GIM_CHAT:
				if (!config.groupIronmanChatEnabled()) return true;
				break;
			case CLAN_CHAT:
				if (!config.clanChatEnabled()) return true;
				break;
			case CLAN_MESSAGE:
			case CLAN_GIM_MESSAGE:
			case CLAN_GUEST_MESSAGE:
				if (!config.clanChatEnabled()) return true;

			case GAMEMESSAGE:
			case LOGINLOGOUTNOTIFICATION:
			case WELCOME:
			case ENGINE:
				if (!config.systemMesagesEnabled()) return true;
				break;
			case TRADEREQ:
			case CHALREQ_CLANCHAT:
			case CHALREQ_FRIENDSCHAT:
			case CHALREQ_TRADE:
				if (!config.requestsEnabled()) return true;
				break;
			case CONSOLE: //
			case MESBOX: // Used for UI text
				return true;
		}
		return false;
	}


	public boolean isTooCrowded() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;
		@Nullable WorldView topLevelWorldView = client.getTopLevelWorldView();
		if (topLevelWorldView == null) return false;

		int count = (int) topLevelWorldView.players().stream()
				// Exclude the local player themselves
				.filter(player -> !Objects.equals(player, localPlayer))
				// For example, within 15 tiles
				.filter(player -> player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <= 15)
				.count();

		return config.muteCrowds() > 0 && config.muteCrowds() < count;
	}

	public boolean isAreaDisabled() {
		if (client.getLocalPlayer() == null) return false;
		//noinspection RedundantIfStatement
		if (config.muteGrandExchange() && inGrandExchange(client.getLocalPlayer().getWorldLocation())) return true;

		return false;
	}

	@NonNull
	public String standardizeChatMessageText(ChatType chatType, @NonNull ChatMessage message) {
		String text = message.getMessage();
		if (chatType == ChatType.System) {
			text = Text.removeFormattingTags(text);
			text = removeNumericCommas(text);
		}

		text = renderReplacements(text);
		text = TextUtil.renderLargeNumbers(text);
		return text;
	}

	@NonNull
	public String standardizeWidgetText(@NonNull Widget widget) {
		String text = Text.sanitizeMultilineText(widget.getText());
		if (config.enableDialogTextReplacements()) {
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
		text = renderReplacements(text, customReplacements);

		if (config.useBuiltInReplacements()) {
			text = renderReplacements(text, builtinReplacements);
		}

		return text;
	}

	public static String removeNumericCommas(String input) {
		Pattern pattern = Pattern.compile("\\d{1,3}(,\\d{3})+");
		Matcher matcher = pattern.matcher(input);

		StringBuilder result = new StringBuilder();

		while (matcher.find()) {
			String numberWithoutCommas = matcher.group().replace(",", "");
			matcher.appendReplacement(result, numberWithoutCommas);
		}

		matcher.appendTail(result);

		return result.toString();
	}

	/**
	 * In method so we can load again when user changes config
	 */
	public void loadCustomReplacements() {
		customReplacements.clear();

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

			customReplacements.add(new Replacement(match, replace));
		}
	}

	@VisibleForTesting
	public void loadBuiltInReplacement(ReplacementsJSON[] builtInReplacements) {
		builtinReplacements.clear();
		try {
			Arrays.stream(builtInReplacements)
				.forEach(entry -> builtinReplacements.add(new Replacement(entry.acronym, entry.sentence)));
		} catch (JsonSyntaxException e) {
			log.error("Failed to parse built-in abbreviations from Resources.", e);
		}
	}


	private static String renderReplacements(String text, List<Replacement> replacements) {
		// instead of tokenizing, we do a find-and-replace
		// this supports space separated targets to be replaced, for example "multiple words"="OKResult"

		// special characteristic:
		// Rule 1. match head requires to either be start of line or preceded by ' ' space
		// Rule 2. match tail requires to be end of line or ' ' space

		for (Replacement entry : replacements) {

			StringBuilder result = new StringBuilder();

			int prev = 0;
			int head = text.toLowerCase().indexOf(entry.match.toLowerCase());

			while (head != -1) {

				result.append(text, prev, head);

				int tail = head + entry.match.length();
				if ((head == 0 || text.charAt(head - 1) == ' ') && // rule 1
					(tail == text.length() || VALID_MATCH_TAILS.contains(text.charAt(tail))) // rule 2
				) {
					result.append(entry.replacement);
				} else {
					result.append(entry.match);
				}

				prev = tail;
				head = text.indexOf(entry.match, prev);
			}

			if (prev < text.length()) {
				result.append(text, prev, text.length());
			}

			//			log.info("\nREPLACE\t{}\nTEXT\t{}\nRESULT\t{}\n", entry, text, result);
			text = result.toString();
		}

		return text.trim();
	}
}
