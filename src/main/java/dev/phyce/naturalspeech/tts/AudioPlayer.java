package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

class AudioPlayer {
    private AudioFormat format;
//    private SourceDataLine line;

//    private  Process source;
//    private boolean isPlaying;
//    public boolean isPlaying() {
//        return isPlaying;
//    }

//    private boolean stop;
//    public void stopStream() {
//        stop = true;
//    }

    public AudioPlayer() {
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                22050.0F, // Sample Rate
                16, // Sample Size in Bits
                1, // Channels
                2, // Frame Size
                22050.0F, // Frame Rate
                false); // Little Endian
    }

    public void playClip(byte[] audio) {
        AudioInputStream audioInputStream = null;
        SourceDataLine line = null;

        try {
            audioInputStream = new AudioInputStream(
                    new ByteArrayInputStream(audio),
                    this.format,
                    audio.length / this.format.getFrameSize());

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, this.format);
            line = (SourceDataLine) AudioSystem.getLine(info);

            line.open(this.format);
            line.start();

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                line.write(buffer, 0, bytesRead);
            }

            line.drain();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (line != null) line.close();

            if (audioInputStream != null) {
                try {
                    audioInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public static int calculateAudioLength(byte[] audioClip) {
        final int bytesPerSample = 2; // 16-bit mono
        final int sampleRate = 22050; // Hz

        // Calculate the number of samples in the audio clip
        int totalSamples = audioClip.length / bytesPerSample;

        // Calculate the length of the audio clip in milliseconds
        // (totalSamples / sampleRate) gives duration in seconds, multiply by 1000 to convert to milliseconds
        return (int) ((totalSamples / (double) sampleRate) * 1000);
    }

    public void shutDown() throws IOException {
//        if (source != null) {
//            line.drain();
//            line.close();
//        }
    }
}

//    public void playAudioStream(Process source) {
//        try {
//            stop = false;
//            line.start();
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = source.getInputStream().read(buffer)) != -1 && !stop) {
//                if(bytesRead > 0)isPlaying = true;
//                else isPlaying = false;
//
//                line.write(buffer, 0, bytesRead);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            line.drain();
//            line.close();
//        }
//    }
