package dev.phyce.naturalspeech.spamdetection;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import static net.runelite.api.ChatMessageType.*;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.MessageNode;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;

// A "pluglet" is defined as a small or simplified version of a plugin.
// Only things needed (as a bare minimum) to re-implement a single function
// Hub rules disallows reflection, so Natural Speech re-implements the code, but uses ChatFilters' user configs.
@Slf4j
@Singleton
public class ChatFilterPluglet {

	private static final Splitter NEWLINE_SPLITTER = Splitter
		.on("\n")
		.omitEmptyStrings()
		.trimResults();

	private static final Set<ChatMessageType> COLLAPSIBLE_MESSAGETYPES = ImmutableSet.of(
		ENGINE,
		GAMEMESSAGE,
		ITEM_EXAMINE,
		NPC_EXAMINE,
		OBJECT_EXAMINE,
		SPAM,
		PUBLICCHAT,
		MODCHAT
	);

	private final CharMatcher jagexPrintableCharMatcher = Text.JAGEX_PRINTABLE_CHAR_MATCHER;
	private List<Pattern> filteredPatterns = Collections.emptyList();
	private List<Pattern> filteredNamePatterns = Collections.emptyList();

	private static class Duplicate {
		int messageId;
		int count;
	}

	private final LinkedHashMap<String, Duplicate> duplicateChatCache = new LinkedHashMap<>() {
		private static final int MAX_ENTRIES = 100;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Duplicate> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	private final Client client;
	private final ConfigManager configManager;

	private boolean isChatFilterEnabled;

	// We avoid using ChatFilterConfig directly to future-proof Natural Speech against ChatFilter updates
	private final ProxyConfig config;
	// region proxyconfig implementation
	private static final String ChatFilterClassName = "chatfilterplugin";
	private static final String CHAT_FILTER_GROUP_NAME = "chatfilter";
	private static final class ConfigKeys {
		private final static String maxRepeatedPublicChats = "maxRepeatedPublicChats";
		private final static String filterFriends = "filterFriends";
		private final static String filterFriendsChat = "filterClan";
		private final static String filterClanChat = "filterClanChat";
		private final static String filteredWords = "filteredWords";
		private final static String filteredRegex = "filteredRegex";
		private final static String filteredNames = "filteredNames";
		private final static String stripAccents = "stripAccents";
	}
	@SuppressWarnings("SameParameterValue")
	private class ProxyConfig {
		private boolean getBoolean(String key, Boolean defaultValue) {
			String result = configManager.getConfiguration(CHAT_FILTER_GROUP_NAME, key);
			if (result != null) {
				return Boolean.parseBoolean(result);
			}
			log.trace("{} doesn't exist in configManager, likely ChatFilter has never been enabled.", key);
			return defaultValue;
		}

		private int getInteger(String key, int defaultValue) {
			String result = configManager.getConfiguration(CHAT_FILTER_GROUP_NAME, key);
			if (result != null) {
				return Integer.parseInt(result);
			}
			log.trace("{} doesn't exist in configManager, likely ChatFilter has never been enabled.", key);
			return defaultValue;
		}

		private String getString(String key, String defaultValue) {
			String result = configManager.getConfiguration(CHAT_FILTER_GROUP_NAME, key);
			if (result != null) {
				return result;
			}
			log.trace("{} doesn't exist in configManager, likely ChatFilter has never been enabled.", key);
			return defaultValue;
		}

		public int maxRepeatedPublicChats() {
			return getInteger(ConfigKeys.maxRepeatedPublicChats, 0);
		}

		public boolean filterFriends() {
			return getBoolean(ConfigKeys.filterFriends, false);
		}

		public boolean filterFriendsChat() {
			return getBoolean(ConfigKeys.filterFriendsChat, false);
		}

		public boolean filterClanChat() {
			return getBoolean(ConfigKeys.filterClanChat, false);
		}

		public String filteredWords() {
			return getString(ConfigKeys.filteredWords, "");
		}

		public String filteredRegex() {
			return getString(ConfigKeys.filteredRegex, "");
		}

		public String filteredNames() {
			return getString(ConfigKeys.filteredNames, "");
		}

		public boolean stripAccents() {
			return getBoolean(ConfigKeys.stripAccents, false);
		}
	}
	// endregion

	@Inject
	public ChatFilterPluglet(Client client, ConfigManager configManager) {
		this.client = client;
		this.configManager = configManager;
		this.config = new ProxyConfig();

		// built-in plugin, guaranteed to exist and be valid bool
		this.isChatFilterEnabled = Boolean.parseBoolean(
			configManager.getConfiguration("runelite", ChatFilterClassName));

		updateFilteredPatterns();
	}

	public boolean isSpam(final String username, final String message) {
		if (!isChatFilterEnabled) return false;

		if (username == null) {
			return false;
		}
		else {
			if (!canFilterPlayer(username)) {
				return false;
			}
			if (isNameFiltered(username)) {
				return true;
			}
		}

		Duplicate duplicateCacheEntry = duplicateChatCache.get(username + ":" + message);
//		log.trace("Duplicate chat entry count:{} for ({})", duplicateCacheEntry.count, duplicateCacheEntry);
		if (config.maxRepeatedPublicChats() > 0 && duplicateCacheEntry.count > config.maxRepeatedPublicChats()) {
//			log.trace("Duplicate chat filtered ({})", duplicateCacheEntry);
			return true;
		}

		String strippedMessage = jagexPrintableCharMatcher.retainFrom(message)
			.replace('\u00A0', ' ')
			.replaceAll("<lt>", "<")
			.replaceAll("<gt>", ">");
		String strippedAccents = stripAccents(strippedMessage);


		for (Pattern pattern : filteredPatterns) {
			Matcher m = pattern.matcher(strippedAccents);
			if (m.find()) {
				return true;
			}
		}

		return false;
	}

	@Subscribe
	private void onPluginChanged(PluginChanged event) {
		// if spam filter was installed after runelite session started
		if (event.getPlugin().getName().equals("Chat Filter") ) {
			if (event.isLoaded()) {
				updateFilteredPatterns();
				log.trace("Detected ChatFilterPlugin activated");
				isChatFilterEnabled = true;
			}
			else {
				filteredPatterns = Collections.emptyList();
				filteredNamePatterns = Collections.emptyList();
				duplicateChatCache.clear();
				log.trace("Detected ChatFilterPlugin deactivated");
				isChatFilterEnabled = false;
			}
		}
	}

	@Subscribe(priority=-2) // run after ChatMessageManager
	public void onChatMessage(ChatMessage chatMessage) {
		if (!isChatFilterEnabled) return;

		if (COLLAPSIBLE_MESSAGETYPES.contains(chatMessage.getType())) {
			final MessageNode messageNode = chatMessage.getMessageNode();
			// remove and re-insert into map to move to end of list
			final String key = messageNode.getName() + ":" + messageNode.getValue();
			Duplicate duplicate = duplicateChatCache.remove(key);
			if (duplicate == null) {
				duplicate = new Duplicate();
			}

			duplicate.count++;
			duplicate.messageId = messageNode.getId();
			duplicateChatCache.put(key, duplicate);
		}
	}
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (!isChatFilterEnabled) return;

		switch (gameStateChanged.getGameState())
		{
			// Login drops references to all messages and also resets the global message id counter.
			// Invalidate the message id so it doesn't collide later when rebuilding the chatfilter.
			case CONNECTION_LOST:
			case HOPPING:
			case LOGGING_IN:
				duplicateChatCache.values().forEach(d -> d.messageId = -1);
		}
	}
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!CHAT_FILTER_GROUP_NAME.equals(event.getGroup())) {
			return;
		}

		updateFilteredPatterns();
	}

	boolean canFilterPlayer(String playerName) {
		boolean isMessageFromSelf = playerName.equals(client.getLocalPlayer().getName());
		return !isMessageFromSelf &&
			(config.filterFriends() || !client.isFriended(playerName, false)) &&
			(config.filterFriendsChat() || !isFriendsChatMember(playerName)) &&
			(config.filterClanChat() || !isClanChatMember(playerName));
	}

	private boolean isFriendsChatMember(String name) {
		FriendsChatManager friendsChatManager = client.getFriendsChatManager();
		return friendsChatManager != null && friendsChatManager.findByName(name) != null;
	}

	private boolean isClanChatMember(String name) {
		ClanChannel clanChannel = client.getClanChannel();
		if (clanChannel != null && clanChannel.findMember(name) != null) {
			return true;
		}

		clanChannel = client.getGuestClanChannel();
		//noinspection RedundantIfStatement
		if (clanChannel != null && clanChannel.findMember(name) != null) {
			return true;
		}

		return false;
	}

	private void updateFilteredPatterns() {
		log.trace("Updating filtered patterns");
		List<Pattern> patterns = new ArrayList<>();
		List<Pattern> namePatterns = new ArrayList<>();

		Text.fromCSV(config.filteredWords()).stream()
			.map(this::stripAccents)
			.map(s -> Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
			.forEach(patterns::add);

		//noinspection UnstableApiUsage
		NEWLINE_SPLITTER.splitToList(config.filteredRegex()).stream()
			.map(this::stripAccents)
			.map(ChatFilterPluglet::compilePattern)
			.filter(Objects::nonNull)
			.forEach(patterns::add);

		//noinspection UnstableApiUsage
		NEWLINE_SPLITTER.splitToList(config.filteredNames()).stream()
			.map(this::stripAccents)
			.map(ChatFilterPluglet::compilePattern)
			.filter(Objects::nonNull)
			.forEach(namePatterns::add);

		filteredPatterns = patterns;
		filteredNamePatterns = namePatterns;

	}

	private String stripAccents(String input) {
		return config.stripAccents() ? StringUtils.stripAccents(input) : input;
	}

	private static Pattern compilePattern(String pattern) {
		try {
			return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException ex) {
			return null;
		}
	}

	private boolean isNameFiltered(final String playerName) {
		String sanitizedName = Text.standardize(playerName);
		for (Pattern pattern : filteredNamePatterns) {
			Matcher m = pattern.matcher(sanitizedName);
			if (m.find()) {
				return true;
			}
		}
		return false;
	}


}
