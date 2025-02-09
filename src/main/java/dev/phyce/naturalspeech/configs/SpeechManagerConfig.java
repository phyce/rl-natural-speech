package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.engine.PiperEngine;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import net.runelite.client.config.ConfigManager;

@PluginSingleton
public class SpeechManagerConfig {

	private final ConfigManager configManager;
	private final PiperConfig piperConfig;
	public static final String SUFFIX = "_enabled";

	@Inject
	public SpeechManagerConfig(
		ConfigManager configManager,
		PiperConfig piperConfig
	) {
		this.configManager = configManager;
		this.piperConfig = piperConfig;
	}

	public boolean isEnabled(SpeechEngine engine) {
		Boolean result = false;
		// Interesting that passing primitive boolean.class does not trigger compiler/linter warnings,
		// but getConfiguration can return null. Likely will be a runtime exception (didn't test). - Louis
		if(engine instanceof PiperEngine){
			result = piperConfig.isEnabled(engine.getEngineName());
		} else {
			result = configManager.getConfiguration(CONFIG_GROUP, getKey(engine), Boolean.class);
			if(result == null) return false;
		}

		return result;
	}


	public void setEnable(SpeechEngine engine, boolean enabled) {
		configManager.setConfiguration(CONFIG_GROUP, getKey(engine), enabled);
	}

	private static String getKey(SpeechEngine engine) {
		return engine.getEngineName() + SUFFIX;
	}

}
