package dev.phyce.naturalspeech.tts;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.filters.BandPass;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.effects.DelayEffect;
import net.runelite.api.ChatMessageType;

class AudioPlayer {
    private AudioFormat format;
    private SourceDataLine line;

    private boolean stop;

    public AudioPlayer() {
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                22050.0F, // Sample Rate
                16, // Sample Size in Bits
                1, // Channels
                2, // Frame Size
                22050.0F, // Frame Rate
                false); // Little Endian
    }
	public void playClip(TTSItem message) {
		AudioInputStream audioInputStream = null;
		SourceDataLine line = null;

		try {
			byte[] audioData = message.audioClip;

			audioInputStream = new AudioInputStream(
				new ByteArrayInputStream(audioData),
				this.format,
				audioData.length / this.format.getFrameSize());

			if (message.getType() == ChatMessageType.ITEM_EXAMINE ||
				message.getType() == ChatMessageType.NPC_EXAMINE ||
				message.getType() == ChatMessageType.OBJECT_EXAMINE) {
				audioInputStream = applyEffectsToStream(audioInputStream);
			}

			DataLine.Info info = new DataLine.Info(SourceDataLine.class, this.format);
			line = (SourceDataLine) AudioSystem.getLine(info);

			line.open(this.format);
			line.start();

			setVolume(line, message); // Assuming this method adjusts the line's volume

			byte[] buffer = new byte[1024];
			int bytesRead;

			while ((bytesRead = audioInputStream.read(buffer)) != -1) {
				line.write(buffer, 0, bytesRead);
			}
			line.drain();
		} catch (Exception exception) {
			System.out.println("Clip failed to play");
			exception.printStackTrace();
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

	public AudioInputStream applyEffectsToStream(AudioInputStream inputAudio) {
		try {
			AudioFormat format = inputAudio.getFormat();
			int bytesPerFrame = format.getFrameSize();
			float sampleRate = format.getSampleRate();
			int delayMilliseconds = 300; // Delay time in milliseconds
			int delayBytes = (int)((delayMilliseconds / 1000.0) * sampleRate * bytesPerFrame);

			// Create a circular buffer to hold the delay
			byte[] delayBuffer = new byte[delayBytes];
			int delayBufferPos = 0;

			// Read the entire stream into memory (not efficient for large files)
			byte[] inputBytes = inputAudio.readAllBytes();
			byte[] outputBytes = new byte[inputBytes.length];

			// Apply the echo effect
			for (int i = 0; i < inputBytes.length; i++) {
				// Mix current sample and delayed sample
				int currentSample = inputBytes[i];
				int delayedSample = delayBuffer[delayBufferPos];

				// Simple mix: average the current sample and the delayed sample
				outputBytes[i] = (byte)((currentSample + delayedSample) / 2);

				// Update the delay buffer
				delayBuffer[delayBufferPos] = (byte)currentSample;
				delayBufferPos = (delayBufferPos + 1) % delayBytes;
			}

			// Convert the output bytes back into an AudioInputStream
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(outputBytes);
			return new AudioInputStream(byteArrayInputStream, format, outputBytes.length / bytesPerFrame);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public double[] bytesToDouble(byte[] byteArray, AudioFormat format) {
		double[] doubleArray = new double[byteArray.length / 2];
		for (int i = 0; i < doubleArray.length; i++) {
			int sample = (byteArray[2*i] & 0xFF) | (byteArray[2*i + 1] << 8);
			doubleArray[i] = sample / 32768.0; // Normalize to -1.0 to 1.0 for 16-bit audio
		}
		return doubleArray;
	}

	public byte[] doubleToBytes(double[] doubleArray, AudioFormat format) {
		byte[] byteArray = new byte[doubleArray.length * 2];
		for (int i = 0; i < doubleArray.length; i++) {
			int sample = (int)(doubleArray[i] * 32768.0);
			byteArray[2*i] = (byte)(sample & 0xFF);
			byteArray[2*i + 1] = (byte)((sample >> 8) & 0xFF);
		}
		return byteArray;
	}

//	private byte[] addThoughtEffect(byte[] inputAudio) throws UnsupportedAudioFileException, LineUnavailableException {
//		// Convert byte array to an AudioInputStream
//		ByteArrayInputStream inputStream = new ByteArrayInputStream(inputAudio);
//		AudioInputStream audioStream = new AudioInputStream(inputStream, this.format, inputAudio.length / this.format.getFrameSize());
//
//		// Set up TarsosDSP audio processing
//		TarsosDSPAudioFormat tarsosFormat = new TarsosDSPAudioFormat(this.format.getSampleRate(), this.format.getSampleSizeInBits(),
//			this.format.getChannels(), this.format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED),
//			this.format.isBigEndian());
//		AudioDispatcher dispatcher = new AudioDispatcher(new UniversalAudioInputStream(audioStream, tarsosFormat), 1024, 0);
//
//		// Apply effects here
//
//		// Echo effect (using delay as a simple approximation)
////		float delayTimeInSeconds = 10f; // Adjust as needed
////		float decay = 50f; // Decay for echo effect
////		dispatcher.addAudioProcessor(new DelayEffect(delayTimeInSeconds, decay, this.format.getSampleRate()));
//
////		// EQ effect to simulate the muffled sound of thoughts
////		// This uses a high-pass and low-pass filter to mimic EQ adjustments
////		float highPassFreq = 300; // High-pass filter cutoff frequency
////		float lowPassFreq = 3000; // Low-pass filter cutoff frequency
////		dispatcher.addAudioProcessor(new HighPass(highPassFreq, this.format.getSampleRate()));
////		dispatcher.addAudioProcessor(new LowPassFS(lowPassFreq, this.format.getSampleRate()));
//
//		// Reverb effect: TarsosDSP might not have a direct reverb effect,
//		// so consider combining multiple delays or using external libraries/effects if necessary.
//
//		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//		dispatcher.addAudioProcessor(new AudioProcessor() {
//			@Override
//			public boolean process(AudioEvent audioEvent) {
//				byte[] audioBytes = audioEvent.getByteBuffer();
//				outputStream.write(audioBytes, 0, audioEvent.getBufferSize());
//				return true;
//			}
//
//			@Override
//			public void processingFinished() {
//			}
//		});
//
//		dispatcher.run();
//
//		return outputStream.toByteArray();
//	}
    public float setVolume(SourceDataLine line, TTSItem message) {
        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);

            if (message.getDistance() > 0) {
                int effectiveDistance = Math.max(1, message.getDistance());
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

