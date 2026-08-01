package dev.phyce.naturalspeech.tts.nativespeech;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

/** A voice the operating system already has installed, whichever platform that is. */
@Value
public class NativeVoice {

	String modelName;
	/** Short and typeable, what goes in a voice setting: david, zira. */
	String id;
	/** What the platform itself calls the voice, ex Microsoft David Desktop. */
	String systemName;
	Gender gender;
	/** BCP-47 language tag, ex en-GB. */
	String language;

	public VoiceID toVoiceID() {
		return new VoiceID(modelName, id);
	}
}
