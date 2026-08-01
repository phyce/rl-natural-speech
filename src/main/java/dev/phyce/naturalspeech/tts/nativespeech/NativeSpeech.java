package dev.phyce.naturalspeech.tts.nativespeech;

import com.google.common.collect.ImmutableSet;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.IOException;
import java.util.Set;
import javax.sound.sampled.AudioFormat;

public final class NativeSpeech {

	public static final String WINDOWS_MODEL_NAME = "microsoft";
	public static final String MACOS_MODEL_NAME = "macos";

	private static final Set<String> MODEL_NAMES = ImmutableSet.of(WINDOWS_MODEL_NAME, MACOS_MODEL_NAME);

	public static final AudioFormat AUDIO_FORMAT = new AudioFormat(
		AudioFormat.Encoding.PCM_SIGNED,
		22050.0F, // sample rate
		16, // sample size in bits
		1, // channels
		2, // frame size
		22050.0F, // frame rate
		false); // little endian

	private NativeSpeech() {}

	/** macOS joins once MacSpeechProcess lands. */
	public static boolean isSupported() {
		return OSValidator.IS_WINDOWS;
	}

	public static boolean isNativeModel(String modelName) {
		return MODEL_NAMES.contains(modelName);
	}

	static NativeSpeechProcess startProcess() throws IOException {
		if (OSValidator.IS_WINDOWS) return WindowsSpeechProcess.start();

		throw new IOException("No system speech implementation for " + System.getProperty("os.name"));
	}
}
