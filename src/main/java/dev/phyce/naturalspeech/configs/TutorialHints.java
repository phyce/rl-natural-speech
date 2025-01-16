package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

public class TutorialHints implements PluginModule {
	private final ConfigManager configManager;

	@Inject
	public TutorialHints(ConfigManager configManager) {
		this.configManager = configManager;
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		if (event.getKey().equals(ConfigKeys.DEVELOPER_RESET_HINTS)) {
			for (String key : ConfigKeys.Hints.ALL) {
				configManager.setConfiguration(CONFIG_GROUP, key, "false");
			}
		}
	}
}
