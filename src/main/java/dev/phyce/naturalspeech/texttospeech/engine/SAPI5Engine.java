package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import static com.google.common.util.concurrent.Futures.submit;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.Voice;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Alias;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Process;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Process.SAPI5Voice;
import dev.phyce.naturalspeech.utils.PlatformUtil;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateError;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateOk;
import dev.phyce.naturalspeech.utils.StreamableFuture;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;

@Slf4j
@PluginSingleton
public class SAPI5Engine extends ManagedSpeechEngine {
	public static final String SAPI5_MODEL_NAME = "microsoft";
	private final ReentrantLock lock = new ReentrantLock();

	private final PluginExecutorService pluginExecutorService;

	@Getter
	@NonNull
	private final ImmutableSet<SAPI5Voice> nativeVoices = nativeVoices();

	@Getter // @Override
	private final ImmutableSet<Voice> voices = voices(nativeVoices);

	@Getter // @Override
	private final ImmutableSet<VoiceID> voiceIDs = voiceIDs(nativeVoices);

	@Nullable
	private SAPI5Process process = null;

	@Inject
	private SAPI5Engine(PluginExecutorService pluginExecutorService) {
		this.pluginExecutorService = pluginExecutorService;
	}

	@Override
	public @NonNull Result<StreamableFuture<Audio>, Rejection> generate(
		@NonNull VoiceID voiceID,
		@NonNull String text
	) {

		if (!isAlive()) return Error(Rejection.DEAD(this));
		if (!voiceIDs.contains(voiceID)) return Error(Rejection.REJECT(this));

		String sapiName = SAPI5Alias.modelToSapiName.getOrDefault(voiceID.id, voiceID.id);

		ListenableFuture<Audio> future = submit(() -> {
			Preconditions.checkNotNull(process);
			Preconditions.checkState(process.isAlive());

			Result<Audio, Exception> result = process.generateAudio(sapiName, text);
			return result.unwrap();
		}, pluginExecutorService);

		StreamableFuture<Audio> stream = StreamableFuture.singular(future);

		return Ok(stream);
	}

	@Override
	@NonNull
	ListenableFuture<Result<Void, EngineError>> startup() {
		try {
			lock.lock();

			if (isAlive()) return immediateOk();

			if (!PlatformUtil.IS_WINDOWS) {
				log.trace("Not windows, WSAPI5 fail.");
				return immediateError(EngineError.NO_RUNTIME(this));
			}

			if (nativeVoices.isEmpty()) {
				log.error("WSAPI5 process failed to start for WSAPI5 Engine");
				return immediateError(EngineError.NO_MODEL(this));
			}


			return submit(() -> {
				try {
					lock.lock();
					Result<SAPI5Process, Exception> result = SAPI5Process.start();

					if (result.isError()) {
						log.error("WSAPI5 process failed to start for WSAPI5 Engine");
						return Error(EngineError.UNEXPECTED_FAIL(this));
					}

					process = result.unwrap();

					return Ok();

				} finally {
					lock.unlock();
				}
			}, pluginExecutorService);
		} finally {
			lock.unlock();
		}
	}

	@Override
	void shutdown() {
		try {
			lock.lock();

			if (!isAlive()) return;

			Preconditions.checkNotNull(process);
			process.destroy();

		} finally {
			lock.unlock();
			process = null;
		}
	}

	private static ImmutableSet<SAPI5Voice> nativeVoices() {

		if (PlatformUtil.IS_WINDOWS) {
			// one-shot process to retrieve system voices
			Result<SAPI5Process, Exception> result = SAPI5Process.start();

			if (result.isOk()) {
				SAPI5Process voiceEnumeratorProcess = result.unwrap();
				voiceEnumeratorProcess.destroy();
				return voiceEnumeratorProcess.getVoices();

			}
			else {
				log.error("WSAPI5 process failed to start for WSAPI5 Engine");
			}
		}

		return ImmutableSet.of();
	}

	private static ImmutableSet<Voice> voices(@NonNull ImmutableSet<SAPI5Voice> nativeVoices) {
		return nativeVoices.stream()
			.map((nativeVoice) -> {
				String sapiName = nativeVoice.getName();
				String voiceName = SAPI5Alias.sapiToModelName.getOrDefault(sapiName, sapiName);
				VoiceID id = VoiceID.of(SAPI5_MODEL_NAME, voiceName);
				return Voice.of(id, nativeVoice.getGender());
			})
			.collect(ImmutableSet.toImmutableSet());
	}

	private static ImmutableSet<VoiceID> voiceIDs(@NonNull ImmutableSet<SAPI5Voice> nativeVoices) {
		return nativeVoices.stream()
			.map((nativeVoice) -> {
				String sapiName = nativeVoice.getName();
				String voiceName = SAPI5Alias.sapiToModelName.getOrDefault(sapiName, sapiName);
				return VoiceID.of(SAPI5_MODEL_NAME, voiceName);
			})
			.collect(ImmutableSet.toImmutableSet());
	}


	@Override
	public boolean isAlive() {
		return process != null && process.isAlive() && !nativeVoices.isEmpty();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.BUILTIN_OS;
	}

	@Override
	public @NonNull String getEngineName() {
		return "SAPI5Engine";
	}

}
