package dev.phyce.naturalspeech.tts.nativespeech;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NativeSpeechProcess {

	List<NativeVoice> getVoices();

	byte[] generateAudio(String systemName, String text) throws IOException;

	boolean isAlive();

	CompletableFuture<?> onExit();

	void stop();
}
