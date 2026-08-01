package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.NaturalSpeechPlugin;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The Windows built-in voices, driven through a long lived PowerShell process.
 * <p>
 * Windows exposes speech through the .NET {@code System.Speech} assembly, which Java cannot call
 * directly. PowerShell ships with every supported version of Windows and can, so it acts as the
 * bridge - the same arrangement as piper, a process that takes text on stdin and returns audio.
 * <p>
 * The script ships as a resource in the plugin jar and is handed to PowerShell inline with
 * {@code -Command}, so there is no temp file to clean up, no execution policy to work around, and
 * the exact script being run is visible in the process list. No reflection, no native libraries.
 *
 * @see <a href="https://learn.microsoft.com/dotnet/api/system.speech.synthesis">System.Speech.Synthesis</a>
 */
@Slf4j
public class WindowsSpeechProcess implements NativeSpeechProcess {

	private static final String SCRIPT_RESOURCE = "tts/nativespeech/WindowsSpeech.ps1";

	private static final String CONTROL_VOICE = "VOICE";
	private static final String CONTROL_READY = "READY";
	private static final String CONTROL_AUDIO = "AUDIO";
	private static final String CONTROL_ERROR = "ERROR";

	private final Process process;
	private final BufferedWriter processStdIn;
	private final BufferedReader processStdOut;
	private final Thread processStdErrThread;

	@Getter
	private final List<NativeVoice> voices;

	private WindowsSpeechProcess(Process process) throws IOException {
		this.process = process;

		processStdIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
		processStdOut = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

		processStdErrThread = new Thread(this::processStdErr, "WindowsSpeechProcess::processStdErr Thread");
		processStdErrThread.setDaemon(true);
		processStdErrThread.start();

		voices = Collections.unmodifiableList(readVoicesUntilReady());
	}

	public static WindowsSpeechProcess start() throws IOException {
		String script = readScript();
		if (script == null) {
			throw new IOException("Could not read " + SCRIPT_RESOURCE + " from the plugin jar");
		}

		// a fixed constant, no user input is ever put on this command line
		ProcessBuilder processBuilder = new ProcessBuilder(
			"powershell.exe",
			"-NoProfile",
			"-NonInteractive",
			"-Command", script
		);

		log.debug("Starting Windows speech process");
		return new WindowsSpeechProcess(processBuilder.start());
	}

	/**
	 * Windows reports its voices once at startup, before accepting any work.
	 */
	private List<NativeVoice> readVoicesUntilReady() throws IOException {
		List<NativeVoice> found = new ArrayList<>();
		Set<String> usedIDs = new HashSet<>();

		String line;
		while ((line = processStdOut.readLine()) != null) {
			if (line.equals(CONTROL_READY)) {
				log.info("Windows speech ready with {} voice(s)", found.size());
				return found;
			}

			String[] fields = line.split("\t");
			if (fields.length == 4 && fields[0].equals(CONTROL_VOICE)) {
				String systemName = fields[1];
				found.add(new NativeVoice(NativeSpeech.WINDOWS_MODEL_NAME, uniqueID(systemName, usedIDs),
					systemName, parseGender(fields[2]), fields[3]));
			}
			else {
				log.debug("Ignoring unexpected line while starting: {}", line);
			}
		}

		throw new IOException("Windows speech process exited before it was ready");
	}

	/**
	 * "Microsoft David Desktop" becomes david. Windows names its voices far too long to type into a
	 * voice setting, and the part that actually tells them apart is the given name in the middle.
	 */
	static String shortID(String systemName) {
		String name = systemName;

		// "Microsoft Zira Desktop - English (United States)"
		int dash = name.indexOf(" - ");
		if (dash > 0) name = name.substring(0, dash);

		name = name.replaceFirst("(?i)^Microsoft\\s+", "");
		name = name.replaceFirst("(?i)\\s+Desktop$", "");
		name = name.trim().toLowerCase(Locale.ROOT);

		// a voice setting splits on the first colon, and spaces are awkward to type
		name = name.replace(':', ' ').trim().replaceAll("\\s+", "-");

		return name.isEmpty() ? systemName.toLowerCase(Locale.ROOT) : name;
	}

	/** Two voices can shorten to the same thing, and a voice setting has to name exactly one. */
	private static String uniqueID(String systemName, Set<String> used) {
		String base = shortID(systemName);

		String id = base;
		for (int suffix = 2; !used.add(id); suffix++) {
			id = base + suffix;
		}
		return id;
	}

	/** One PowerShell process handles one utterance at a time, hence synchronized. */
	@Override
	public synchronized byte[] generateAudio(String voiceID, String text) throws IOException {
		if (!process.isAlive()) throw new IOException("Windows speech process is not running");

		// SelectVoice only knows the name Windows itself uses
		String systemName = voices.stream()
			.filter(voice -> voice.getId().equals(voiceID))
			.map(NativeVoice::getSystemName)
			.findFirst()
			.orElse(voiceID);

		processStdIn.write(encode(systemName));
		processStdIn.write('\t');
		processStdIn.write(encode(text));
		processStdIn.newLine();
		processStdIn.flush();

		String line = processStdOut.readLine();
		if (line == null) throw new IOException("Windows speech process closed while waiting for audio");

		int separator = line.indexOf('\t');
		String control = separator < 0 ? line : line.substring(0, separator);
		String payload = separator < 0 ? "" : line.substring(separator + 1);

		switch (control) {
			case CONTROL_AUDIO:
				return Base64.getDecoder().decode(payload);
			case CONTROL_ERROR:
				// a bad voice name should not take the whole engine down, the process survives it
				log.warn("Windows speech could not speak with voice '{}': {}", voiceID, payload);
				return null;
			default:
				log.warn("Unexpected response from Windows speech: {}", line);
				return null;
		}
	}

	@Override
	public boolean isAlive() {
		return process.isAlive();
	}

	@Override
	public void stop() {
		processStdErrThread.interrupt();
		try {
			// closing stdin ends the script's read loop, letting it exit on its own
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

	private static String readScript() {
		try (InputStream is = NaturalSpeechPlugin.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (is == null) return null;
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			log.error("Failed reading {}", SCRIPT_RESOURCE, e);
			return null;
		}
	}

	private void processStdErr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
				// PowerShell writes a CLIXML progress preamble here on first run, not worth surfacing
				if (line.startsWith("#< CLIXML") || line.startsWith("<Objs")) continue;
				log.debug("[WindowsSpeech-StdErr]: {}", line);
			}
		}
		catch (IOException e) {
			log.debug("Windows speech stderr reader stopped", e);
		}
	}

	@Override
	public String toString() {
		return process.isAlive()
			? String.format("pid:%s windows speech", process.pid())
			: "pid:dead windows speech";
	}
}
