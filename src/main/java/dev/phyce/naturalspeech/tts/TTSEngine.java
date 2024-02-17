package dev.phyce.naturalspeech.tts;


import dev.phyce.naturalspeech.Strings;
//import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.Strings;

import net.runelite.api.events.ChatMessage;

import java.io.*;
import javax.inject.Inject;
import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

public class TTSEngine implements Runnable {
    private final String modelPath;
    private Process ttsProcess;
    private final ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private final AudioPlayer audio;
    private final AtomicBoolean ttsLocked = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<TTSMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TTSMessage> dialogQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TTSAudio> audioQueue = new ConcurrentLinkedQueue<>();
    private boolean processing = false;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
    private Map<String, String> shortenedPhrases;

    public TTSEngine(String ttsBinary, String ttsModel, String phrases) throws IOException, LineUnavailableException {
        modelPath = ttsModel;

        audio = new AudioPlayer();

        processBuilder = new ProcessBuilder(
//                "C:\\piper\\piper.exe",
                ttsBinary,
                "--model", modelPath,
                "--output-raw",
                "--json-input"
        );

        startTTSProcess();
        if (ttsProcess.isAlive()) {
            System.out.println("TTS launched successfully");
        } else {
            System.out.println("TTS failed to launch");
            return;

        }
        processing = true;

        prepareShortenedPhrases(phrases);

        new Thread(this).start();
        new Thread(this::processAudioQueue).start();
        new Thread(this::captureAudioStream).start();
        new Thread(this::readControlMessages).start();
        System.out.println("TTSEngine Started...");
    }
    public synchronized void startTTSProcess() {
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
        System.out.println("TTSProcess Started...");
    }
    private void prepareShortenedPhrases(String phrases) {
        shortenedPhrases = new HashMap<>();
        String[] lines = phrases.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) shortenedPhrases.put(parts[0].trim(), parts[1].trim());
        }
    }
    private void captureAudioStream() {
        try (InputStream inputStream = ttsProcess.getInputStream()) {
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                if (capturing.get()) synchronized (streamCapture) {
                    streamCapture.write(data, 0, nRead);
                }
            }
        } catch (IOException e) {}
    }
    private void readControlMessages() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ttsProcess.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
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

        while (capturing.get()) try {
            Thread.sleep(25);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        synchronized (streamCapture) {return streamCapture.toByteArray();}
    }
    public synchronized void speak(ChatMessage message, int voiceId, int distance) throws IOException {
        if (ttsInputWriter == null) throw new IOException("ttsInputWriter is empty");
        if (messageQueue.size() > 10)messageQueue.clear();

        TTSMessage ttsMessage;
        if (voiceId == -1) ttsMessage = new TTSMessage(message, distance);
        else ttsMessage = new TTSMessage(message, distance, voiceId);

        messageQueue.add(ttsMessage);
    }
    public void clearQueues() {
        if(messageQueue.isEmpty())messageQueue.clear();
        if(audioQueue.isEmpty())audioQueue.clear();
    }
    @Override
    public void run() {
        while (processing) if (!ttsLocked.get()) {
            TTSMessage message;

            if (!dialogQueue.isEmpty()) message = dialogQueue.poll();
            else if (!messageQueue.isEmpty()) message = messageQueue.poll();
            else continue;

            if (message != null) new Thread(() -> {
                prepareMessage(message);
            }).start();
        }
    }
    private void prepareMessage(TTSMessage message) {
        String parsedMessage = Strings.parseMessage(message.getMessage(), shortenedPhrases);
        while (processing) if (!ttsLocked.get()) break;

        sendStreamTTSData(parsedMessage, message.getDistance(), message.getVoiceId());
    }
    private void sendStreamTTSData(String message, int distance, int voiceIndex) {
        ttsLocked.set(true);
        try {
            byte[] audioClip = generateAudio(message, voiceIndex);
            TTSAudio clip = new TTSAudio(audioClip, distance);

            if (audioClip.length > 0) audioQueue.add(clip);
        }
        catch (IOException exception) {
            System.out.println("Failed to send TTS data to stream");
            System.out.println(exception);
            throw new RuntimeException(exception);
        }
        ttsLocked.set(false);
    }
    private void processAudioQueue() {
        while (processing) if (!audioQueue.isEmpty()) {
            TTSAudio sentence = audioQueue.poll();
            new Thread(() -> audio.playClip(sentence)).start();
        }
    }

    public void shutDown() {
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