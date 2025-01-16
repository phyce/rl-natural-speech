package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.PiperEngine;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperProcess;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository.PiperModel;
import lombok.Value;

// region events
@Value(staticConstructor="of")
public class PiperProcessEvent {
	Events event;
	PiperEngine modelEngine;
	PiperProcess process;
	PiperModel model;

	public static PiperProcessEvent SPAWNED(
		PiperEngine modelEngine,
		PiperProcess process,
		PiperModel model
	) {
		return of(Events.SPAWNED, modelEngine, process, model);
	}

	public static PiperProcessEvent DIED(
		PiperEngine modelEngine,
		PiperProcess process,
		PiperModel model
	) {
		return of(Events.DIED, modelEngine, process, model);
	}

	public static PiperProcessEvent CRASHED(
		PiperEngine modelEngine,
		PiperProcess process,
		PiperModel model
	) {
		return of(Events.CRASHED, modelEngine, process, model);
	}

	public static PiperProcessEvent BUSY(
		PiperEngine modelEngine,
		PiperProcess process,
		PiperModel model
	) {
		return of(Events.BUSY, modelEngine, process, model);
	}

	public static PiperProcessEvent DONE(
		PiperEngine modelEngine,
		PiperProcess process,
		PiperModel model
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
