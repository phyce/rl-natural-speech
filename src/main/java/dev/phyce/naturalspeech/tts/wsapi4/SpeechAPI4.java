package dev.phyce.naturalspeech.tts.wsapi4;

import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.Getter;
import lombok.NonNull;
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


	@CheckForNull
	public static List<String> getModels(@NonNull Path sapi4Path) {
		Path sapi4limits = sapi4Path.resolveSibling("sapi4limits.exe");

		if (!sapi4limits.toFile().exists()) {
			log.debug("SAPI4 not installed. {} does not exist.", sapi4limits);
			return null;
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
			List<String> limits = reader.lines()
				.skip(2)
				.map((String key) -> SAPI4ModelCache.sapiToModelName.getOrDefault(key, key))
				.collect(Collectors.toList());
			log.debug("{}", limits);
			return limits;
		} catch (IOException e) {
			log.error("Failed to read SAPI4 limits", e);
			return null;
		}
	}

	private SpeechAPI4(String modelName, Path sapi4Path, Path outputPath, int speed, int pitch) {
		this.modelName = modelName;
		this.sapi4Path = sapi4Path;
		this.speed = speed;
		this.pitch = pitch;
		this.outputFolder = outputPath.toFile();

		if (!outputFolder.exists()) {
			boolean ignored = outputFolder.mkdir();
		}
	}

	public static SpeechAPI4 start(String modelName, Path sapi4Path) {
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
		return new SpeechAPI4(sapiName, sapi4Path, outputPath, speed, pitch);
	}


	public void speak(
		String text,
		VoiceID voiceID,
		float volumnDb,
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
				File audioFile = outputFolder.toPath().resolve(filename).toFile();
				try (AudioInputStream audioFileStream = AudioSystem.getAudioInputStream(audioFile)) {

					AudioFormat format = audioFileStream.getFormat();
					Clip clip = AudioSystem.getClip();
					clip.open(audioFileStream);
					clip.start();
					clip.drain();
				} catch (UnsupportedAudioFileException | LineUnavailableException e) {
					throw new RuntimeException(e);
				}
				assert audioFile.delete();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};

	}

}
