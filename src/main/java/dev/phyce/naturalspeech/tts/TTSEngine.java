package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import net.runelite.api.events.ChatMessage;

import java.io.IOException;
import javax.sound.sampled.*;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TTSEngine implements Runnable {
    public int maxVoices = 903;
    private String modelPath;
    private AudioPlayer player;
    private Process ttsProcess;
    private ProcessBuilder processBuilder;
    private BufferedWriter ttsInputWriter;
    private boolean isSpeaking = false;
    private final ConcurrentLinkedQueue<ChatMessage> messageQueue = new ConcurrentLinkedQueue<>();

    public TTSEngine(String model) throws IOException, LineUnavailableException {
        modelPath = model;

        player = new AudioPlayer();

        processBuilder = new ProcessBuilder(
                "C:\\piper\\piper.exe",
                "--model", modelPath,
                "--output-raw",
                "--json-input"
        );

        startTTSProcess();

        new Thread(this).start();
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

//        new Thread(this::playAudioStream).start();
        new Thread(() -> player.playAudioStream(ttsProcess)).start();
    }



    public synchronized void speak(ChatMessage message) throws IOException {
        if (ttsInputWriter == null) throw new IOException("ttsInputWriter is empty");

        if (isSpeaking) {
            messageQueue.add(message);
        } else {
            isSpeaking = true;
            ttsInputWriter.write(generateJson(message.getMessage(), getVoiceIndex(message.getName())));
            ttsInputWriter.newLine();
            ttsInputWriter.flush();
        }
    }

    @Override
    public void run() {
        while (true) {
            if (isSpeaking && messageQueue.isEmpty()) {
                isSpeaking = false;
            }
            if (!isSpeaking && !messageQueue.isEmpty()) {
                isSpeaking = true;
                try {
                    processQueue();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processQueue() throws IOException {
        while (!messageQueue.isEmpty()) {
            ChatMessage message = messageQueue.poll();
            if (message != null) {
                ttsInputWriter.write(generateJson(message.getMessage(), getVoiceIndex(message.getName())));
                ttsInputWriter.newLine();
                ttsInputWriter.flush();
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
        player.shutDown();
    }
}