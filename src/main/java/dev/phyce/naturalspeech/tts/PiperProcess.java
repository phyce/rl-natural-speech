package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.concurrent.CompletableFuture;
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
	private Thread processStdInThread;
	private Thread processStdErrThread;

	private PiperProcess(Path piperPath, Path modelPath) throws IOException {
		this.modelPath = modelPath;
		processBuilder = new ProcessBuilder(
				piperPath.toString(),
				"--model", modelPath.toString(),
				"--output-raw",
				"--json-input"
		);

		process = processBuilder.start();

		piperLocked.set(false);

		process = processBuilder.start();
		processStdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

		processStdInThread = new Thread(this::processStdIn);
		processStdInThread.start();
		processStdErrThread = new Thread(this::processStdErr);
		processStdErrThread.start();

		log.info("{}: Started...", this);
	}

	@Override
	public String toString() {
		if (process.isAlive())
			return String.format("pid:%s model:%s", process.pid(), modelPath.getFileName());
		else
			return String.format("pid:dead model:%s", modelPath.getFileName());
	}

	public static PiperProcess start(Path piperPath, Path modelPath) throws IOException {
		return new PiperProcess(piperPath, modelPath);
	}

	public void stop() {
		piperLocked.set(true);
		try {
			if (processStdin != null) processStdin.close();
			processStdErrThread.interrupt();
			processStdInThread.interrupt();
			if (process != null) process.destroy();
		} catch (IOException exception) {
			log.error("{} failed shutting down", this, exception);
		}
	}

	//Capture audio stream
	public void processStdIn() {
		try (InputStream inputStream = process.getInputStream()) {
			byte[] data = new byte[1024];
			int nRead;
			while (!processStdInThread.isInterrupted() && (nRead = inputStream.read(data, 0, data.length)) != -1) {
				synchronized (streamCapture) {
					streamCapture.write(data, 0, nRead);
				}
			}
		} catch (IOException e) {
			log.error("{}: readStdIn threw", this, e);
		}
	}

	public void processStdErr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while (!processStdErrThread.isInterrupted() && (line = reader.readLine()) != null) {
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

	public CompletableFuture<Process> onExit() {
		return process.onExit();
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