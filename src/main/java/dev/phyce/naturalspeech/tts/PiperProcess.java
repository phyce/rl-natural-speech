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
public class PiperProcess implements Runnable {
	private final ProcessBuilder processBuilder;
	@Getter
	private final AtomicBoolean piperLocked = new AtomicBoolean(false);
	private final AtomicBoolean capturing = new AtomicBoolean(false);
	private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
	private Process process;
	private BufferedWriter processStdin;
	@Getter
	private boolean processing = false;

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
		processing = true;

		new Thread(this).start();
		new Thread(this::readControlMessages).start();
		log.info("TTSProcess Started...");
	}

	public void shutDown() {
		processing = false;
		piperLocked.set(true);
		try {
			if (processStdin != null) processStdin.close();
			if (process != null) process.destroy();

		} catch (IOException exception) {
			log.error("TTSEngine failed shutting down", exception);
		}
	}

	@Override
	//Capture audio stream
	public void run() {
		try (InputStream inputStream = process.getInputStream()) {
			byte[] data = new byte[1024];
			int nRead;
			while (processing && (nRead = inputStream.read(data, 0, data.length)) != -1) {
				if (capturing.get()) synchronized (streamCapture) {
					streamCapture.write(data, 0, nRead);
				}
			}
			processing = false;
		} catch (IOException e) {
		}
	}

	public void readControlMessages() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			String line;
			while (processing && (line = reader.readLine()) != null) if (line.endsWith(" sec)")) capturing.set(false);
		} catch (IOException e) {}
	}

	// refactor: inlined the speak(TTSItem) method into one generateAudio function
	public synchronized byte[] generateAudio(String text, int piperVoiceID) {
		piperLocked.set(true);
		byte[] audioClip;
		try {
			byte[] result = null;
			boolean finished = false;
			synchronized (this) {
				synchronized (streamCapture) {
					streamCapture.reset();
				}
				capturing.set(true);

				processStdin.write(TextUtil.generateJson(text, piperVoiceID));
				processStdin.newLine();
				processStdin.flush();
			}

			while (capturing.get()) try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				finished = true;
				break;
			}
			if (!finished) {
				synchronized (streamCapture) {
					result = streamCapture.toByteArray();
				}
			}
			audioClip = result;
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		} finally {
			piperLocked.set(false);
		}
		return audioClip;
	}
}