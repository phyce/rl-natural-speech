package dev.phyce.naturalspeech.enums;

public enum Gender {
	OTHER,
	MALE,
	FEMALE;

	public static Gender parseInt(int id) {
		if (id == 0) {
			return MALE;
		}
		else if (id == 1) {
			return FEMALE;
		}
		else {
			return OTHER;
		}
	}

}
