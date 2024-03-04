package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.SpeakerTypes;
import lombok.Getter;

public class SpeakerConfiguration {
	@Getter
	SpeakerTypes type;
	@Getter
	private String name;
	@Getter
	private String model;
	@Getter
	private int voiceID;
	@Getter
	private int priority;
}
