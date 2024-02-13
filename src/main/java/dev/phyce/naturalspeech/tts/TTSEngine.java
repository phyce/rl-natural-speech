package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.NaturalSpeechConfig;

import java.io.*;
import javax.inject.Inject;
import javax.sound.sampled.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

public class TTSEngine implements Runnable {
    private String modelPath;
    private Process ttsProcess;
    private ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private volatile long speakCooldown;
    private AudioPlayer audio;
    private AtomicBoolean ttsLocked = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<ChatMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();
    private Map<String, String> shortenedPhrasesMap;
    private boolean processing = false;
    private AtomicBoolean capturing = new AtomicBoolean(false);
    private ByteArrayOutputStream streamCapture = new ByteArrayOutputStream();

    public TTSEngine(String model) throws IOException, LineUnavailableException {
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

        shortenedPhrasesMap = fetchShortenedPhrases();

        new Thread(this).start();
        new Thread(this::processAudioQueue).start();
        new Thread(this::timer).start();
        new Thread(this::captureAudioStream).start();
        new Thread(this::readControlMessages).start();
        System.out.println("TTSEngine Started...");
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
        if (ttsInputWriter == null) throw new IOException("ttsInputWriter is empty");
        if (messageQueue.size() > 10)messageQueue.clear();
        messageQueue.add(message);
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
                    ChatMessage message = messageQueue.poll();

                    if (message != null) {
                        TTSMessage ttsMessage = new TTSMessage(message);
                        new Thread(() -> {
                            prepareMessage(ttsMessage);
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
        sendStreamTTSData(parsedMessage, message.getVoiceIndex());
//        System.out.println("Done sending to generate audio");
    }
    private void sendStreamTTSData(String message, int voiceIndex) {
        ttsLocked.set(true);
//        System.out.println(message);
//        System.out.println(voiceIndex);
        try {
//            System.out.println("IN sendStreamTTSData");

            byte[] audioClip = generateAudio(message,voiceIndex);
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
//                speakCooldown = audio.calculateAudioLength(sentence);
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
    private Map<String, String> fetchShortenedPhrases() {
        Map<String, String> map = new HashMap<>();
        String phrases = "ags=armadyl godsword\n" +
                "ags2=ancient godsword\n" +
                "bgs=bandos godsword\n" +
                "idk=i don't know\n" +
                // Add the rest of your phrases here
                "wyd=what you doing";

        String[] lines = phrases.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) { // Ensure the line is valid
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }
    public String parseMessage(String message) {
        List<String> tokens = tokenizeMessage(message);
        StringBuilder parsedMessage = new StringBuilder();

        for (String token : tokens) {
            String key = token.replaceAll("\\p{Punct}", ""); // Remove punctuation from the token for lookup
            String replacement = shortenedPhrasesMap.getOrDefault(key, token); // Replace abbreviation if exists
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



//private void sendStreamTTSData(String message, int voiceIndex) {
//    ttsLocked.set(true);
////        audioClipReady.set(false);
////        new Thread(this::stderrListener).start();
//    try {
////            ttsInputWriter.write(generateJson(message, voiceIndex));
////            ttsInputWriter.newLine();
////            ttsInputWriter.flush();
////            Thread.sleep(10);
//        System.out.println("IN sendStreamTTSData");
//
////            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
////            byte[] data = new byte[1024];
////            int length;
////            InputStream inputStream = ttsProcess.getInputStream();
////            int nRead;
//
////            try {
////                System.out.println("about to loop through stream data");
////
////                while (!audioClipReady.get()) {
////                    // While data is available, read it
////                    while (inputStream.available() > 0 && !audioClipReady.get()) {
////                        nRead = inputStream.read(data, 0, data.length);
////                        System.out.println("writing data");
////                        buffer.write(data, 0, nRead);
////                        System.out.println("finished writing");
////                    }
////                }
//////                System.out.println("flushing");
//////                buffer.flush();
////
////                System.out.println("Audio data added to queue.");
////            } catch (IOException e) {
////                e.printStackTrace();
////            }
////            audioQueue.add(buffer.toByteArray());
////            System.out.println("AUDIO CLIP SHOULD BE READY");
////
////
////            long lastWriteTime = System.currentTimeMillis();
////            boolean dataWasWritten = false;
////
////            while (!audioClipReady) {
////                System.out.println("LOOP START ///////////////////////////////////////////");
////                if (inputStream.available() > 0) {
////                    dataWasWritten = false;
////                    System.out.println("writing sound data...");
////                    outputStream.write(buffer, 0, length);
////                    lastWriteTime = System.currentTimeMillis(); // Update last write time
////                    dataWasWritten = true;
////
////                    if (System.currentTimeMillis() - lastWriteTime >= 100) {
////                        System.out.println("breaking from clipReadyLoop");
////                        break;
////                    }
////                    System.out.println("more data tba");
////
////                    System.out.println("about to check if audioClip is ready");
////                    if (audioClipReady) break;
////                    System.out.println("out of the inputstream read loop");
////
////                    if (dataWasWritten) {
////                        System.out.println("Data was written to the output stream.");
////                    }
////                }
////                System.out.println("Loop end///////////////////////////////////////////////");
////            }
//
//
//        byte[] audioClip = generateAudio(message,voiceIndex);
//
//        if (audioClip.length > 0) {
//            audioQueue.add(audioClip);
//            System.out.println("Audio data added to queue. Length: " + audioClip.length);
//        } else {
//            System.out.println("Audio clip empty");
//        }
//        ttsLocked.set(false);
//
//    } catch (IOException | InterruptedException e) {
//        throw new RuntimeException(e);
//    }
//}