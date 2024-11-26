package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode(callSuper=true)
@Value
public class EngineError extends Throwable {
	public enum Reason {
		ALREADY_STARTED,
		UNEXPECTED_FAIL,
		MULTIPLE_REASONS,
		NO_RUNTIME,
		NO_MODEL,
		DISABLED;
	}

	@NonNull
	public SpeechEngine engine;
	@NonNull
	public EngineError.Reason reason;
	@NonNull
	public ImmutableList<EngineError> childs;

	public static EngineError UNEXPECTED_FAIL(SpeechEngine engine) {
		return new EngineError(engine, Reason.UNEXPECTED_FAIL);
	}
	public static EngineError MULTIPLE_REASONS(SpeechEngine engine, List<EngineError> errors) {
		return new EngineError(engine, errors);
	}

	public static EngineError NO_RUNTIME(SpeechEngine engine) {
		return new EngineError(engine, Reason.NO_RUNTIME);
	}

	public static EngineError NO_MODEL(SpeechEngine engine) {
		return new EngineError(engine, Reason.NO_MODEL);
	}

	public static EngineError DISABLED(SpeechEngine engine) {
		return new EngineError(engine, Reason.DISABLED);
	}

	public static EngineError ALREADY_STARTED(SpeechEngine engine) {
		return new EngineError(engine, Reason.ALREADY_STARTED);
	}

	public EngineError(@NonNull SpeechEngine engine, @NonNull Reason reason) {
		super();
		this.engine = engine;
		this.reason = reason;
		this.childs = ImmutableList.of();
	}

	public EngineError(@NonNull SpeechEngine engine, @NonNull List<EngineError> childs) {
		super(childs.toString());
		this.engine = engine;
		this.reason = Reason.MULTIPLE_REASONS;
		this.childs = ImmutableList.copyOf(childs);
	}

	public void log() {
		switch (reason) {
			case NO_RUNTIME:
			case DISABLED:
				log.trace("engine skipped {} because {}", engine.getEngineName(), reason);
				break;
			case MULTIPLE_REASONS:
				log.trace("Multiple reasons for engine {}: ", engine.getEngineName());
				this.childs.forEach(EngineError::log);
				break;
			case NO_MODEL:
			case UNEXPECTED_FAIL:
			default:
				log.error("Failed to start engine {}", engine.getEngineName(), this);
				break;
		}
	}
}


