package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.Strings;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import java.io.*;
import javax.sound.sampled.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
public class TTSEngine implements Runnable {
    private Process ttsProcess;
    private final ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    @Getter
	private final AtomicBoolean ttsLocked = new AtomicBoolean(false);
    @Getter
	private boolean processing = false;
	private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
    public TTSEngine(Path ttsEngine, Path ttsModel) throws IOException, LineUnavailableException {
        processBuilder = new ProcessBuilder(
			ttsEngine.toString(),
                "--model", ttsModel.toString(),
                "--output-raw",
                "--json-input"
        );

        startTTSProcess();
        if (ttsProcess == null || !ttsProcess.isAlive()) {
            log.error("TTS failed to launch");
            return;
        }
        log.info("TTSEngine Started...");
    }
    public synchronized void startTTSProcess() {
        ttsLocked.set(false);
        if (ttsProcess != null) {
            ttsProcess.destroy();
            //Or maybe simply return?

            try {ttsInputWriter.close();}
            catch (IOException e) {
                log.error("tts process error", e);
                return;
            }
        }

        try {
            ttsProcess = processBuilder.start();
            ttsInputWriter = new BufferedWriter(new OutputStreamWriter(ttsProcess.getOutputStream()));
        }
        catch (IOException e) {
            log.error("tts process error", e);
            return;
        }
        processing = true;

		new Thread(this).start();
		new Thread(this::readControlMessages).start();
        log.info("TTSProcess Started...");
    }
	@Override
	//Capture audio stream
	public void run() {
		try (InputStream inputStream = ttsProcess.getInputStream()) {
			byte[] data = new byte[1024];
			int nRead;
			while (processing && (nRead = inputStream.read(data, 0, data.length)) != -1) {
				if (capturing.get()) synchronized (streamCapture) {
					streamCapture.write(data, 0, nRead);
				}
			}
			processing = false;
		} catch (IOException e) {}
	}
	public void readControlMessages() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(ttsProcess.getErrorStream()))) {
			String line;
			while (processing && (line = reader.readLine()) != null) {
				if (line.endsWith(" sec)")) capturing.set(false);
			}
		}
		catch (IOException e) {}
	}
    public byte[] generateAudio(String message, int voiceIndex) throws IOException {
        synchronized (this) {
            synchronized (streamCapture) {streamCapture.reset();}
            capturing.set(true);

            ttsInputWriter.write(Strings.generateJson(message, voiceIndex));
            ttsInputWriter.newLine();
            ttsInputWriter.flush();
        }

        while (capturing.get()) try {Thread.sleep(25);}
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        synchronized (streamCapture) {return streamCapture.toByteArray();}
    }
	public synchronized byte[] sendStreamTTSData(TTSItem message) {
		ttsLocked.set(true);
		byte[] audioClip;
		try {
			audioClip = generateAudio(message.getMessage(), message.voiceID);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		} finally {
			ttsLocked.set(false);
		}
		return audioClip;
	}
    public void shutDown() {
        processing = false;
        ttsLocked.set(true);
        try {
            if (ttsInputWriter != null) ttsInputWriter.close();
            if (ttsProcess != null) ttsProcess.destroy();

        }
        catch (IOException exception) {
            log.error("TTSEngine failed shutting down", exception);
        }
    }
}