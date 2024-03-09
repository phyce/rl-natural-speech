package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.utils.TextUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.LineUnavailableException;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;


// Renamed from TTSEngine
@Slf4j
public class PiperProcess {
	private final ProcessBuilder processBuilder;
	@Getter
	private final AtomicBoolean piperLocked = new AtomicBoolean(false);
	private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
	private Process process;
	private BufferedWriter processStdin;
	@Getter
	private Thread readStdInThread;
	private Thread readStdErrThread;

	public PiperProcess(Path piperPath, Path modelPath) throws IOException, LineUnavailableException {
		processBuilder = new ProcessBuilder(
				piperPath.toString(),
				"--model", modelPath.toString(),
				"--output-raw",
				"--json-input"
		);

		startTTSProcess();
		if (process == null || !process.isAlive()) {
			log.error("TTS failed to launch: {}", this);
			return;
		}
		log.info("TTSEngine Started: {}", this);
	}

	@Override
	public String toString() {
		return String.format("PiperProcess with command: %s",
				processBuilder.command().stream().reduce((a, b) -> a + " " + b).orElse(""));
	}

	public synchronized void startTTSProcess() throws IOException {
		piperLocked.set(false);
		if (process != null) {
			process.destroy();
			//Or maybe simply return?

			try {
				processStdin.close();
			} catch (IOException e) {
				log.error("tts process error", e);
				throw e;
			}
		}

		try {
			process = processBuilder.start();
			processStdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
		} catch (IOException e) {
			log.error("PiperProcess error", e);
			throw e;
		}

		readStdInThread = new Thread(this::readStdIn);
		readStdInThread.start();
		readStdErrThread = new Thread(this::readStdErr);
		readStdErrThread.start();

		log.info("TTSProcess Started...");
	}

	public void shutDown() {
		piperLocked.set(true);
		try {
			if (processStdin != null) processStdin.close();
			if (process != null) process.destroy();

		} catch (IOException exception) {
			log.error("TTSEngine failed shutting down", exception);
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
			log.error("readStdIn for {} threw", this, e);
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
			}
		} catch (IOException e) {
			log.error("readStdErr for {} threw", this, e);
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
}