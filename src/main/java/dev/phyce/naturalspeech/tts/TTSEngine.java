package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import net.runelite.api.events.ChatMessage;

import java.io.*;
import javax.sound.sampled.*;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TTSEngine implements Runnable {
    public int maxVoices = 903;
    private boolean isSpeaking = false;
    private String modelPath;
    private Process ttsProcess;
    private ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private volatile long speakingTimeLeft;
    private AudioPlayer audio;
    private boolean TtsLocked = false;
    private final ConcurrentLinkedQueue<ChatMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();

    private boolean processing = false;

    public TTSEngine(String model) throws IOException, LineUnavailableException {
        modelPath = model;

        audio = new AudioPlayer();

        processBuilder = new ProcessBuilder(
                "C:\\piper\\piper.exe",
                "--model", modelPath,
//                "--output-raw",
                "--json-input"
        );

        startTTSProcess();
        processing = true;
        speakingTimeLeft = 0;

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
                    if (speakingTimeLeft < 1000 && !TtsLocked) {
                        System.out.println("attempt to prepare message");
                        new Thread(() -> {prepareMessage(message);}).start();
                    }
                }
            }
        }
    }
    private void prepareMessage(ChatMessage message) {
        System.out.println("Preparing audio for: " + message.getMessage());

        while (processing) if (speakingTimeLeft < 1000) break;

        System.out.println("sending to generate audio...");
        sendTTSData(message);
        System.out.println("Done sending to generate audio");
//        InputStream inputStream = ttsProcess.getInputStream();
//        List<Byte> byteList = new ArrayList<>();
//
//        final int timeoutMs = 1000; // Maximum time to wait for new data
//
//        new Thread(() -> {
//            System.out.println("about to start listening to audio");
//            int readByte = 0;
//            int lastReadTime = (int) System.currentTimeMillis();
//
//            while (true) {
//                try {
//                    if (inputStream.available() > 0) {
//                        readByte = inputStream.read();
//                        byteList.add((byte) readByte);
//                        lastReadTime = (int) System.currentTimeMillis();
//                    } else {
//                        // Check for timeout
//                        if ((int) System.currentTimeMillis() - lastReadTime > timeoutMs) {
//                            System.out.println("Timeout reached, breaking loop");
//                            break;
//                        }
//                    }
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//            System.out.println("done");
//
//            byte[] audioData = new byte[byteList.size()];
//
//            for (int i = 0; i < byteList.size(); i++) {
//                audioData[i] = byteList.get(i);
//            }
//
//            System.out.println("Adding audio to queue");
//            System.out.println(audioData.length);
//
//            audioQueue.add(audioData);
//        }).start();
//
//        try {
//            Thread.sleep(25);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

//        new Thread(() -> {sendTTSData(message);}).start();

    }

    private void sendTTSData(ChatMessage message) {
        TtsLocked = true;
        int ID = generateID();
        try {
            ttsInputWriter.write(generateJson(message.getMessage(), getVoiceIndex(message.getName()), ID));
            ttsInputWriter.newLine();
            ttsInputWriter.flush();

            System.out.println("IN sendTTSData");
            System.out.println(String.valueOf(ID) + ".pcm");

            // Wait for and read the output filename from stdout
            BufferedReader reader = new BufferedReader(new InputStreamReader(ttsProcess.getInputStream()));
            String outputFilename;
            while ((outputFilename = reader.readLine()) != null) {
                System.out.println("OUTPUT FROM PIPER");
                System.out.println(outputFilename);
                if (outputFilename.contains("./" + String.valueOf(ID) + ".pcm")) {
                    System.out.println(String.valueOf(ID) + ".pcm");

                    // Filename found, break the loop
                    break;
                }
            }
            System.out.println("past output from piper");

            // Check if the output file exists
            File outputFile = new File(outputFilename);
            System.out.println("ABOUT TO CHECK IF FILE EXISTS");
            while (!outputFile.exists() || outputFile.length() == 0) {
                try {
                    System.out.println("Waiting for file to be filled...");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    throw new RuntimeException("Interrupted while waiting for the audio file to be filled", e);
                }
                // Refresh file state to get the updated size and existence status
                outputFile = new File(outputFilename);
            }
//            Thread.sleep(25);
            TtsLocked = false;
            System.out.println("File exists and is filled");

            byte[] audioClip = Files.readAllBytes(outputFile.toPath());
            System.out.println("ADDING DATA TO AUDIO QUEUE");

            audioQueue.add(audioClip);
            System.out.println("audio length:");
            int audioLength = audio.calculateAudioLength(audioClip); // Assuming calculateAudioLength is accessible here
            System.out.println(audioLength);

            speakingTimeLeft = audioLength;

            Files.delete(outputFile.toPath());


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        return ID;

    }

    private void processAudioQueue() {
        System.out.println("In processAudioQueue()");
        while (processing) {
//            System.out.println("Audio Queue Length");
//            System.out.println(audioQueue.size());

            if(!audioQueue.isEmpty()) {
                byte[] sentence = audioQueue.poll();
                speakingTimeLeft = audio.calculateAudioLength(sentence);
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
                if (speakingTimeLeft > 0) {
                    long timePassed = System.currentTimeMillis() - startTime;
                    startTime = System.currentTimeMillis();
                    speakingTimeLeft -= timePassed;

//                    System.out.println("Speaking time left: " + speakingTimeLeft + " ms");
                } else speakingTimeLeft = 0;

                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    public static String generateJson(String message, int voiceId, int identifier) {
        message = escapeJsonString(message);
        return String.format("{\"text\":\"%s\", \"speaker_id\":%d, \"output_file\":\"./%d.pcm\"}", message, voiceId, identifier);
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