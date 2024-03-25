package dev.phyce.naturalspeech.tts.wsapi4;

import lombok.Data;

@Data
public class SAPI4Model {
	String name;
	int minPitch;
	int maxPitch;
	int minSpeed;
	int maxSpeed;
}
