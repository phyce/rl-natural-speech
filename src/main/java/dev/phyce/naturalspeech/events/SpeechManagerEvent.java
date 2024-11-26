package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import lombok.Value;

@Value(staticConstructor = "of")
public class SpeechManagerEvent {
	SpeechManager speechManager;
	Event event;
	public enum Event {
		STARTING,
		STARTED,
		STOPPED;
	}

	public static SpeechManagerEvent STARTING(SpeechManager speechManager) {
		return new SpeechManagerEvent(speechManager, Event.STARTING);
	}

	public static SpeechManagerEvent STARTED(SpeechManager speechManager) {
		return new SpeechManagerEvent(speechManager, Event.STARTED);
	}

	public static SpeechManagerEvent STOPPED(SpeechManager speechManager) {
		return new SpeechManagerEvent(speechManager, Event.STOPPED);
	}

}
