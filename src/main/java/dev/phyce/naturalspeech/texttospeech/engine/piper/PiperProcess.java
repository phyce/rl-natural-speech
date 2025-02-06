package dev.phyce.naturalspeech.texttospeech.engine.piper;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PiperProcess {

	private static final Pattern PIPER_LOG_MATCHER = Pattern.compile("\\[.+] \\[piper] \\[info] (.+)");

	private final Path modelPath;
	private final Process process;

	private final BufferedWriter stdIn;
	private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

	private final StdOutThread stdOutThread;
	private final StdErrThread stdErrThread;

	private volatile boolean destroying = false;

	private final ReentrantLock generateLock = new ReentrantLock();
	private final Condition generateDone = generateLock.newCondition();

	public static Result<PiperProcess, IOException> start(Path piperPath, Path modelPath) {
		try {
			return Ok(new PiperProcess(piperPath, modelPath));
		} catch (IOException e) {
			log.error("Failed to start PiperProcess", e);
			return Error(e);
		}
	}

	private PiperProcess(Path piperPath, Path modelPath)
		throws IOException {
		this.modelPath = modelPath;

		ProcessBuilder processBuilder = new ProcessBuilder(
			piperPath.toString(),
			"--model", modelPath.toString(),
			"--output-raw",
			"--json-input"
		);

		process = processBuilder.start();

		stdIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

		stdOutThread = new StdOutThread();
		stdOutThread.start();

		stdErrThread = new StdErrThread();
		stdErrThread.start();

		log.trace("{}", processBuilder.command().stream().reduce((a, b) -> a + " " + b).orElse(""));
	}

	@Synchronized
	public byte[] generate(int piperVoiceID, String text) throws IOException {
		try {
			generateLock.lockInterruptibly();

			stdOut.reset();

			stdIn.write(TextUtil.generateJson(text, piperVoiceID));
			stdIn.newLine();
			stdIn.flush();

			generateDone.awaitUninterruptibly();

			return stdOut.toByteArray();
		} catch (InterruptedException e) {
			log.debug("Interrupted while waiting generate lock.");
			return null;
		} finally {
			generateLock.unlock();
		}
	}

	private class StdOutThread extends Thread {

		private StdOutThread() {
			super(String.format("[%s] processStdIn Thread", PiperProcess.this));
		}

		@Override
		public void run() {
			try (InputStream inputStream = process.getInputStream()) {
				byte[] data = new byte[1024];
				int nRead;
				while (!isInterrupted() && (nRead = inputStream.read(data, 0, data.length)) != -1) {
					stdOut.write(data, 0, nRead);
				}
			} catch (IOException e) {
				log.error("{}: readStdIn threw", this, e);
			}
		}
	}

	private class StdErrThread extends Thread {

		private StdErrThread() {super(String.format("[%s] processStdErr Thread", PiperProcess.this));}

		@Override
		public void run() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while (!isInterrupted() && (line = reader.readLine()) != null) {
					log.trace("[pid:{}-StdErr]:{}", getPid(), stripPiperLogPrefix(line));
					if (line.endsWith(" sec)")) {
						try {
							generateLock.lock();
							generateDone.signal();
						} finally {
							generateLock.unlock();
						}
					}
				}
			} catch (IOException e) {
				log.error("{}: readStdErr threw exception", this, e);
			}
		}
	}

	public boolean alive() {
		return process.isAlive();
	}

	public void destroy() {
		destroying = true;
		stdErrThread.interrupt();
		stdOutThread.interrupt();

		if (process != null && process.isAlive()) {
			try {
				if (stdIn != null) stdIn.close();
			} catch (IOException exception) {
				log.error("{} failed closing processStdIn on destroy.", this, exception);
			}
			process.destroy();
		}

	}

	public long getPid() {
		return process.pid();
	}

	@Override
	public String toString() {
		if (process.isAlive()) {return String.format("pid:%s model:%s", process.pid(), modelPath.getFileName());}
		else {return String.format("pid:%s(dead) model:%s", process.pid(), modelPath.getFileName());}
	}

	public ListenableFuture<PiperProcess> onCrash() {
		SettableFuture<PiperProcess> crash = SettableFuture.create();
		process.onExit().thenAccept((p) -> {
			if (!this.destroying) {
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
		process.onExit().thenAccept((p) -> {
			if (this.destroying) {
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
		if ((match = PIPER_LOG_MATCHER.matcher(piperLog)).matches()) {
			return match.group(1);
		}
		else {
			return piperLog;
		}
	}
}