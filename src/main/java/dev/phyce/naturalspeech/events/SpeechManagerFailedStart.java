package dev.phyce.naturalspeech.events;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class SpeechManagerFailedStart {
	public enum Reason {
		ALL_DISABLED,
		NOT_INSTALLED,
		ALL_FAILED
	}

	Reason reason;
}
