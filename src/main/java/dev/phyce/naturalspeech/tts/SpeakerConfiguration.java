package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.SpeakerTypes;
import lombok.Getter;

@Getter
public class SpeakerConfiguration {
	SpeakerTypes type;
	private String name;
	private String model;
	private int voiceID;
	private int priority;
}
