package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.utils.PlatformUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class SAPI4Repository {

	private final RuntimePathConfig runtimeConfig;

	@Getter
	@NonNull
	private final List<String> voices = new ArrayList<>();

	@Inject
	public SAPI4Repository(RuntimePathConfig runtimeConfig) {
		this.runtimeConfig = runtimeConfig;

		if (NaturalSpeechPlugin._SIMULATE_NO_TTS || NaturalSpeechPlugin._SIMULATE_MINIMUM_MODE) {
			return;
		}

		if (PlatformUtil.IS_WINDOWS) {
			reload();
		}
	}

	/**
	 * Starts the sapi4limits.exe which interfaces with native Windows Speech API 4 (if installed) to get
	 * available voices.
	 */
	private void reload() {
		voices.clear();

		Path sapi4Path = runtimeConfig.getSAPI4Path();
		Path sapi4limits = sapi4Path.resolveSibling("sapi4limits.exe");

		if (!sapi4limits.toFile().exists()) {
			log.debug("SAPI4 not installed. {} does not exist.", sapi4limits);
			return;
		}

		ProcessBuilder processBuilder = new ProcessBuilder(sapi4limits.toString());

		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			log.error("Failed to start SAPI4 limits, used for fetching available models.", e);
			throw new RuntimeException(e);
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			reader.lines()
				.skip(2) // output header
				.map((String key) -> SAPI4Cache.sapiToVoiceName.getOrDefault(key, key))
				.forEach(voices::add);
			log.debug("{}", voices);
		} catch (IOException e) {
			log.error("Failed to read SAPI4 limits", e);
		}

	}

}
