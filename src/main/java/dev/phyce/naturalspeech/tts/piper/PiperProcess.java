package dev.phyce.naturalspeech.tts.piper;

import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


// Renamed from TTSEngine
@Slf4j
public class PiperProcess {
	public static final Pattern piperLogMatcher = Pattern.compile("\\[.+] \\[piper] \\[info] (.+)");
	@Getter
	private final AtomicBoolean piperLocked;
	private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
	private final Path modelPath;
	private final Process process;
	private final BufferedWriter processStdIn;
	private final Thread processStdInThread;
	private final Thread processStdErrThread;

	private PiperProcess(Path piperPath, Path modelPath) throws IOException {
		piperLocked = new AtomicBoolean(false);
		piperLocked.set(false);
		this.modelPath = modelPath;

		ProcessBuilder processBuilder = new ProcessBuilder(
			piperPath.toString(),
			"--model", modelPath.toString(),
			"--output-raw",
			"--json-input"
		);

		process = processBuilder.start();

		processStdIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

		processStdInThread =
			new Thread(this::processStdIn, String.format("[%s] PiperProcess::processStdIn Thread", this));
		processStdInThread.start();
		processStdErrThread =
			new Thread(this::processStdErr, String.format("[%s] PiperProcess::processStdErr Thread", this));
		processStdErrThread.start();

		log.info("{}", processBuilder.command().stream().reduce((a, b) -> a + " " + b).orElse(""));
	}

	@Override
	public String toString() {
		if (process.isAlive()) {return String.format("pid:%s model:%s", process.pid(), modelPath.getFileName());}
		else {return String.format("pid:dead model:%s", modelPath.getFileName());}
	}

	public static PiperProcess start(Path piperPath, Path modelPath) throws IOException {
		return new PiperProcess(piperPath, modelPath);
	}

	public void stop() {
		piperLocked.set(true);
		processStdErrThread.interrupt();
		processStdInThread.interrupt();

		if (process != null && process.isAlive()) {
			try {
				if (processStdIn != null) processStdIn.close();
			} catch (IOException exception) {
				log.error("{} failed closing processStdIn on stop.", this, exception);
			}
			process.destroy();
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
					synchronized (streamCapture) {
						streamCapture.notify();
					}
				}
				log.trace("[pid:{}-StdErr]: {}", this.getPid(), stripPiperLogPrefix(line));
			}
		} catch (IOException e) {
			log.error("{}: readStdErr threw exception", this, e);
		}
	}

	// refactor: inlined the speak(TTSItem) method into one generateAudio function
	public byte[] generateAudio(String text, int piperVoiceID) throws IOException, InterruptedException {
		piperLocked.set(true);
		byte[] audioClip;
		try {
			byte[] result = null;
			boolean valid = false;

			synchronized (streamCapture) { streamCapture.reset(); }
			
			processStdIn.write(TextUtil.generateJson(text, piperVoiceID));
			processStdIn.newLine();
			processStdIn.flush();

			synchronized (streamCapture) {
				streamCapture.wait();

				if (!valid) result = streamCapture.toByteArray();
			}

			audioClip = result;
		} finally {
			try { Thread.sleep(10); }
			catch(InterruptedException e) { throw new RuntimeException(e); }
			streamCapture.reset();
			piperLocked.set(false);
		}
		return audioClip;
	}

	public boolean isAlive() {
		return process.isAlive();
	}

	public long getPid() {
		return process.pid();
	}

	public CompletableFuture<PiperProcess> onExit() {
		CompletableFuture<PiperProcess> piperOnExit = new CompletableFuture<PiperProcess>();
		process.onExit().thenRun(() -> piperOnExit.complete(this));
		return piperOnExit;
	}

	private static String stripPiperLogPrefix(String piperLog) {
		// [2024-03-08 16:07:17.781] [piper] [info] Real-time factor: 0.45758559656250003 (infer=0.6640698 sec, audio=1.4512471655328798 sec)
		// ->
		// Real-time factor: 0.45758559656250003 (infer=0.6640698 sec, audio=1.4512471655328798 sec)
		Matcher match;
		if ((match = piperLogMatcher.matcher(piperLog)).matches()) {
			return match.group(1);
		}
		else {
			return piperLog;
		}
	}

}