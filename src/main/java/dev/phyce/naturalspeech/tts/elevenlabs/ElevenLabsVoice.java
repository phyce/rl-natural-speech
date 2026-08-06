package dev.phyce.naturalspeech.tts.elevenlabs;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

/** One voice from the user's ElevenLabs library, as returned by {@code GET /v2/voices}. */
@Value
public class ElevenLabsVoice {

	/** The ElevenLabs voice id, ex 21m00Tcm4TlvDq8ikWAM. */
	String id;
	/** Display name from the library, ex Rachel. */
	String name;
	/** From {@code labels.gender}; {@link Gender#OTHER} when unlabelled. */
	Gender gender;

	public VoiceID toVoiceID() {
		return ElevenLabs.voiceID(id);
	}

	@Override
	public String toString() {
		return name + " (" + id + ")";
	}
}
