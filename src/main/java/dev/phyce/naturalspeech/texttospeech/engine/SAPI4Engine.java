package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static com.google.common.util.concurrent.Futures.submit;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.Voice;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4.SAPI4Repository;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4.SpeechAPI4;
import dev.phyce.naturalspeech.utils.PlatformUtil;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.Ok;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateError;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateOk;
import dev.phyce.naturalspeech.utils.StreamableFuture;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class SAPI4Engine extends ManagedSpeechEngine {

	// "microsoft" does not denote any specific models and has no lifetime
	// The VoiceID::ids are the actual models and can be available or not.
	// We want "microsoft:sam", not "sam:0"
	// A more generalized approach can be done at a later time.
	public static final String SAPI4_MODEL_NAME = "microsoft";

	private final PluginExecutorService pluginExecutorService;

	@NonNull
	private final ImmutableMap<String, SpeechAPI4> nativeVoices;

	@NonNull
	@Getter // @Override
	private final ImmutableSet<VoiceID> voiceIDs;

	@NonNull
	@Getter // @Override
	private final ImmutableSet<Voice> voices;

	@Inject
	public SAPI4Engine(SAPI4Repository sapi4Repository, PluginExecutorService pluginExecutorService) {
		this.pluginExecutorService = pluginExecutorService;

		nativeVoices = nativeVoices(sapi4Repository);
		voices = voices(nativeVoices);
		voiceIDs = voiceIDs(nativeVoices);
	}


	@Override
	public @NonNull Result<StreamableFuture<Audio>, Rejection> generate(
		@NonNull VoiceID voiceID,
		@NonNull String text
	) {
		if (!isAlive()) return Error(Rejection.DEAD(this));
		if (!voiceIDs.contains(voiceID)) return Error(Rejection.REJECT(this));

		SpeechAPI4 sapi = Preconditions.checkNotNull(nativeVoices.get(voiceID.id));


		ListenableFuture<Audio> future = submit(() -> {
			var result = sapi.generate(text);
			return result.unwrap();
		}, pluginExecutorService);


		return Ok(StreamableFuture.singular(future));
	}

	@Override
	public boolean isAlive() {
		return !nativeVoices.isEmpty();
	}

	@Override
	@Synchronized
	@NonNull
	ListenableFuture<Result<Void, EngineError>> startup() {

		if (isAlive()) return immediateOk();

		if (!PlatformUtil.IS_WINDOWS) {
			log.trace("Not windows, SAPI4 skipping");
			return immediateError(EngineError.NO_RUNTIME(this));
		}

		if (nativeVoices.isEmpty()) {
			return immediateError(EngineError.NO_MODEL(this));
		}

		return immediateOk();
	}

	@Override
	@Synchronized
	void shutdown() {
	}

	@NonNull
	private static ImmutableMap<String, SpeechAPI4> nativeVoices(SAPI4Repository sapi4Repository) {
		final ImmutableMap<String, SpeechAPI4> sapi4s;
		ImmutableMap.Builder<String, SpeechAPI4> sapi4Builder = ImmutableMap.builder();

		ImmutableList<String> voiceNames = sapi4Repository.getVoices();
		for (String voiceName : voiceNames) {
			Result<SpeechAPI4, Exception> result = SpeechAPI4.build(voiceName, RuntimePathConfig.SAPI4_PATH);
			result.ifOk(sapi -> sapi4Builder.put(voiceName, sapi));
		}

		sapi4s = sapi4Builder.build();
		return sapi4s;
	}

	@NonNull
	private static ImmutableSet<Voice> voices(ImmutableMap<String, SpeechAPI4> nativeVoices) {
		ImmutableSet.Builder<Voice> voiceBuilder = ImmutableSet.builder();
		nativeVoices.forEach((voiceName, sapi) -> {
			VoiceID voiceID = VoiceID.of(SAPI4_MODEL_NAME, voiceName);
			voiceBuilder.add(Voice.of(voiceID, sapi.getGender()));
		});

		return voiceBuilder.build();
	}

	@NonNull
	private static ImmutableSet<VoiceID> voiceIDs(ImmutableMap<String, SpeechAPI4> nativeVoices) {
		ImmutableSet.Builder<VoiceID> voiceIDBuilder = ImmutableSet.builder();
		nativeVoices.forEach((voiceName, sapi) -> {
			VoiceID voiceID = VoiceID.of(SAPI4_MODEL_NAME, voiceName);
			voiceIDBuilder.add(voiceID);
		});

		return voiceIDBuilder.build();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.EXTERNAL_DEPENDENCY;
	}

	@Override
	public @NonNull String getEngineName() {
		return "SAPI4Engine";
	}
}
