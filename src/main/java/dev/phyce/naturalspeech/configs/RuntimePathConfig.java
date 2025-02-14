package dev.phyce.naturalspeech.configs;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.events.PiperPathChanged;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.PluginPaths;
import dev.phyce.naturalspeech.utils.PlatformUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime Configs are serialized configurations invisible to the player but used at plugin runtime.
 */
@PluginSingleton
public class RuntimePathConfig implements PluginModule {
	private static final Logger log = LoggerFactory.getLogger(RuntimePathConfig.class);
	private final ConfigManager configManager;
	private final PluginEventBus pluginEventBus;
	public static final Path SAPI4_PATH = PluginPaths.NATURAL_SPEECH_PATH
		.resolve("sapi4out")
		.resolve("sapi4out.exe");

	@Inject
	private RuntimePathConfig(
		ConfigManager configManager,
		PluginEventBus pluginEventBus
	) {
		this.configManager = configManager;
		this.pluginEventBus = pluginEventBus;
	}

	public Path getPiperPath() {

		String deprecatedPiperPath =
			configManager.getConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH);

		Path path = getDefaultPath();

		// If the user has installed Natural Speech, favor the installed piper and return the path
		if (path.toFile().exists()) {
			if(deprecatedPiperPath != null) {
				migrateModels();
			}
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

	private void migrateModels()
	{
		String deprecatedPiperPath = configManager.getConfiguration(
			NaturalSpeechPlugin.CONFIG_GROUP,
			ConfigKeys.DEPRECATED_PIPER_PATH
		);
		if (deprecatedPiperPath == null) return;

		Path deprecatedPiperDir = Path.of(deprecatedPiperPath).getParent();
		if (deprecatedPiperDir == null)
		{
			log.error("Invalid deprecated Piper path: " + deprecatedPiperPath);
			return;
		}

		Path oldModelsPath = deprecatedPiperDir.resolve("models");
		Path newModelsPath = getDefaultPath().getParent().resolve("models");

		try
		{
			if (!Files.exists(oldModelsPath)) return;

			if (oldModelsPath.equals(newModelsPath))
			{
				configManager.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH);
				return;
			}

			Files.createDirectories(newModelsPath);

			try (Stream<Path> files = Files.walk(oldModelsPath))
			{
				files.forEach(source -> {
					try
					{
						Path relativePath = oldModelsPath.relativize(source);
						Path destination = newModelsPath.resolve(relativePath);

						if (Files.isDirectory(source)) Files.createDirectories(destination);
						else Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
					}
					catch (IOException e)
					{
						log.error("Failed to move: " + source + " -> " + e.getMessage());
					}
				});
			}

			configManager.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH);

			try (Stream<Path> paths = Files.walk(oldModelsPath))
			{
				paths
					.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try
						{
							Files.delete(path);
						}
						catch (IOException e)
						{
							log.error("Failed to delete: " + path + " -> " + e.getMessage());
						}
					});
			}
		}
		catch (IOException e)
		{
			log.error("Failed to migrate models folder: " + e.getMessage());
			//e.printStackTrace();
		}
	}


	private static Path getDefaultPath() {
		Path path;
		if (PlatformUtil.IS_MAC || PlatformUtil.IS_UNIX) {
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

	@Deprecated(
		since="2.0.0 We have an installer which installs to a standard location, transitioning old user configs.")
	public void savePiperPath(Path path) {
		configManager.setConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH,
			path.toString());
		pluginEventBus.post(new PiperPathChanged(path));
	}

	public void reset() {
		configManager.unsetConfiguration(NaturalSpeechPlugin.CONFIG_GROUP, ConfigKeys.DEPRECATED_PIPER_PATH);
	}


	public boolean isPiperPathValid() {

		File piper_file = getPiperPath().toFile();

		if (PlatformUtil.IS_WINDOWS) {
			String filename = piper_file.getName();
			// naive canExecute check for windows, 99.99% of humans use .exe extension for executables on Windows
			// File::canExecute returns true for all files on Windows.
			return filename.endsWith(".exe") && piper_file.exists() && !piper_file.isDirectory();
		}
		else {
			return piper_file.canExecute() && piper_file.exists() && !piper_file.isDirectory();
		}
	}
}
