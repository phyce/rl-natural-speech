package dev.phyce.naturalspeech.tts.wsapi4;

import static com.google.common.base.Preconditions.checkState;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeechAPI4 {

	@Getter
	private final String modelName;
	@Getter
	private final int speed;
	@Getter
	private final int pitch;

	private final Path sapi4Path;
	private final File outputFolder;
	private final AudioEngine audioEngine;


	private SpeechAPI4(AudioEngine audioEngine, String modelName, Path sapi4Path, Path outputPath, int speed, int pitch) {
		this.audioEngine = audioEngine;
		this.modelName = modelName;
		this.sapi4Path = sapi4Path;
		this.speed = speed;
		this.pitch = pitch;
		this.outputFolder = outputPath.toFile();

		if (!outputFolder.exists()) {
			boolean ignored = outputFolder.mkdir();
		}
	}

	public static SpeechAPI4 start(AudioEngine audioEngine, String modelName, Path sapi4Path) {
		String sapiName = SAPI4ModelCache.modelToSapiName.getOrDefault(modelName, modelName);

		int speed;
		int pitch;

		if (SAPI4ModelCache.isCached(sapiName)) {
			SAPI4ModelCache cached = Objects.requireNonNull(SAPI4ModelCache.findSapiName(sapiName), sapiName);
			log.trace("Found SAPI4 Model cache for {}", cached);
			speed = cached.defaultSpeed;
			pitch = cached.defaultPitch;
		}
		else {
			ProcessBuilder processBuilder = new ProcessBuilder(
				sapi4Path.resolveSibling("sapi4limits.exe").toString(),
				sapiName
			);

			Process process;
			try {
				log.trace("Starting {}", processBuilder.command());
				process = processBuilder.start();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				var limits = reader.lines().collect(Collectors.toList());
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
				throw new RuntimeException(e);
			}
		}
		Path outputPath = sapi4Path.getParent().resolve("output");
		return new SpeechAPI4(audioEngine, sapiName, sapi4Path, outputPath, speed, pitch);
	}


	public void speak(
		String text,
		VoiceID voiceID,
		Supplier<Float> gainDB,
		String audioQueueName) {

		log.debug("Fake Speech API 4 speaking with voice {}: {}", modelName, text);
		ProcessBuilder processBuilder = new ProcessBuilder(
			sapi4Path.toString(),
			modelName,
			Integer.toString(speed),
			Integer.toString(pitch),
			text
		);
		processBuilder.directory(outputFolder);
		log.debug("SAPI4 Process command: {}", processBuilder.command());
		Process process;
		try {
			process = processBuilder.start();
			new Thread(processStdOut(process)).start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Runnable processStdOut(Process process) {
		return () -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String filename = reader.readLine();
				if (filename == null) {
					log.error("SAPI4 failed to execute, likely due to missing SAPI4 installation.");
					return;
				}
				File audioFile = outputFolder.toPath().resolve(filename).toFile();
				try (AudioInputStream audioFileStream = AudioSystem.getAudioInputStream(audioFile)) {
					audioEngine.play("&temp", audioFileStream, () -> 0f);
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
