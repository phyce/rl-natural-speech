package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.events.piper.PiperPathChanged;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.nio.file.Path;
import net.runelite.client.config.ConfigManager;

/**
 * Runtime Configs are serialized configurations invisible to the player but used at plugin runtime.
 */
@PluginSingleton
public class NaturalSpeechRuntimeConfig {
	public static final String KEY_TTS_ENGINE_PATH = "ttsEngine";
	private final ConfigManager configManager;
	private final PluginEventBus pluginEventBus;

	@Inject
	private NaturalSpeechRuntimeConfig(
		ConfigManager configManager, PluginEventBus pluginEventBus

	) {
		this.configManager = configManager;
		this.pluginEventBus = pluginEventBus;
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

	public Path getSAPI4Path() {
		//FIXME(Louis): Use piper path for now
		if (OSValidator.IS_WINDOWS) {
			return getPiperPath().resolveSibling("SAPI4").resolve("sapi4out.exe");
		} else {
			throw new RuntimeException("Windows Speech API4.0 not supported on this operating system");
		}
	}

	public void savePiperPath(Path path) {
		configManager.setConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH, path.toString());
		pluginEventBus.post(new PiperPathChanged(path));
	}

	public void reset() {
		configManager.unsetConfiguration(CONFIG_GROUP, KEY_TTS_ENGINE_PATH);
		savePiperPath(getPiperPath());
	}


}
