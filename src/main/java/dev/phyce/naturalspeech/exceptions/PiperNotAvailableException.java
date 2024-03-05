package dev.phyce.naturalspeech.exceptions;

import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;

public class PiperNotAvailableException extends RuntimeException {
	public PiperNotAvailableException(VoiceID voiceID) {
		super(String.format("No Piper instance for %s",
				voiceID.toVoiceIDString()));
	}
	public PiperNotAvailableException(String errMessage, VoiceID voiceID) {
		super(String.format("No Piper instance for %s\n%s",
				voiceID.toVoiceIDString(), errMessage));
	}
}
