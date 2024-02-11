package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import net.runelite.api.events.ChatMessage;

import java.io.*;
import javax.sound.sampled.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Random;

public class TTSEngine implements Runnable {
    public int maxVoices = 903;
    private boolean isSpeaking = false;
    private String modelPath;
    private Process ttsProcess;
    private ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private volatile long speakCooldown;
    private AudioPlayer audio;
    private boolean ttsLocked = false;
    private final ConcurrentLinkedQueue<ChatMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();

    private boolean processing = false;
    private boolean audioClipReady = false;

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

        System.out.println("TTSEngine Started...");
//        new Thread(this::processTextQueue).start();
        new Thread(this).start();
        new Thread(this::processAudioQueue).start();
//        new Thread(this::processAudioListener).start();
        new Thread(this::timer).start();
        // new Thread(() -> audio.playAudioStream(ttsProcess)).start();
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

    @Override
//    private void processTextQueue()
    public void run() {
        System.out.println("In processTextQueue()/run()");
        while (processing) {
            if (!messageQueue.isEmpty()) {
                ChatMessage message = messageQueue.poll();

                if (message != null) {
                    if (!ttsLocked && speakCooldown < 1) {
                        System.out.println("attempt to prepare message");
                        new Thread(() -> {prepareMessage(message);}).start();
                    }
                }
            }
        }
    }
    private void prepareMessage(ChatMessage message) {
        while (processing) if (!ttsLocked) break;
        System.out.println("Preparing audio for: " + message.getMessage());
        System.out.println("sending to generate audio...");
        speakCooldown = 250;
        sendStreamTTSData(message);
        System.out.println("Done sending to generate audio");
    }

    private void sendStreamTTSData(ChatMessage message) {
        ttsLocked = true;
        audioClipReady = false;
        new Thread(this::stderrListener).start();
        try {
            ttsInputWriter.write(generateJson(message.getMessage(), getVoiceIndex(message.getName())));
            ttsInputWriter.newLine();
            ttsInputWriter.flush();

            System.out.println("IN sendStreamTTSData");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            InputStream inputStream = ttsProcess.getInputStream();

            outerloop:
            while (!audioClipReady) {
                if (inputStream.available() > 0) {
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                        if (audioClipReady || inputStream.available() < 1) break outerloop;
                    }
                } else {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Handle interrupted exception
                        throw new RuntimeException("Interrupted while waiting for audio data", e);
                    }
                }
            }
            System.out.println("AUDIO CLIP IS READY !!!!!!");

            byte[] audioClip = outputStream.toByteArray();
            ttsLocked = false;

            if (audioClip.length > 0) {
                audioQueue.add(audioClip);
                System.out.println("Audio data added to queue. Length: " + audioClip.length);
            } else {
                System.out.println("Audio clip empty");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void stderrListener() {
        while (ttsLocked) {
            System.out.println("TTS IS LOCKED");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ttsProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("PRINTING OUT LINE");
                    System.out.println(line);

                    // Check if the line does not end with "Initialized piper"
                    if (line.endsWith(" sec)")) {
                        // If two relevant lines have been read, set audioClipReady to true
                        Thread.sleep(25);
                        audioClipReady = true;
                        break; // Exit the loop as the condition is met
                    }
                }
                System.out.println("LEFT WHILE");
                // Check outside the inner loop in case the outer loop's condition changes
                if (audioClipReady) break;

            } catch (Exception e) {
                System.out.println("Ending listening to stderr");
                break;
            }
        }
    }
    private void processAudioQueue() {
        System.out.println("In processAudioQueue()");
        while (processing) {
            //System.out.println("Audio Queue Length");
            //System.out.println(audioQueue.size());

            if(!audioQueue.isEmpty()) {
                byte[] sentence = audioQueue.poll();
//                speakCooldown = audio.calculateAudioLength(sentence);
                System.out.println("Queue not empty, Sending to play clip");
                new Thread(() -> audio.playClip(sentence)).start();
            }
        }
    }

    private void timer() {
        System.out.println("Timer started");

        long startTime = System.currentTimeMillis();
        while (processing) {
            try {
                if (speakCooldown > 0) {
                    long timePassed = System.currentTimeMillis() - startTime;
                    startTime = System.currentTimeMillis();
                    speakCooldown -= timePassed;

                    System.out.println("Speaking time left: " + speakCooldown + " ms");
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

    public int generateID() {
        Random rand = new Random();
        return rand.nextInt();
    }
    public int getVoiceIndex(String name) {
        int hashCode = name.hashCode();

        return Math.abs(hashCode) % maxVoices;
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
}


//    private void sendFileTTSData(ChatMessage message) {
//        TtsLocked = true;
//        int ID = generateID();
//        try {
//            ttsInputWriter.write(generateJson(message.getMessage(), getVoiceIndex(message.getName())));
//            ttsInputWriter.newLine();
//            ttsInputWriter.flush();
//
//            System.out.println("IN sendTTSData");
//            System.out.println(String.valueOf(ID) + ".pcm");
//
//            // Wait for and read the output filename from stdout
//            BufferedReader reader = new BufferedReader(new InputStreamReader(ttsProcess.getInputStream()));
//            String outputFilename;
//            while ((outputFilename = reader.readLine()) != null) {
//                System.out.println("OUTPUT FROM PIPER");
//                System.out.println(outputFilename);
//                if (outputFilename.contains("./" + String.valueOf(ID) + ".pcm")) {
//                    System.out.println(String.valueOf(ID) + ".pcm");
//
//                    // Filename found, break the loop
//                    break;
//                }
//            }
//            System.out.println("past output from piper");
//
//            // Check if the output file exists
//            File outputFile = new File(outputFilename);
//            System.out.println("ABOUT TO CHECK IF FILE EXISTS");
//            while (!outputFile.exists() || outputFile.length() == 0) {
//                try {
//                    System.out.println("Waiting for file to be filled...");
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt(); // Restore interrupted status
//                    throw new RuntimeException("Interrupted while waiting for the audio file to be filled", e);
//                }
//                // Refresh file state to get the updated size and existence status
//                outputFile = new File(outputFilename);
//            }
////            Thread.sleep(25);
//            TtsLocked = false;
//            System.out.println("File exists and is filled");
//
//            byte[] audioClip = Files.readAllBytes(outputFile.toPath());
//            System.out.println("ADDING DATA TO AUDIO QUEUE");
//
//            audioQueue.add(audioClip);
//            System.out.println("audio length:");
//            int audioLength = audio.calculateAudioLength(audioClip); // Assuming calculateAudioLength is accessible here
//            System.out.println(audioLength);
//
//            speakCooldown = audioLength;
//
//            Files.delete(outputFile.toPath());
//
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }