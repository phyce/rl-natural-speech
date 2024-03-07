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
	private final ConfigManager configManager;
	@Inject
	private NaturalSpeechRuntimeConfig(ConfigManager configManager) {
		this.configManager = configManager;
	}

	public Path getPiperPath() {
		String pathString = configManager.getConfiguration(GROUP_NAME, "ttsEngine");

		Path path;
		if (pathString != null) path = Path.of(pathString);
		else {
			if (OSValidator.IS_MAC || OSValidator.IS_UNIX) path = Path.of(System.getProperty("user.home") + "/piper");
			else path = Path.of("C:\\piper\\piper.exe");
			setPiperPath(path);
		}

		return path;
	}

	public void setPiperPath(Path path) {
		configManager.setConfiguration(GROUP_NAME, "ttsEngine", path.toString());
	}

}
