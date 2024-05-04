package dev.phyce.naturalspeech.texttospeech.engine.piper;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class PiperProcess {
	public static final Pattern piperLogMatcher = Pattern.compile("\\[.+] \\[piper] \\[info] (.+)");
	private final AtomicBoolean piperLocked;
	private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
	private final Path modelPath;
	private final Process process;
	private final BufferedWriter processStdIn;
	private final Thread processStdInThread;
	private final Thread processStdErrThread;
	private final PluginExecutorService pluginExecutorService;

	private volatile boolean destroying = false;

	private PiperProcess(PluginExecutorService pluginExecutorService, Path piperPath, Path modelPath)
		throws IOException {
		this.pluginExecutorService = pluginExecutorService;
		piperLocked = new AtomicBoolean(false);
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

	public static PiperProcess start(PluginExecutorService pluginExecutorService, Path piperPath, Path modelPath)
		throws IOException {
		return new PiperProcess(pluginExecutorService, piperPath, modelPath);
	}

	public void destroy() {
		piperLocked.set(true);
		destroying = true;
		processStdErrThread.interrupt();
		processStdInThread.interrupt();

		if (process != null && process.isAlive()) {
			try {
				if (processStdIn != null) processStdIn.close();
			} catch (IOException exception) {
				log.error("{} failed closing processStdIn on destroy.", this, exception);
			}
			process.destroy();
		}

	}

	//Capture audio stream
	private void processStdIn() {
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

	private void processStdErr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while (!processStdErrThread.isInterrupted() && (line = reader.readLine()) != null) {
				if (line.endsWith(" sec)")) {
					synchronized (streamCapture) {
						streamCapture.notify();
					}
				}
				log.trace("[pid:{}-StdErr]:{}", this.getPid(), stripPiperLogPrefix(line));
			}
		} catch (IOException e) {
			log.error("{}: readStdErr threw exception", this, e);
		}
	}

	// refactor: inlined the speak(TTSItem) method into one generateAudio function
	public ListenableFuture<byte[]> generateAudio(int piperVoiceID, String text) {
		if (piperLocked.get()) {
			log.error("attempting to generateAudio with locked PiperProcess({}):{}", this, text);
			return Futures.immediateCancelledFuture();
		}

		piperLocked.set(true);

		return Futures.submit(() -> {
			byte[] result = null;
			try {
				result = _blockedGenerateAudio(piperVoiceID, text);
			} catch (InterruptedException | IOException e) {
				log.error("PiperProcess {} failed to generate:{}", this, text);
			} finally {
				piperLocked.set(false);
			}
			return result;
		}, pluginExecutorService);
	}

	private byte[] _blockedGenerateAudio(int piperVoiceID, String text) throws IOException, InterruptedException {
		try {
			byte[] result = null;

			synchronized (streamCapture) {streamCapture.reset();}

			processStdIn.write(TextUtil.generateJson(text, piperVoiceID));
			processStdIn.newLine();
			processStdIn.flush();

			synchronized (streamCapture) {
				streamCapture.wait();

				result = streamCapture.toByteArray();
			}

			return result;
		} finally {
			streamCapture.reset();
		}
	}

	public boolean isAlive() {
		return process.isAlive();
	}

	public boolean isLocked() {
		return piperLocked.get();
	}

	public long getPid() {
		return process.pid();
	}

	public ListenableFuture<PiperProcess> onCrash() {
		SettableFuture<PiperProcess> crash = SettableFuture.create();
		PiperProcess ref = this;
		process.onExit().thenAccept((p) -> {
			// if we're exiting, but not in the process of destroying PiperProcess
			// then this is a crash
			if (!ref.destroying) {
				crash.set(this);
			}
			else {
				crash.cancel(false);
			}
		});
		return crash;
	}

	public ListenableFuture<PiperProcess> onExit() {
		SettableFuture<PiperProcess> exit = SettableFuture.create();
		PiperProcess ref = this;
		process.onExit().thenAccept((p) -> {
			// if we're exiting, but in the process of destroying PiperProcess
			// then this is a normal exit
			if (ref.destroying) {
				exit.set(this);
			}
			else {
				exit.cancel(false);
			}
		});
		return exit;
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