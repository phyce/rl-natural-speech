package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech;

import net.runelite.api.events.ChatMessage;

import java.io.IOException;
import javax.sound.sampled.*;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class TTSEngine {
    private Process ttsProcess;
    private BufferedWriter ttsInputWriter;
    private AudioFormat audioFormat;
    private SourceDataLine sourceLine;

    private int maxVoices = 903;

    public TTSEngine(String modelPath) throws IOException, LineUnavailableException {
        // Define the audio format of the output PCM stream
        audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                22050.0F, // Sample Rate
                16, // Sample Size in Bits
                1, // Channels
                2, // Frame Size
                22050.0F, // Frame Rate
                false); // Little Endian
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        sourceLine = (SourceDataLine) AudioSystem.getLine(info);
        sourceLine.open(audioFormat);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "C:\\piper\\piper.exe",
                "--model", modelPath,
                "--output-raw",
                "--json-input"
        );
        ttsProcess = processBuilder.start();
        ttsInputWriter = new BufferedWriter(new OutputStreamWriter(ttsProcess.getOutputStream()));

        // Start a thread to read and play the audio data
        new Thread(this::playAudioStream).start();
    }

    public void speak(ChatMessage message) throws IOException {
        if (ttsInputWriter == null) throw new IOException("ttsInputWrite is empty");


        ttsInputWriter.write(generateJson(message.getMessage(), getVoiceIndex(message.getName())));
        ttsInputWriter.newLine();
        ttsInputWriter.flush();
    }

    public static String generateJson(String message, int voiceId) {
        return String.format("{\"text\":\"%s\", \"speaker_id\":%d}", message, voiceId);
    }

    private void playAudioStream() {
        try {
            sourceLine.start();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = ttsProcess.getInputStream().read(buffer)) != -1) {
                sourceLine.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            sourceLine.drain();
            sourceLine.close();
        }
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
        if (sourceLine != null) {
            sourceLine.drain();
            sourceLine.close();
        }
    }
}




































/*





package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech;

import javax.sound.sampled.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.sound.sampled.LineUnavailableException;


import net.runelite.api.events.ChatMessage;

public class TTSEngine {
    private ProcessBuilder processBuilder;
    private Process process;
    private BufferedWriter processInputWriter;
    private AudioFormat audioFormat;

    private String modelPath;

    public void TTSEngine() {
        startProcessBuilder();
        // modelPath = path;
        // Define the audio format based on piper's output
        // Piper outputs raw audio in 16-bit mono PCM format at a sample rate that matches the voice model
    }

    public void startProcessBuilder() {
//        processBuilder = new ProcessBuilder("C:\\piper\\piper.exe", "--model", "C:\\piper\\voices\\piper-voices\\en\\en_US\\libritts\\high\\en_US-libritts-high.onnx", "--output-raw");
        processBuilder = new ProcessBuilder("C:\\piper\\piper.exe", "--model", "C:\\piper\\voices\\piper-voices\\en\\en_US\\libritts\\high\\en_US-libritts-high.onnx");
        processBuilder.redirectErrorStream(true);

        audioFormat = new AudioFormat(22050.0f, 16, 1, true, false);
    }

    public void startProcess() throws IOException {
        if (process == null || !process.isAlive()) {
            if (processBuilder == null) startProcessBuilder();
            process = processBuilder.start();
            OutputStream stdin = process.getOutputStream();
            processInputWriter = new BufferedWriter(new OutputStreamWriter(stdin));
        }
    }

    public void stopProcess() throws IOException {
        if (process != null) {
            processInputWriter.close(); // Close the input stream to signify no more input will be sent
            process.destroy(); // Optionally force the process to terminate
            process = null;
        }
    }

    public void speak(ChatMessage message) throws IOException {
        if (process == null) throw new IOException("Process is null");
        if (!process.isAlive()) throw new IOException("Process is not alive");
        if (processInputWriter == null) throw new IOException("Process input writer is null");

        processInputWriter.write(message.getMessage());
        processInputWriter.newLine();
        processInputWriter.flush(); // Ensure the text is sent to the process

        playAudioFromProcess();
    }

    private void playAudioFromProcess() {
        new Thread(() -> {
            SourceDataLine audioLine = null;

            try (AudioInputStream audioInputStream = new AudioInputStream(process.getInputStream(), audioFormat, AudioSystem.NOT_SPECIFIED)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                audioLine = (SourceDataLine) AudioSystem.getLine(info);
                audioLine.open(audioFormat);
                audioLine.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                    audioLine.write(buffer, 0, bytesRead);
                }

                audioLine.drain();
                audioLine.close();
            } catch (IOException | LineUnavailableException e) {
                e.printStackTrace();
            } finally {
                if (audioLine != null) {
                    audioLine.close();
                }
            }
        }).start();
    }
}
*/