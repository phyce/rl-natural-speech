package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository.PiperModel;
import lombok.Value;

@Value(staticConstructor = "of")
public class PiperModelEngineEvent {
	Events event;
	PiperModel model;

	public static PiperModelEngineEvent STARTED(PiperModel model) {
		return of(Events.STARTED, model);
	}

	public static PiperModelEngineEvent STOPPED(PiperModel model) {
		return of(Events.STOPPED, model);
	}

	public enum Events {
		STARTED,
		STOPPED
	}
}
