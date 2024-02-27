package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.Strings;

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
    private final Path modelPath;
    private Process ttsProcess;
    private final ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private final AudioPlayer audio;
    private final AtomicBoolean ttsLocked = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<TTSItem> messageQueue = new ConcurrentLinkedQueue<>();
	private final ConcurrentHashMap<String, PlayerAudioQueue> audioQueues = new ConcurrentHashMap<>();
    private boolean processing = false;
    public boolean isProcessing() { return processing; }
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
    private Map<String, String> shortenedPhrases;

    public TTSEngine(Path ttsBinary, Path ttsModel, String phrases) throws IOException, LineUnavailableException {
        modelPath = ttsModel;
        audio = new AudioPlayer();
        processBuilder = new ProcessBuilder(
                ttsBinary.toString(),
                "--model", modelPath.toString(),
                "--output-raw",
                "--json-input"
        );

        prepareShortenedPhrases(phrases);
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
        new Thread(this::processAudioQueue).start();
        new Thread(this::captureAudioStream).start();
        new Thread(this::readControlMessages).start();
        log.info("TTSProcess Started...");
    }
    @Override
    public void run() {
        while (processing) if (!ttsLocked.get()) {
            TTSItem message;

            if (!messageQueue.isEmpty()) message = messageQueue.poll();
            else continue;

            if (message != null) new Thread(() -> {prepareMessage(message);}).start();

        }
    }
	private void processAudioQueue() {
		while (processing) {
			audioQueues.forEach((key, audioQueue) -> {
				if (!audioQueue.queue.isEmpty() && !audioQueue.isPlaying().get()) {
					audioQueue.setPlaying(true);
					new Thread(() -> {
						try {
							TTSItem sentence;
							while ((sentence = audioQueue.queue.poll()) != null) {
								// Simulate playing the clip
								audio.playClip(sentence);
							}
						} finally {
							audioQueue.setPlaying(false);
						}
					}).start();
				}
			});

			// Just a short pause to prevent a tight loop hammering the CPU
			try {
				Thread.sleep(100); // Slightly longer sleep to give time for threads to process
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return; // Exit if interrupted
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

    public synchronized void speak(ChatMessage message, int voiceID, int distance) throws IOException {
        if (ttsInputWriter == null) throw new IOException("ttsInputWriter is empty");
        if (messageQueue.size() > 10) messageQueue.clear();

		TTSItem ttsItem;
		if (voiceID == -1) ttsItem = new TTSItem(message, distance);
		else ttsItem = new TTSItem(message, distance, voiceID);

        messageQueue.add(ttsItem);
    }

    private void prepareShortenedPhrases(String phrases) {
        shortenedPhrases = new HashMap<>();
        String[] lines = phrases.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) shortenedPhrases.put(parts[0].trim(), parts[1].trim());
        }
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
		// Assuming messageQueue still needs to be cleared
		if (messageQueue != null && !messageQueue.isEmpty()) {
			messageQueue.clear();
		}
		audioQueues.values().forEach(audioQueue -> {
			if (!audioQueue.queue.isEmpty()) {
				audioQueue.queue.clear();
			}
		});
	}
    private void prepareMessage(TTSItem message) {
        while (processing) if (!ttsLocked.get()) break;
		if(message.getType() != ChatMessageType.DIALOG) {
			message.setMessage(Strings.parseMessage(message.getMessage(), shortenedPhrases));
		}

		TTSItem[] sentences = message.explode();
		for (TTSItem sentence : sentences) sendStreamTTSData(sentence);
    }
	private void sendStreamTTSData(TTSItem message) {
		ttsLocked.set(true);
		try {
			message.audioClip = generateAudio(message.getMessage(), message.getVoiceID());

			String key = (message.getType() == ChatMessageType.DIALOG) ? "&dialog" : message.getName();
			if (message.audioClip.length > 0) {
				audioQueues.computeIfAbsent(key, k -> new PlayerAudioQueue()).queue.add(message);
			}
		} catch (IOException exception) {
			System.out.println("Failed to send TTS data to stream");
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