package dev.phyce.naturalspeech.jna.macos.avfoundation;

import static com.google.common.base.Preconditions.checkState;
import dev.phyce.naturalspeech.enums.Gender;
import lombok.Getter;

@Getter
public enum AVSpeechSynthesisVoiceGender {
	AVSpeechSynthesisVoiceGenderUnspecified(0),
	AVSpeechSynthesisVoiceGenderMale(1),
	AVSpeechSynthesisVoiceGenderFemale(2)

	; // End of enum

	public final int value;

	AVSpeechSynthesisVoiceGender(int value) {this.value = value;}

	public static AVSpeechSynthesisVoiceGender fromValue(int value) {
		checkState(value >= 0 && value <= 2, "Invalid value for AVSpeechSynthesisVoiceGender: " + value);

		return values()[value];
	}



}