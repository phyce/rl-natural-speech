package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

@Value
public class NativeVoice {

	String modelName;
	String id;
	String systemName;
	Gender gender;

	public VoiceID toVoiceID() {
		return new VoiceID(modelName, id);
	}
}
