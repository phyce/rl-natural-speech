package dev.phyce.naturalspeech.exceptions;

import dev.phyce.naturalspeech.tts.VoiceID;

public class PiperNotActiveException extends RuntimeException {
	public PiperNotActiveException(VoiceID voiceID) {
		super(String.format("No Piper instance for %s",
			voiceID.toVoiceIDString()));
	}

	public PiperNotActiveException(String errMessage, VoiceID voiceID) {
		super(String.format("No Piper instance for %s\n%s",
			voiceID.toVoiceIDString(), errMessage));
	}
}
