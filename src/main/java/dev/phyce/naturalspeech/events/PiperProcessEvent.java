package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperModelEngine;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperProcess;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import lombok.Value;

// region events
@Value(staticConstructor="of")
public class PiperProcessEvent {
	Events event;
	PiperModelEngine modelEngine;
	PiperProcess process;
	PiperRepository.PiperModel model;

	public static PiperProcessEvent SPAWNED(
		PiperModelEngine modelEngine,
		PiperProcess process,
		PiperRepository.PiperModel model
	) {
		return of(Events.SPAWNED, modelEngine, process, model);
	}

	public static PiperProcessEvent DIED(
		PiperModelEngine modelEngine,
		PiperProcess process,
		PiperRepository.PiperModel model
	) {
		return of(Events.DIED, modelEngine, process, model);
	}

	public static PiperProcessEvent CRASHED(
		PiperModelEngine modelEngine,
		PiperProcess process,
		PiperRepository.PiperModel model
	) {
		return of(Events.CRASHED, modelEngine, process, model);
	}

	public static PiperProcessEvent BUSY(
		PiperModelEngine modelEngine,
		PiperProcess process,
		PiperRepository.PiperModel model
	) {
		return of(Events.BUSY, modelEngine, process, model);
	}

	public static PiperProcessEvent DONE(
		PiperModelEngine modelEngine,
		PiperProcess process,
		PiperRepository.PiperModel model
	) {
		return of(Events.DONE, modelEngine, process, model);
	}

	public enum Events {
		SPAWNED,
		DIED,
		CRASHED,
		BUSY,
		DONE,
	}


}
