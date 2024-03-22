package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.chatfilter.ChatFilterPlugin;

@Slf4j
public class SpamDetection {

	private final PluginManager pluginManager;
	private final ConfigManager configManager;


	private Plugin spamFilterPlugin;
	private static final String SPAM_FILTER_GROUP_NAME = "spamfilter";
	private static final String SPAM_FILTER_CONFIG_THRESHOLD_KEY = "threshold";
	private Method spamFilterPlugin_pMessageBad;

	private ChatFilterPlugin chatFilterPlugin;
	private Method chatFilterPlugin_censorMessage;
	private static final Pattern chatFilterPlugin_censoredWordPattern = Pattern.compile("\\*+");
	private static final String chatFilterPlugin_censoredMessage =
		"Hey, everyone, I just tried to say something very silly!";


	@Inject
	private SpamDetection(PluginManager pluginManager, ConfigManager configManager) {
		this.pluginManager = pluginManager;
		this.configManager = configManager;

		// look for spam plugin, if it's already installed
		for (Plugin plugin : pluginManager.getPlugins()) {
			if (plugin.getClass().getSimpleName().equals("SpamFilterPlugin")) {
				log.info("Spam Filter Plugin Detected.");
				spamFilterPlugin = plugin;
			}

			if (plugin instanceof ChatFilterPlugin) {
				log.info("Chat Filter Plugin Detected.");
				chatFilterPlugin = (ChatFilterPlugin) plugin;
			}
		}
	}

	public boolean isSpam(String username, String text) {
		return isSpam_SpamFilterPlugin(text) || isSpam_ChatFilter(username, text);
	}

	private boolean isSpam_SpamFilterPlugin(String text) {
		if (spamFilterPlugin == null || !pluginManager.isPluginEnabled(spamFilterPlugin)) {
			return false;
		}

		if (spamFilterPlugin_pMessageBad == null) {
			try {
				// private float pMessageBad(String text)
				spamFilterPlugin_pMessageBad =
					spamFilterPlugin.getClass().getDeclaredMethod("pMessageBad", String.class);
				spamFilterPlugin_pMessageBad.setAccessible(true);
			} catch (NoSuchMethodException e) {
				log.error("Spam Filter method pMessageBad reflection failed.", e);
				return false;
			}
		}

		String result = configManager.getConfiguration(SPAM_FILTER_GROUP_NAME, SPAM_FILTER_CONFIG_THRESHOLD_KEY);
		if (result == null) {
			log.error("Spam filter did not have {} config value set.", SPAM_FILTER_CONFIG_THRESHOLD_KEY);
			return false;
		}
		float threshold = Float.parseFloat(result) / 100f;
		float spamScore;
		try {
			spamScore = (float) spamFilterPlugin_pMessageBad.invoke(spamFilterPlugin, text.strip());
		} catch (IllegalAccessException | InvocationTargetException e) {
			log.error("Spam Filter private method pMessageBad reflection failed.", e);
			return false;
		}

		return spamScore > threshold;
	}

	private boolean isSpam_ChatFilter(String username, String text) {
		if (chatFilterPlugin == null || !pluginManager.isPluginEnabled(chatFilterPlugin)) {
			return false;
		}

		if (chatFilterPlugin_censorMessage == null) {
			try {
				// private boolean censorMessage(String username, String text)
				chatFilterPlugin_censorMessage =
					chatFilterPlugin.getClass().getDeclaredMethod("censorMessage", String.class, String.class);
				chatFilterPlugin_censorMessage.setAccessible(true);
			} catch (NoSuchMethodException e) {
				log.error("Chat Filter private method censorMessage reflection failed.", e);
				return false;
			}
		}

		try {
			String result = (String) chatFilterPlugin_censorMessage.invoke(chatFilterPlugin, username, text);
			if (result == null || result.equals(chatFilterPlugin_censoredMessage)) {
				return true;
			}
			return chatFilterPlugin_censoredWordPattern.matcher(result).matches();
		} catch (IllegalAccessException | InvocationTargetException e) {
			log.error("Chat Filter method censorMessage reflection failed.", e);
			return false;
		}
	}

	@Subscribe
	private void onPluginChanged(PluginChanged event) {
		// if spam filter was installed after runelite session started
		if (event.getPlugin().getClass().getSimpleName().equals("SpamFilterPlugin")) {
			log.info("Spam Filter Plugin Detected.");
			spamFilterPlugin = event.getPlugin();
		}
	}


}
