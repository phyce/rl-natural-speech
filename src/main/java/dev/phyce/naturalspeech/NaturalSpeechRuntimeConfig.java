package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.utils.OSValidator;
import net.runelite.client.config.ConfigManager;

import java.nio.file.Path;

import static net.runelite.client.config.RuneLiteConfig.GROUP_NAME;

/**
 * Runtime Configs are serialized configurations invisible to the player but used at plugin runtime.
 */
@Singleton
public class NaturalSpeechRuntimeConfig {
	public static final String KEY_TTS_ENGINE_PATH = "ttsEngine";
	private final ConfigManager configManager;
	@Inject
	private NaturalSpeechRuntimeConfig(ConfigManager configManager) {
		this.configManager = configManager;
	}

	public Path getPiperPath() {
		String pathString = configManager.getConfiguration(GROUP_NAME, KEY_TTS_ENGINE_PATH);

		Path path;
		if (pathString != null) path = Path.of(pathString);
		else {
			if (OSValidator.IS_MAC || OSValidator.IS_UNIX) path = Path.of(System.getProperty("user.home")).resolve("piper").resolve("piper");
			else path = Path.of(System.getProperty("user.home")).resolve("piper").resolve("piper.exe");
			setPiperPath(path);
		}

		return path;
	}

	public void setPiperPath(Path path) {
		configManager.setConfiguration(GROUP_NAME, KEY_TTS_ENGINE_PATH, path.toString());
	}

	public void reset() {
		configManager.unsetConfiguration(GROUP_NAME, KEY_TTS_ENGINE_PATH);
		setPiperPath(getPiperPath()); // reset
	}

}
