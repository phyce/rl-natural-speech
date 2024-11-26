package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.EngineError;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

@Value
@AllArgsConstructor
public class SpeechEngineEvent {
	public enum Events {
		STARTING,
		START_NO_RUNTIME,
		START_NO_MODEL,
		START_DISABLED,
		START_CRASHED,
		STARTED,
		CRASHED,
		STOPPED,
	}
	@NonNull
	SpeechEngine speechEngine;
	@NonNull
	Events event;

	@Nullable
	EngineError error;

	public static SpeechEngineEvent STARTING(SpeechEngine speechEngine) {
		return new SpeechEngineEvent(speechEngine, Events.STARTING, null);
	}

	public static SpeechEngineEvent START_DISABLED(SpeechEngine speechEngine) {
		return new SpeechEngineEvent(speechEngine, Events.START_DISABLED, null);
	}

	public static SpeechEngineEvent START_CRASHED(EngineError error) {
		return new SpeechEngineEvent(error.engine, Events.START_CRASHED, error);
	}

	public static SpeechEngineEvent START_NO_RUNTIME(SpeechEngine speechEngine) {
		return new SpeechEngineEvent(speechEngine, Events.START_NO_RUNTIME, null);
	}

	public static SpeechEngineEvent START_NO_MODEL(SpeechEngine speechEngine) {
		return new SpeechEngineEvent(speechEngine, Events.START_NO_MODEL, null);
	}

	public static SpeechEngineEvent STARTED(SpeechEngine speechEngine) {
		return new SpeechEngineEvent(speechEngine, Events.STARTED, null);
	}

	public static SpeechEngineEvent CRASHED(EngineError error) {
		return new SpeechEngineEvent(error.engine, Events.CRASHED, error);
	}

	public static SpeechEngineEvent STOPPED(SpeechEngine speechEngine) {
		return new SpeechEngineEvent(speechEngine, Events.STOPPED, null);
	}


}

