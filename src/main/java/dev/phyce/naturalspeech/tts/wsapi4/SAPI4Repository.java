package dev.phyce.naturalspeech.tts.wsapi4;

import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SAPI4Repository {

	private final NaturalSpeechRuntimeConfig runtimeConfig;

	@Getter
	private final List<String> models = new ArrayList<>();

	@Inject
	public SAPI4Repository(NaturalSpeechRuntimeConfig runtimeConfig) {
		this.runtimeConfig = runtimeConfig;

		reload();
	}

	public void reload() {
		models.clear();

		Path sapi4limits = runtimeConfig.getSAPI4Path().resolveSibling("sapi4limits.exe");

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
				.skip(2)
				.map((String key) -> SAPI4ModelCache.sapiToModelName.getOrDefault(key, key))
				.forEach(models::add);
			log.debug("{}", models);
		} catch (IOException e) {
			log.error("Failed to read SAPI4 limits", e);
		}

	}

}
