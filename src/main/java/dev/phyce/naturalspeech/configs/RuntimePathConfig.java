package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.events.PiperPathChanged;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.PluginPaths;
import dev.phyce.naturalspeech.utils.Platforms;
import java.nio.file.Path;
import net.runelite.client.config.ConfigManager;

/**
 * Runtime Configs are serialized configurations invisible to the player but used at plugin runtime.
 */
@PluginSingleton
public class RuntimePathConfig {
	private final ConfigManager configManager;
	private final PluginEventBus pluginEventBus;

	@Inject
	private RuntimePathConfig(
		ConfigManager configManager,
		PluginEventBus pluginEventBus
	) {
		this.configManager = configManager;
		this.pluginEventBus = pluginEventBus;
	}

	public Path getPiperPath() {

		//noinspection deprecation
		String deprecatedPiperPath =
			configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH);

		Path path = getDefaultPath();

		// If the user has installed Natural Speech, favor the installed piper and return the path
		if (path.toFile().exists()) {
			return path;
		}
		else if (deprecatedPiperPath != null) {
			// If the user has not installed using installer, try the deprecated custom piper path
			return Path.of(deprecatedPiperPath);
		}
		else {
			// Natural Speech not installed and did not have a custom piper path set
			// We just return the default path and let the user know they need to install Natural Speech
			return path;
		}
	}

	private static Path getDefaultPath() {
		Path path;
		if (Platforms.IS_MAC || Platforms.IS_UNIX) {
			path = PluginPaths.NATURAL_SPEECH_PATH
				.resolve("piper") // piper folder
				.resolve("piper"); // piper executable;
		}
		else {
			path = PluginPaths.NATURAL_SPEECH_PATH
				.resolve("piper")
				.resolve("piper.exe");
		}
		return path;
	}

	public Path getSAPI4Path() {
		return PluginPaths.NATURAL_SPEECH_PATH
			.resolve("sapi4out")
			.resolve("sapi4out.exe");
	}

	@Deprecated(
		since="1.3.0 We have an installer which installs to a standard location, transitioning old user configs.")
	public void savePiperPath(Path path) {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH,
			path.toString());
		pluginEventBus.post(new PiperPathChanged(path));
	}

	public void reset() {
		//noinspection deprecation
		configManager.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH);
	}


}
