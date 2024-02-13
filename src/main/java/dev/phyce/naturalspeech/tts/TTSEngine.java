package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.NaturalSpeechConfig;

import java.io.*;
import javax.inject.Inject;
import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

public class TTSEngine implements Runnable {
    private final String modelPath;
    private Process ttsProcess;
    private final ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private volatile long speakCooldown;
    private final AudioPlayer audio;
    private final AtomicBoolean ttsLocked = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<TTSMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();
    private boolean processing = false;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();
    private Map<String, String> shortenedPhrases;

    public TTSEngine(String model, String phrases) throws IOException, LineUnavailableException {
        modelPath = model;

        audio = new AudioPlayer();

        processBuilder = new ProcessBuilder(
                "C:\\piper\\piper.exe",
                "--model", modelPath,
                "--output-raw",
                "--json-input"
        );

        startTTSProcess();
        processing = true;
        speakCooldown = 0;

        prepareShortenedPhrases(phrases);

        new Thread(this).start();
        new Thread(this::processAudioQueue).start();
        new Thread(this::timer).start();
        new Thread(this::captureAudioStream).start();
        new Thread(this::readControlMessages).start();
        System.out.println("TTSEngine Started...");
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
                if (capturing.get()) {
                    synchronized (streamCapture) {
                        streamCapture.write(data, 0, nRead);
                    }
                }
            }
        } catch (IOException e) {
            // Handle exceptions
        }
    }
    private void readControlMessages() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ttsProcess.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith(" sec)")) {
                    capturing.set(false);
                }
            }
        } catch (IOException e) {
            // Handle exceptions
        }
    }
    public byte[] generateAudio(String message, int voiceIndex) throws IOException {
        synchronized (this) {
            synchronized (streamCapture) {streamCapture.reset();}
            capturing.set(true);

            ttsInputWriter.write(generateJson(message, voiceIndex));
            ttsInputWriter.newLine();
            ttsInputWriter.flush();
        }

        while (capturing.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        synchronized (streamCapture) {
            return streamCapture.toByteArray();
        }
    }
    public synchronized void startTTSProcess() {
        if (ttsProcess != null) {
            ttsProcess.destroy();
            //Or maybe simply return?
            try {
                ttsInputWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            ttsProcess = processBuilder.start();
            ttsInputWriter = new BufferedWriter(new OutputStreamWriter(ttsProcess.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("TTSProcess Started...");
    }
    public synchronized void speak(ChatMessage message) throws IOException {
        speak(message, -1);
    }

    public synchronized void speak(ChatMessage message, int voiceId) throws IOException {
        System.out.println("speak function with voiceID=");
        System.out.println(voiceId);
        if (ttsInputWriter == null) throw new IOException("ttsInputWriter is empty");
        if (messageQueue.size() > 10)messageQueue.clear();

        System.out.println(message.toString());
        System.out.println("speak function");
        System.out.println(message.getName());

        TTSMessage ttsMessage;
        if (voiceId == -1)ttsMessage = new TTSMessage(message);
        else ttsMessage = new TTSMessage(message, voiceId);

        messageQueue.add(ttsMessage);
    }
    public void clearQueues() {
        if(messageQueue.isEmpty())messageQueue.clear();
        if(audioQueue.isEmpty())audioQueue.clear();
    }
    @Override
    public void run() {
        while (processing) {
            if (!ttsLocked.get() && speakCooldown < 1) {
                if (!messageQueue.isEmpty()) {
                    TTSMessage message = messageQueue.poll();

                    if (message != null) {
                        new Thread(() -> {
                            prepareMessage(message);
                        }).start();
                    }
                }
            }
        }
    }
    private void prepareMessage(TTSMessage message) {
        String parsedMessage = parseMessage(message.getMessage());
        while (processing) if (!ttsLocked.get()) break;
//        System.out.println("Preparing audio for: " + message.getMessage());
        speakCooldown = 250;
        sendStreamTTSData(parsedMessage, message.getVoiceId());
//        System.out.println("Done sending to generate audio");
    }
    private void sendStreamTTSData(String message, int voiceIndex) {
        ttsLocked.set(true);
        System.out.println(message);
        System.out.println(voiceIndex);
        try {
//            System.out.println("IN sendStreamTTSData");

            byte[] audioClip = generateAudio(message, voiceIndex);
//            System.out.println("Audio Generated");

            if (audioClip.length > 0) {
                audioQueue.add(audioClip);
//                System.out.println("Audio data added to queue. Length: " + audioClip.length);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ttsLocked.set(false);
    }
    private void processAudioQueue() {
        while (processing) {
            if(!audioQueue.isEmpty()) {
                byte[] sentence = audioQueue.poll();
                //speakCooldown = audio.calculateAudioLength(sentence);
                new Thread(() -> audio.playClip(sentence)).start();
            }
        }
    }
    private void timer() {
        long startTime = System.currentTimeMillis();
        while (processing) {
            try {
                if (speakCooldown > 0) {
                    long timePassed = System.currentTimeMillis() - startTime;
                    startTime = System.currentTimeMillis();
                    speakCooldown -= timePassed;

                    //System.out.println("Speaking time left: " + speakCooldown + " ms");
                } else speakCooldown = 0;

                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    public static String generateJson(String message, int voiceId) {
        message = escapeJsonString(message);
        return String.format("{\"text\":\"%s\", \"speaker_id\":%d}", message, voiceId);
    }
    public static String escapeJsonString(String message) {
        return message.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    public void shutDown() throws IOException {
        if (ttsInputWriter != null) {
            ttsInputWriter.close();
        }
        if (ttsProcess != null) {
            ttsProcess.destroy();
        }
        audio.shutDown();
    }
    public String parseMessage(String message) {
        List<String> tokens = tokenizeMessage(message);
        StringBuilder parsedMessage = new StringBuilder();

        for (String token : tokens) {
            String key = token.replaceAll("\\p{Punct}", "").toLowerCase(); // Remove punctuation from the token for lookup
            String replacement = shortenedPhrases.getOrDefault(key, token); // Replace abbreviation if exists
            // Append the original token if no replacement was done, preserving the original punctuation
            parsedMessage.append(replacement.equals(token) ? token : replacement).append(" ");
        }

        return parsedMessage.toString().trim(); // Trim the trailing space
    }
    private List<String> tokenizeMessage(String message) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("[\\w']+|\\p{Punct}").matcher(message);

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }
}