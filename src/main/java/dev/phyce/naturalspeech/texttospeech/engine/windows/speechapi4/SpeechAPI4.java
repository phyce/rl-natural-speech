package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4;

import static com.google.common.base.Preconditions.checkState;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.enums.Gender;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.Getter;
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
	private final AudioEngine audioEngine;


	private SpeechAPI4(
		AudioEngine audioEngine,
		String voiceName,
		Path sapi4Path,
		Path outputPath,
		int speed,
		int pitch,
		Gender gender
	) {
		this.audioEngine = audioEngine;
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

	@CheckForNull
	public static SpeechAPI4 start(AudioEngine audioEngine, String voiceName, Path sapi4Path) {

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
				log.error("Failed to start SAPI4 limits", e);
				return null;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				var limits = reader.lines().collect(Collectors.toList());
				// When the model was not found or installed, limit only prints 2 lines
				if (limits.size() <= 2) {
					if (limits.get(1).contains("(null)")) {
						log.trace("Windows Speech API 4 is not installed, cannot launch {}", sapiName);
					}
					else {
						log.error("Non-existent WSAPI4 model:{}", sapiName);
					}
					return null;
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
				log.error("Failed to read SAPI4 limits", e);
				return null;
			}
		}
		Path outputPath = sapi4Path.getParent().resolve("output");
		return new SpeechAPI4(audioEngine, sapiName, sapi4Path, outputPath, speed, pitch, gender);
	}


	public void speak(String text, Supplier<Float> gainSupplier, String lineName) {

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
			new Thread(processStdOut(process, gainSupplier, lineName)).start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Runnable processStdOut(Process process, Supplier<Float> gainSupplier, String lineName) {
		return () -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String filename = reader.readLine();
				if (filename == null) {
					log.error("SAPI4 failed to execute, likely due to missing SAPI4 installation.");
					return;
				}
				File audioFile = outputFolder.toPath().resolve(filename).toFile();
				try (AudioInputStream audioFileStream = AudioSystem.getAudioInputStream(audioFile)) {
					audioEngine.play(lineName, audioFileStream, gainSupplier);
				} catch (UnsupportedAudioFileException e) {
					log.error("Unsupported audio file", e);
					return; // keep the audioFile for inspection, don't delete.
				}
				checkState(audioFile.delete(), "Failed to delete SAPI4 speech wav file:" + audioFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};

	}

}
