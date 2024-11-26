package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.util.concurrent.ListenableFuture;
import dev.phyce.naturalspeech.utils.Result;
import lombok.NonNull;

abstract class ManagedSpeechEngine implements SpeechEngine {

	abstract @NonNull ListenableFuture<Result<Void, EngineError>> startup();
	abstract void shutdown();
}
