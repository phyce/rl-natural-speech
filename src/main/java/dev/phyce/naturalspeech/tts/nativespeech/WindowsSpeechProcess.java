package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.enums.Gender;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WindowsSpeechProcess implements NativeSpeechProcess {

	private static final String SCRIPT_RESOURCE = "WindowsSpeech.ps1";

	private final Process process;
	private final BufferedWriter processStdIn;
	private final BufferedReader processStdOut;

	@Getter
	private final List<NativeVoice> voices;

	private WindowsSpeechProcess(Process process) throws IOException {
		this.process = process;

		processStdIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
		processStdOut = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

		Thread stdErrThread = new Thread(this::logStdErr, "WindowsSpeechProcess stderr");
		stdErrThread.setDaemon(true);
		stdErrThread.start();

		voices = Collections.unmodifiableList(readVoicesUntilReady());
	}

	public static WindowsSpeechProcess start() throws IOException {
		String script;
		try (InputStream stream = WindowsSpeechProcess.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (stream == null) throw new IOException("Missing " + SCRIPT_RESOURCE);
			script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		return new WindowsSpeechProcess(new ProcessBuilder(
			"powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).start());
	}

	private List<NativeVoice> readVoicesUntilReady() throws IOException {
		List<NativeVoice> found = new ArrayList<>();
		Set<String> usedIDs = new HashSet<>();

		String line;
		while ((line = processStdOut.readLine()) != null) {
			if (line.equals("READY")) {
				log.info("Windows speech ready with {} voice(s)", found.size());
				return found;
			}

			String[] fields = line.split("\t");
			if (fields.length == 3 && fields[0].equals("VOICE")) {
				String id = NativeSpeech.uniqueID(shortID(fields[1]), usedIDs);
				found.add(new NativeVoice(NativeSpeech.WINDOWS_MODEL_NAME, id, fields[1], parseGender(fields[2])));
			}
			else {
				log.debug("Ignoring unexpected line while starting: {}", line);
			}
		}

		throw new IOException("Windows speech process exited before it was ready");
	}

	static String shortID(String systemName) {
		String name = systemName;

		int dash = name.indexOf(" - ");
		if (dash > 0) name = name.substring(0, dash);

		name = name.replaceFirst("(?i)^Microsoft\\s+", "");
		name = name.replaceFirst("(?i)\\s+Desktop$", "");
		name = name.trim().toLowerCase(Locale.ROOT);
		name = name.replace(':', ' ').trim().replaceAll("\\s+", "-");

		return name.isEmpty() ? systemName.toLowerCase(Locale.ROOT) : name;
	}

	@Override
	public synchronized byte[] generateAudio(String systemName, String text) throws IOException {
		if (!process.isAlive()) throw new IOException("Windows speech process is not running");

		processStdIn.write(encode(systemName) + "\t" + encode(text));
		processStdIn.newLine();
		processStdIn.flush();

		String line = processStdOut.readLine();
		if (line == null) throw new IOException("Windows speech process closed while waiting for audio");

		if (line.startsWith("AUDIO\t")) {
			return Base64.getDecoder().decode(line.substring("AUDIO\t".length()));
		}

		log.warn("Windows speech could not speak with voice '{}': {}", systemName, line);
		return null;
	}

	@Override
	public boolean isAlive() {
		return process.isAlive();
	}

	@Override
	public CompletableFuture<?> onExit() {
		return process.onExit();
	}

	@Override
	public void stop() {
		try {
			processStdIn.close();
		}
		catch (IOException e) {
			log.debug("Failed closing Windows speech stdin", e);
		}
		process.destroy();
	}

	private static String encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static Gender parseGender(String gender) {
		if ("Male".equalsIgnoreCase(gender)) return Gender.MALE;
		if ("Female".equalsIgnoreCase(gender)) return Gender.FEMALE;
		return Gender.OTHER;
	}

	private void logStdErr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("#< CLIXML") || line.startsWith("<Objs")) continue;
				log.debug("[WindowsSpeech-StdErr]: {}", line);
			}
		}
		catch (IOException e) {
			log.debug("Windows speech stderr reader stopped", e);
		}
	}
}
