package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor
@Value
public class VoiceMetadata {
	public String name;
	public Gender gender;
	public VoiceID voiceId;

	public static VoiceMetadata from(PiperRepository.PiperVoiceMetadata piperMetadata) {
		return new VoiceMetadata(piperMetadata.getName(), piperMetadata.getGender(), piperMetadata.toVoiceID());
	}
}
