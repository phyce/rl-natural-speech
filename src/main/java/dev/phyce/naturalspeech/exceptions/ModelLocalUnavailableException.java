package dev.phyce.naturalspeech.exceptions;

import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;

public class ModelLocalUnavailableException extends Exception {

	public VoiceID voiceID;

	public ModelLocalUnavailableException(VoiceID voiceID) {
		super("Voice ID Missing: " + voiceID.toVoiceIDString());
	}

}
