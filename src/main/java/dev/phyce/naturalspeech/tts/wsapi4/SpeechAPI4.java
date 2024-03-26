package dev.phyce.naturalspeech.tts.wsapi4;

import dev.phyce.naturalspeech.tts.AudioPlayer;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeechAPI4 {

	private final String modelName;
	private final Path sapi4Path;
	private final int speed;
	private final int pitch;

	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final File outputFolder;

	private static String getSAPI4Name(String modelName) {
		// SAPI4 names are case-sensitive, so we map NaturalSpeech case-insensitive version here
		switch (modelName.toLowerCase()) {
			case "sam":
				return "Sam";
			case "mary":
				return "Mary";
			case "mike":
				return "Mike";
			case "robo1":
				return "RoboSoft One";
			default:
				log.error("Invalid SAPI4 modelName {}", modelName);
				throw new IllegalArgumentException("Invalid SAPI4 modelName");
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
		String sapi4Name = getSAPI4Name(modelName);

		ProcessBuilder processBuilder = new ProcessBuilder(
			sapi4Path.resolveSibling("sapi4limits.exe").toString(),
			sapi4Name
		);

		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		int speed;
		int pitch;
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var limits = reader.lines().collect(Collectors.toList());
			speed = Integer.parseInt(limits.get(3).split(" ")[0]);
			pitch = Integer.parseInt(limits.get(4).split(" ")[0]);
			log.debug("limits for {} speed:{} pitch:{}", sapi4Name, speed, pitch);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Path outputPath = sapi4Path.getParent().resolve("output");
		return new SpeechAPI4(sapi4Name, sapi4Path, outputPath, speed, pitch);
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
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
