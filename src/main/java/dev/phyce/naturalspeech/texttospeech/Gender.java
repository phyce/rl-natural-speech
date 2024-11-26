package dev.phyce.naturalspeech.texttospeech;

import dev.phyce.naturalspeech.texttospeech.engine.macos.avfoundation.AVSpeechSynthesisVoiceGender;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import net.runelite.api.Player;

@AllArgsConstructor
public enum Gender {
	OTHER("other"),
	MALE("male"),
	FEMALE("female");

	public final String string;

	@Override
	public String toString() {
		return string;
	}

	public static Gender fromInt(int number) {
		number = number % 2;

		switch (number) {
			case 0: return MALE;
			case 1: return FEMALE;
			default: return OTHER;
		}
	}

	public static Gender fromPlayer(Player player) {
		int genderId = player.getPlayerComposition().getGender();
		switch (genderId) {
			case 0: return MALE;
			case 1: return FEMALE;
			default: return OTHER;
		}
	}

	public static Gender parseString(@NonNull String str) {
		str = str.toLowerCase().strip();
		switch (str) {
			case "0":
			case "m":
			case "male":
				return Gender.MALE;
			case "1":
			case "f":
			case "female":
				return Gender.FEMALE;
			default:
				return Gender.OTHER;
		}
	}

	/**
	 * Convert an AVSpeechSynthesisVoiceGender to Gender
	 *
	 * @param avGender AVSpeechSynthesisVoiceGender found in AVFoundation
	 */
	public static Gender fromAVGender(AVSpeechSynthesisVoiceGender avGender) {
		switch (avGender) {
			case AVSpeechSynthesisVoiceGenderMale:
				return Gender.MALE;
			case AVSpeechSynthesisVoiceGenderFemale:
				return Gender.FEMALE;
			case AVSpeechSynthesisVoiceGenderUnspecified:
			default:
				return Gender.OTHER;
		}
	}
}
