package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;

import javax.sound.sampled.*;
import java.io.IOException;

public class AudioPlayer {
    private AudioFormat format;
    private SourceDataLine line;

    private  Process source;

    public AudioPlayer() throws LineUnavailableException {
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                22050.0F, // Sample Rate
                16, // Sample Size in Bits
                1, // Channels
                2, // Frame Size
                22050.0F, // Frame Rate
                false); // Little Endian

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
    }

    public void playAudioStream(Process source) {
        try {
            line.start();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = source.getInputStream().read(buffer)) != -1) {
                line.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            line.drain();
            line.close();
        }
    }

    public void shutDown() throws IOException {
        if (source != null) {
            line.drain();
            line.close();
        }
    }
}
