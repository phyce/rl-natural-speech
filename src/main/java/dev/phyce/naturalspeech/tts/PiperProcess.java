package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.LineUnavailableException;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;


// Renamed from TTSEngine
@Slf4j
public class PiperProcess {
	public static final Pattern piperLogMatcher = Pattern.compile("\\[.+] \\[piper] \\[info] (.+)");
	private final ProcessBuilder processBuilder;
	@Getter
	private final AtomicBoolean piperLocked = new AtomicBoolean(false);
	private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
	private final Path modelPath;
	private Process process;
	private BufferedWriter processStdin;
	@Getter
	private Thread readStdInThread;
	private Thread readStdErrThread;

	public PiperProcess(Path piperPath, Path modelPath) throws IOException, LineUnavailableException {
		this.modelPath = modelPath;
		processBuilder = new ProcessBuilder(
				piperPath.toString(),
				"--model", modelPath.toString(),
				"--output-raw",
				"--json-input"
		);

		startTTSProcess();
		if (process == null || !process.isAlive()) {
			log.error("{} failed to launch", this);
			return;
		}
		log.info("{} started", this);
	}

	@Override
	public String toString() {
		if (process.isAlive())
			return String.format("pid:%s model:%s", process.pid(), modelPath.getFileName());
		else
			return String.format("pid:dead model:%s", modelPath.getFileName());
	}

	public synchronized void startTTSProcess() throws IOException {
		piperLocked.set(false);
		if (process != null) {
			process.destroy();
			//Or maybe simply return?

			try {
				processStdin.close();
			} catch (IOException e) {
				log.error("{}: Error", this, e);
				throw e;
			}
		}

		try {
			process = processBuilder.start();
			processStdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
		} catch (IOException e) {
			log.error("{}: Error", this, e);
			throw e;
		}

		readStdInThread = new Thread(this::readStdIn);
		readStdInThread.start();
		readStdErrThread = new Thread(this::readStdErr);
		readStdErrThread.start();

		log.info("{}: Started...", this);
	}

	public void shutDown() {
		piperLocked.set(true);
		try {
			if (processStdin != null) processStdin.close();
			if (process != null) process.destroy();

		} catch (IOException exception) {
			log.error("{} failed shutting down", this, exception);
		}
	}

	//Capture audio stream
	public void readStdIn() {
		try (InputStream inputStream = process.getInputStream()) {
			byte[] data = new byte[1024];
			int nRead;
			while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
				synchronized (streamCapture) {
					streamCapture.write(data, 0, nRead);
				}
			}
		} catch (IOException e) {
			log.error("{}: readStdIn threw", this, e);
		}
	}

	public void readStdErr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.endsWith(" sec)")) {
					synchronized(streamCapture) {
						streamCapture.notify();
					}
				}
				log.info("{}: {}", this, stripPiperLogPrefix(line));
			}
		} catch (IOException e) {
			log.error("{}: readStdErr threw exception", this, e);
		}
	}

	// refactor: inlined the speak(TTSItem) method into one generateAudio function
	public byte[] generateAudio(String text, int piperVoiceID) {
		piperLocked.set(true);
		byte[] audioClip;
		try {
			byte[] result = null;
			boolean valid = false;

			synchronized (streamCapture) {
				streamCapture.reset();
			}

			processStdin.write(TextUtil.generateJson(text, piperVoiceID));
			processStdin.newLine();
			processStdin.flush();

			synchronized(streamCapture) {
				streamCapture.wait();

				if (!valid) {
					result = streamCapture.toByteArray();
				}
			}

			audioClip = result;
		} catch (IOException | InterruptedException exception) {
			throw new RuntimeException(exception);
		} finally {
			piperLocked.set(false);
		}
		return audioClip;
	}

	public boolean isProcessAlive() {
		return process.isAlive();
	}

	private static String stripPiperLogPrefix(String piperLog) {
		// [2024-03-08 16:07:17.781] [piper] [info] Real-time factor: 0.45758559656250003 (infer=0.6640698 sec, audio=1.4512471655328798 sec)
		// ->
		// Real-time factor: 0.45758559656250003 (infer=0.6640698 sec, audio=1.4512471655328798 sec)
		Matcher match;
		if ((match = piperLogMatcher.matcher(piperLog)).matches()) {
			return match.group(1);
		} else {
			return piperLog;
		}
	}
}