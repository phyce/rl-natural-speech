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
//    private final Path modelPath;
    private Process ttsProcess;
	private AudioPlayer audio;
    private final ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    @Getter
	private final AtomicBoolean ttsLocked = new AtomicBoolean(false);
    private boolean processing = false;
    public boolean isProcessing() { return processing; }
    private final AtomicBoolean capturing = new AtomicBoolean(false);
	private final ConcurrentHashMap<String, PlayerAudioQueue> audioQueues = new ConcurrentHashMap<>();
    private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
    public TTSEngine(Path ttsEngine, Path ttsModel) throws IOException, LineUnavailableException {
        processBuilder = new ProcessBuilder(
			ttsEngine.toString(),
                "--model", ttsModel.toString(),
                "--output-raw",
                "--json-input"
        );
		audio = new AudioPlayer();
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
        new Thread(this::captureAudioStream).start();
        new Thread(this::readControlMessages).start();
        log.info("TTSProcess Started...");
    }
	@Override
	public void run() {
		while (processing) {
			audioQueues.forEach((key, audioQueue) -> {
				if (!audioQueue.queue.isEmpty() && !audioQueue.isPlaying().get()) {
					audioQueue.setPlaying(true);
					new Thread(() -> {
						try {
							TTSItem sentence;
							while ((sentence = audioQueue.queue.poll()) != null) {
								audio.playClip(sentence);
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}).start();
				}
			});

			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
    private void captureAudioStream() {
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
    private void readControlMessages() {
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
	public void clearQueues() {
		audioQueues.values().forEach(audioQueue -> {
			if (!audioQueue.queue.isEmpty()) {
				audioQueue.queue.clear();
			}
		});
	}

	public synchronized void sendStreamTTSData(TTSItem message) {
		ttsLocked.set(true);
		try {
			message.audioClip = generateAudio(message.getMessage(), message.getVoiceID());
			String key = (message.getType() == ChatMessageType.DIALOG) ? "&dialog" : message.getName();
			if (message.audioClip.length > 0) {
				audioQueues.computeIfAbsent(key, k -> new PlayerAudioQueue()).queue.add(message);
			}
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
		ttsLocked.set(false);
	}

	public ConcurrentLinkedQueue<TTSItem> getAudioQueue(String key) {
		return audioQueues.computeIfAbsent(key, k -> new PlayerAudioQueue()).queue;
	}

    public void shutDown() {
        processing = false;
        ttsLocked.set(true);
        clearQueues();
        try {
            if (ttsInputWriter != null) ttsInputWriter.close();
            if (ttsProcess != null) ttsProcess.destroy();
            audio.shutDown();
        }
        catch (IOException exception) {
            log.error("TTSEngine failed shutting down", exception);
        }
    }
}