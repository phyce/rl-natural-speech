package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.chatfilter.ChatFilterPlugin;

@Slf4j
public class SpamDetection {

	private final PluginManager pluginManager;
	private final SpamFilterPluglet spamFilterPluglet;

	private ChatFilterPlugin chatFilterPlugin;
	private Method chatFilterPlugin_censorMessage;
	private static final Pattern chatFilterPlugin_censoredWordPattern = Pattern.compile("\\*+");
	private static final String chatFilterPlugin_censoredMessage =
		"Hey, everyone, I just tried to say something very silly!";


	@Inject
	private SpamDetection(
		PluginManager pluginManager,
		SpamFilterPluglet spamFilterPluglet
	) {
		this.pluginManager = pluginManager;
		this.spamFilterPluglet = spamFilterPluglet;

		// look for spam plugin, if it's already installed
		for (Plugin plugin : pluginManager.getPlugins()) {
			if (plugin instanceof ChatFilterPlugin) {
				log.info("Chat Filter Plugin Detected.");
				chatFilterPlugin = (ChatFilterPlugin) plugin;
			}
		}
	}

	public boolean isSpam(String username, String text) {
		return spamFilterPluglet.isSpam(text) || isSpam_ChatFilter(username, text);
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

}
