package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import net.runelite.client.config.ConfigManager;

@PluginSingleton
public class SpeechManagerConfig {

	private final ConfigManager configManager;
	private static final String SUFFIX = "_enabled";

	@Inject
	public SpeechManagerConfig(ConfigManager configManager) {
		this.configManager = configManager;
	}

	public boolean isEnabled(SpeechEngine engine) {

		// Interesting that passing primitive boolean.class does not trigger compiler/linter warnings,
		// but getConfiguration can return null. Likely will be a runtime exception (didn't test). - Louis
		Boolean enabled = configManager.getConfiguration(CONFIG_GROUP, getKey(engine), Boolean.class);
		// enabled == null: means un-configured, default to true
		return enabled == null || enabled;
	}


	public void setEnable(SpeechEngine engine, boolean enabled) {
		configManager.setConfiguration(CONFIG_GROUP, getKey(engine), enabled);
	}

	private static String getKey(SpeechEngine engine) {
		return engine.getEngineName() + SUFFIX;
	}

}
