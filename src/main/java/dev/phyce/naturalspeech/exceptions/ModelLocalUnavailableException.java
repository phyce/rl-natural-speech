package dev.phyce.naturalspeech.exceptions;

import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;

public class ModelLocalUnavailableException extends RuntimeException {

	public VoiceID voiceID;

	public ModelLocalUnavailableException(String errMessage, VoiceID voiceID) {
		super(String.format("No model files for %s\n%s",
				voiceID.toVoiceIDString(), errMessage));
	}
	public ModelLocalUnavailableException(VoiceID voiceID) {
		super(String.format("No model files for: %s",
				voiceID.toVoiceIDString()));
	}

}
