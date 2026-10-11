package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.IOException;
import java.util.Set;

public final class NativeSpeech {

	public static final String WINDOWS_MODEL_NAME = "microsoft";
	public static final String MACOS_MODEL_NAME = "macos";

	private NativeSpeech() {}

	public static boolean isSupported() {
		return OSValidator.IS_WINDOWS || OSValidator.IS_MAC;
	}

	public static boolean isNativeModel(String modelName) {
		return WINDOWS_MODEL_NAME.equals(modelName) || MACOS_MODEL_NAME.equals(modelName);
	}

	static NativeSpeechProcess startProcess() throws IOException {
		if (OSValidator.IS_WINDOWS) return WindowsSpeechProcess.start();
		if (OSValidator.IS_MAC) return MacSpeechProcess.start();

		throw new IOException("No system voices for " + System.getProperty("os.name"));
	}

	static String uniqueID(String id, Set<String> usedIDs) {
		String unique = id;
		for (int suffix = 2; !usedIDs.add(unique); suffix++) {
			unique = id + suffix;
		}
		return unique;
	}
}
