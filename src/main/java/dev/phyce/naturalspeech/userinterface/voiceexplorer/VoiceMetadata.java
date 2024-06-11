package dev.phyce.naturalspeech.userinterface.voiceexplorer;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor
@Value
public class VoiceMetadata {
	public String name;
	public Gender gender;
	public VoiceID voiceId;

	public static VoiceMetadata from(PiperRepository.PiperVoice piperMetadata) {
		return new VoiceMetadata(piperMetadata.getName(), piperMetadata.getGender(), piperMetadata.toVoiceID());
	}
}
