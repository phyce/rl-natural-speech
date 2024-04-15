package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.PluginEventBus;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.events.piper.PiperPathChanged;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.nio.file.Path;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

/**
 * Runtime Configs are serialized configurations invisible to the player but used at plugin runtime.
 */
@PluginSingleton
public class NaturalSpeechRuntimeConfig {
	public static final String KEY_DEPRECATED_PIPER_PATH = "ttsEngine";
	private final ConfigManager configManager;
	private final PluginEventBus pluginEventBus;

	@Inject
	private NaturalSpeechRuntimeConfig(
		ConfigManager configManager,
		PluginEventBus pluginEventBus
	) {
		this.configManager = configManager;
		this.pluginEventBus = pluginEventBus;
	}

	public Path getPiperPath() {

		String deprecatedPiperPath = configManager.getConfiguration(CONFIG_GROUP, KEY_DEPRECATED_PIPER_PATH);

		Path path;
		if (OSValidator.IS_MAC || OSValidator.IS_UNIX) {
			path = getNaturalSpeechPath()
				.resolve("piper")
				.resolve("piper");
		}
		else {
			path = getNaturalSpeechPath()
				.resolve("piper")
				.resolve("piper.exe");
		}

		// Favor piper in the installed location
		if (path.toFile().exists()) {
			return path;
		}
		// If user has not installed using installer, try deprecated custom piper path
		else if (deprecatedPiperPath != null) {
			return Path.of(deprecatedPiperPath);
		}
		// Not installed and did not have custom piper path
		else {
			return path;
		}
	}

	public Path getNaturalSpeechPath() {
		return RuneLite.RUNELITE_DIR.toPath().resolve("NaturalSpeech");
	}

	public Path getSAPI4Path() {
		return getNaturalSpeechPath()
			.resolve("sapi4out")
			.resolve("sapi4out.exe");
	}

	public void savePiperPath(Path path) {
		configManager.setConfiguration(CONFIG_GROUP, KEY_DEPRECATED_PIPER_PATH, path.toString());
		pluginEventBus.post(new PiperPathChanged(path));
	}

	public void reset() {
		configManager.unsetConfiguration(CONFIG_GROUP, KEY_DEPRECATED_PIPER_PATH);
	}


}
