package dev.phyce.naturalspeech.enums;

import lombok.NonNull;

public enum Gender {
	OTHER,
	MALE,
	FEMALE;

	public static Gender parseInt(int id) {
		switch (id) {
			case 0:
				return MALE;
			case 1:
				return FEMALE;
			default:
				return OTHER;
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

}
