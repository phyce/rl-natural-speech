package dev.phyce.naturalspeech.tts.nativespeech;

import com.google.common.collect.ImmutableSet;
import dev.phyce.naturalspeech.enums.Gender;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MacSpeechProcess implements NativeSpeechProcess {

	private static final String SAY = "/usr/bin/say";
	private static final AudioFormat AUDIO_FORMAT = new AudioFormat(22050, 16, 1, true, false);
	private static final Pattern VOICE_LINE = Pattern.compile("^(.+?)\\s+\\S+\\s+#");

	private static final Set<String> MALE_VOICES = ImmutableSet.of(
		"aaron", "albert", "alex", "arthur", "daniel", "eddy", "evan", "fred", "gordon", "grandpa",
		"junior", "lee", "nathan", "oliver", "ralph", "reed", "rishi", "rocko", "thomas", "tom");
	private static final Set<String> FEMALE_VOICES = ImmutableSet.of(
		"allison", "ava", "catherine", "fiona", "flo", "grandma", "isha", "joelle", "karen", "kate",
		"kathy", "martha", "moira", "nicky", "noelle", "samantha", "sandy", "serena", "shelley", "susan",
		"tessa", "veena", "victoria", "zoe");

	@Getter
	private final List<NativeVoice> voices;
	private volatile Process speaking;
	private volatile boolean alive = true;

	private MacSpeechProcess(List<NativeVoice> voices) {
		this.voices = voices;
	}

	public static MacSpeechProcess start() throws IOException {
		Process process = new ProcessBuilder(SAY, "-v", "?").redirectErrorStream(true).start();
		String listing = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		waitFor(process);

		List<NativeVoice> voices = parseVoices(listing);
		if (voices.isEmpty()) throw new IOException("say listed no voices: " + listing);

		log.info("macOS speech ready with {} voice(s)", voices.size());
		return new MacSpeechProcess(voices);
	}

	static List<NativeVoice> parseVoices(String listing) {
		List<NativeVoice> voices = new ArrayList<>();
		Set<String> usedIDs = new HashSet<>();
		Set<String> names = new HashSet<>();

		for (String line : listing.split("\n")) {
			Matcher matcher = VOICE_LINE.matcher(line);
			if (!matcher.find()) continue;

			String name = matcher.group(1).trim();
			if (!names.add(name)) continue;

			String id = NativeSpeech.uniqueID(shortID(name), usedIDs);
			voices.add(new NativeVoice(NativeSpeech.MACOS_MODEL_NAME, id, name, parseGender(name)));
		}
		return Collections.unmodifiableList(voices);
	}

	static String shortID(String name) {
		String id = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
		return id.isEmpty() ? "voice" : id;
	}

	static Gender parseGender(String name) {
		String firstName = name.split("[\\s(]", 2)[0].toLowerCase(Locale.ROOT);
		if (MALE_VOICES.contains(firstName)) return Gender.MALE;
		if (FEMALE_VOICES.contains(firstName)) return Gender.FEMALE;
		return Gender.OTHER;
	}

	static String neutralise(String text) {
		return text.replace("[", "");
	}

	@Override
	public byte[] generateAudio(String systemName, String text) throws IOException {
		if (!alive) throw new IOException("macOS speech has been stopped");

		File wav = null;
		try {
			wav = Files.createTempFile("natural-speech", ".wav").toFile();
			return say(systemName, text, wav);
		}
		catch (IOException e) {
			if (!alive) throw e;
			log.warn("macOS voice {} could not speak: {}", systemName, text, e);
			return null;
		}
		finally {
			speaking = null;
			if (wav != null && !wav.delete()) wav.deleteOnExit();
		}
	}

	private byte[] say(String systemName, String text, File wav) throws IOException {
		Process process = new ProcessBuilder(SAY, "-v", systemName, "-o", wav.getPath(),
			"--file-format=WAVE", "--data-format=LEI16@22050")
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		speaking = process;
		if (!alive) {
			process.destroy();
			throw new IOException("macOS speech has been stopped");
		}

		try (OutputStream stdin = process.getOutputStream()) {
			stdin.write(neutralise(text).getBytes(StandardCharsets.UTF_8));
		}

		int exitCode = waitFor(process);
		if (exitCode != 0) throw new IOException("say exited with " + exitCode);

		return readPcm(wav);
	}

	@Override
	public boolean isAlive() {
		return alive;
	}

	@Override
	public CompletableFuture<?> onExit() {
		return new CompletableFuture<>();
	}

	@Override
	public void stop() {
		alive = false;
		Process process = speaking;
		if (process != null) {
			process.destroy();
		}
	}

	private static byte[] readPcm(File wav) throws IOException {
		try (AudioInputStream source = AudioSystem.getAudioInputStream(wav);
			 AudioInputStream pcm = AudioSystem.getAudioInputStream(AUDIO_FORMAT, source)) {
			return pcm.readAllBytes();
		}
		catch (UnsupportedAudioFileException | IllegalArgumentException e) {
			throw new IOException("Could not read the audio say produced", e);
		}
	}

	private static int waitFor(Process process) throws IOException {
		try {
			if (!process.waitFor(30, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IOException("say took longer than 30 seconds");
			}
			return process.exitValue();
		}
		catch (InterruptedException e) {
			process.destroyForcibly();
			throw new IOException("Interrupted waiting for say", e);
		}
	}
}
