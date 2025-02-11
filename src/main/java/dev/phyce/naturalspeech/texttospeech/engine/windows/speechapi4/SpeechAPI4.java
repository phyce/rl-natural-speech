package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4;

import static com.google.common.base.Preconditions.checkState;
import dev.phyce.naturalspeech.texttospeech.Gender;
import dev.phyce.naturalspeech.texttospeech.engine.Audio;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeechAPI4 {

	@Getter
	private final String voiceName;
	@Getter
	private final int speed;
	@Getter
	private final int pitch;
	@Getter
	private final Gender gender;

	private final Path sapi4Path;
	private final File outputFolder;

	private SpeechAPI4(
		String voiceName,
		Path sapi4Path,
		Path outputPath,
		int speed,
		int pitch,
		Gender gender
	) {
		this.voiceName = voiceName;
		this.sapi4Path = sapi4Path;
		this.speed = speed;
		this.pitch = pitch;
		this.gender = gender;
		this.outputFolder = outputPath.toFile();

		if (!outputFolder.exists()) {
			boolean ignored = outputFolder.mkdir();
		}
	}

	@NonNull
	public static Result<SpeechAPI4, Exception> build(@NonNull String voiceName, @NonNull Path sapi4Path) {

		String sapiName = SAPI4Cache.voiceToSapiName.getOrDefault(voiceName, voiceName);
		checkState(sapiName != null);

		int speed;
		int pitch;
		Gender gender;

		if (SAPI4Cache.isCached(sapiName)) {
			SAPI4Cache cached = SAPI4Cache.findSapiName(sapiName);
			checkState(cached != null);

			log.trace("Found SAPI4 Model cache for {}", cached);
			speed = cached.defaultSpeed;
			pitch = cached.defaultPitch;

			// manually labeled gender in cache
			gender = cached.gender;
		}
		else {
			// SAPI4 models don't have gender information (maybe they do)
			// So we assume other.
			gender = Gender.OTHER;

			ProcessBuilder processBuilder = new ProcessBuilder(
				sapi4Path.resolveSibling("sapi4limits.exe").toString(),
				sapiName
			);

			Process process;
			try {
				log.trace("Starting {}", processBuilder.command());
				process = processBuilder.start();
			} catch (IOException e) {
				return Error(new RuntimeException("Failed to start SAPI4 limits", e));
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				var limits = reader.lines().collect(Collectors.toList());
				// When the model was not found or installed, limit only prints 2 lines
				if (limits.size() <= 2) {
					if (limits.get(1).contains("(null)")) {
						// lost guava v25.1 Strings.lenientFormat downgrading to v23
						return Error(new RuntimeException(String.format(
							"Windows Speech API 4 is not installed, cannot launch %s", sapiName)));
					}
					else {
						return Error(new IllegalStateException(
								// lost guava v25.1 Strings.lenientFormat downgrading to v23
							String.format("Non-existent WSAPI4 model:%s", sapiName)));
					}
				}
				speed = Integer.parseInt(limits.get(3).split(" ")[0]);
				int minSpeed = Integer.parseInt(limits.get(3).split(" ")[1]);
				int maxSpeed = Integer.parseInt(limits.get(3).split(" ")[2]);
				pitch = Integer.parseInt(limits.get(4).split(" ")[0]);
				int minPitch = Integer.parseInt(limits.get(4).split(" ")[1]);
				int maxPitch = Integer.parseInt(limits.get(4).split(" ")[2]);
				log.debug(
					"limits for {} defaultSpeed:{} minSpeed:{} maxSpeed:{} defaultPitch:{} minPitch:{} maxPitch:{}",
					sapiName,
					speed,
					minSpeed,
					maxSpeed,
					pitch,
					minPitch,
					maxPitch);
			} catch (IOException e) {
				return Error(new RuntimeException("Failed to read SAPI4 limits", e));
			}
		}
		Path outputPath = sapi4Path.getParent().resolve("output");
		return Ok(new SpeechAPI4(sapiName, sapi4Path, outputPath, speed, pitch, gender));
	}

	// blocking
	public @NonNull Result<Audio, Exception> generate(@NonNull String text) {

		// Security:
		// A syscall to start process or a fork depending on JDK, no shell/cmd involved, no chance of injection.
		ProcessBuilder processBuilder = new ProcessBuilder(
			sapi4Path.toString(),
			voiceName,
			Integer.toString(speed),
			Integer.toString(pitch),
			text
		);
		processBuilder.directory(outputFolder);
		log.debug("SAPI4 Process command: {}", processBuilder.command());
		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			return Error(e);
		}

		String filename;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			filename = reader.readLine(); // nullable
		} catch (IOException e) {
			return Error(e);
		}

		if (filename == null) {
			return Error(new RuntimeException("SAPI4 failed to execute, likely due to missing SAPI4 installation."));
		}

		File file = outputFolder.toPath().resolve(filename).toFile();
		if (!file.exists()) {
			return Error(new RuntimeException(
					// Guava downgrade v33 to v23 -> lost guava v25.1 Strings.lenientFormat downgrading to v23
				String.format("SAPI4 failed to generate audio file: %s", filename)));
		}

		try (AudioInputStream audioFileStream = AudioSystem.getAudioInputStream(file)) {
			Audio audio = Audio.of(
				audioFileStream.readAllBytes(),
				audioFileStream.getFormat()
			);
			return Ok(audio);
		} catch (IOException | UnsupportedAudioFileException e) {
			return Error(e);
		} finally {
			boolean cleanup = file.delete();
			checkState(cleanup, "Failed to delete SAPI4 speech wav file:" + file);
		}

	}


}
