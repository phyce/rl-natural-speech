package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

class AudioPlayer {
    private AudioFormat format;
    private SourceDataLine line;
    private boolean isPlaying;

    private boolean stop;
    public void stopStream() {
        stop = true;
    }

    public AudioPlayer() {
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                22050.0F, // Sample Rate
                16, // Sample Size in Bits
                1, // Channels
                2, // Frame Size
                22050.0F, // Frame Rate
                false); // Little Endian
    }
    public void playClip(TTSAudio audio) {
        AudioInputStream audioInputStream = null;
        SourceDataLine line = null;

        try {
            audioInputStream = new AudioInputStream(
                    new ByteArrayInputStream(audio.getClip()),
                    this.format,
                    audio.getClip().length / this.format.getFrameSize());

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, this.format);
            line = (SourceDataLine) AudioSystem.getLine(info);

            line.open(this.format);
            line.start();

            setVolume(line, audio);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                line.write(buffer, 0, bytesRead);
            }
            line.drain();
        }
        catch (Exception exception) {
            System.out.println("Clip failed to play");
            System.out.println(exception);
            exception.printStackTrace();
        }
        finally {
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
    public float setVolume(SourceDataLine line, TTSAudio audio) {
        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);

            if (audio.getDistance() > 0) {
                int effectiveDistance = Math.max(1, audio.getDistance());
                float volumeReduction = -6.0f * (float)(Math.log(effectiveDistance) / Math.log(2)); // Log base 2

                float newVolume = Math.max(volumeControl.getMinimum(), volumeControl.getValue() + volumeReduction);
                volumeControl.setValue(newVolume);
            }
        }
        return -1;
    }
    public static int calculateAudioLength(byte[] audioClip) {
        final int bytesPerSample = 2; // 16-bit mono
        final int sampleRate = 22050; // Hz

        int totalSamples = audioClip.length / bytesPerSample;

        return (int) ((totalSamples / (double) sampleRate) * 1000);
    }
    public void shutDown() {}
}

