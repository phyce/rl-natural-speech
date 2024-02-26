package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.Strings;

import net.runelite.api.events.ChatMessage;
import java.io.*;
import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


public class TTSEngine implements Runnable {
    private final String modelPath;
    private Process ttsProcess;
    private final ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private final AudioPlayer audio;
    private final AtomicBoolean ttsLocked = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<TTSItem> messageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TTSItem> audioQueue = new ConcurrentLinkedQueue<>();
    private boolean processing = false;
    public boolean isProcessing() { return processing; }
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
    private Map<String, String> shortenedPhrases;

    public TTSEngine(String ttsBinary, String ttsModel, String phrases) throws IOException, LineUnavailableException {
        modelPath = ttsModel;
        audio = new AudioPlayer();
        processBuilder = new ProcessBuilder(
                ttsBinary,
                "--model", modelPath,
                "--output-raw",
                "--json-input"
        );

        prepareShortenedPhrases(phrases);
        startTTSProcess();
        if (!ttsProcess.isAlive()) {
            System.out.println("TTS failed to launch");
            return;
        }
        System.out.println("TTSEngine Started...");
    }
    public synchronized void startTTSProcess() {
        ttsLocked.set(false);
        if (ttsProcess != null) {
            ttsProcess.destroy();
            //Or maybe simply return?

            try {ttsInputWriter.close();}
            catch (IOException e) {e.printStackTrace();}
        }

        try {
            ttsProcess = processBuilder.start();
            ttsInputWriter = new BufferedWriter(new OutputStreamWriter(ttsProcess.getOutputStream()));
        }
        catch (IOException e) {e.printStackTrace();}
        processing = true;

        new Thread(this).start();
        new Thread(this::processAudioQueue).start();
        new Thread(this::captureAudioStream).start();
        new Thread(this::readControlMessages).start();
        System.out.println("TTSProcess Started...");
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
        while (processing) if (!audioQueue.isEmpty()) {
            TTSItem sentence = audioQueue.poll();
            new Thread(() -> audio.playClip(sentence)).start();
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
        if (messageQueue.size() > 10)messageQueue.clear();

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
        if(messageQueue.isEmpty())messageQueue.clear();
        if(audioQueue.isEmpty())audioQueue.clear();
    }
    private void prepareMessage(TTSItem message) {
        while (processing) if (!ttsLocked.get()) break;
		message.message = Strings.parseMessage(message.getMessage(), shortenedPhrases);

        sendStreamTTSData(message);
    }
    private void sendStreamTTSData(TTSItem message) {
        ttsLocked.set(true);
        try {
			message.audioClip = generateAudio(message.message, message.getVoiceID());

            if (message.audioClip.length > 0) audioQueue.add(message);
        }
        catch (IOException exception) {
            System.out.println("Failed to send TTS data to stream");
            System.out.println(exception);
            throw new RuntimeException(exception);
        }
        ttsLocked.set(false);
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
            System.out.println("TTSEngine failed shutting down");
            System.out.println(exception);
        }
    }
}