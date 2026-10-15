package dev.phyce.naturalspeech.tts.elevenlabs;

import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

@Value
public class ElevenLabsVoice {
	String id;
	String name;
	Gender gender;

	public VoiceID toVoiceID(ElevenLabsModel model) {
		return new VoiceID(model.getId(), id);
	}
}
