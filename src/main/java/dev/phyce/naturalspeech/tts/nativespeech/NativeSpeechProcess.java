package dev.phyce.naturalspeech.tts.nativespeech;

import java.io.IOException;
import java.util.List;

public interface NativeSpeechProcess {

	List<NativeVoice> getVoices();

	/**
	 * Blocking. Returns raw PCM in {@link NativeSpeech#AUDIO_FORMAT}, or null when the platform
	 * could not speak this - which is not fatal, the process stays usable.
	 * <p>
	 * Callers must serialise access.
	 */
	byte[] generateAudio(String voiceName, String text) throws IOException;

	boolean isAlive();

	void stop();
}
