package dev.phyce.naturalspeech.events;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class TextToSpeechFailedStart {
	public enum Reason {
		NO_ENGINE
	}

	Reason reason;
}
