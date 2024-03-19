package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.nio.file.Path;
import net.runelite.client.config.ConfigManager;

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
		String pathString = configManager.getConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH);

		Path path;
		if (pathString != null) {path = Path.of(pathString);}
		else {
			if (OSValidator.IS_MAC || OSValidator.IS_UNIX) {
				path = Path.of(System.getProperty("user.home")).resolve("piper").resolve("piper");
			}
			else {path = Path.of(System.getProperty("user.home")).resolve("piper").resolve("piper.exe");}
			savePiperPath(path);
		}

		return path;
	}



	public void savePiperPath(Path path) {
		configManager.setConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH, path.toString());
	}

	public void reset() {
		configManager.unsetConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH);
		savePiperPath(getPiperPath());
	}


}
