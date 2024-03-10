package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.VOICE_CONFIG_FILE;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceConfig;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.VoiceConfigDatum;
import dev.phyce.naturalspeech.utils.OSValidator;
import net.runelite.client.config.ConfigManager;

import java.nio.file.Path;

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
		if (pathString != null) path = Path.of(pathString);
		else {
			if (OSValidator.IS_MAC || OSValidator.IS_UNIX) path = Path.of(System.getProperty("user.home")).resolve("piper").resolve("piper");
			else path = Path.of(System.getProperty("user.home")).resolve("piper").resolve("piper.exe");
			setPiperPath(path);
		}

		return path;
	}

	public VoiceConfig getCustomVoices() {
		//fetch from config manager
		String json = configManager.getConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE);
		if (json != null) return new VoiceConfig(json);
		return null;
	}

	public void setPiperPath(Path path) {
		configManager.setConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH, path.toString());
	}

	public void reset() {
		configManager.unsetConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH);
		setPiperPath(getPiperPath());
	}
}
