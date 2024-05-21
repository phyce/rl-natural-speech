package dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import dev.phyce.naturalspeech.utils.Platforms;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class SAPI4Engine implements SpeechEngine {
	// need lombok to expose secret lock because future thread needs to synchronize on the lock
	private final Object lock = new Object[0];

	// "microsoft" does not denote any specific models and has no lifetime
	// The VoiceID::ids are the actual models and can be available or not.
	// We want "microsoft:sam", not "sam:0"
	// A more generalized approach can be done at a later time.
	public static final String SAPI4_MODEL_NAME = "microsoft";

	private final AudioEngine audioEngine;
	private final VoiceManager voiceManager;

	private final Map<String, SpeechAPI4> sapi4s = new HashMap<>();


	@Getter
	private boolean started = false;

	@Inject
	public SAPI4Engine(
		SAPI4Repository sapi4Repository,
		RuntimePathConfig runtimeConfig,
		AudioEngine audioEngine,
		VoiceManager voiceManager
	) {
		this.audioEngine = audioEngine;
		this.voiceManager = voiceManager;

		if (!Platforms.IS_WINDOWS) {
			return;
		}

		if (NaturalSpeechPlugin._SIMULATE_NO_TTS || NaturalSpeechPlugin._SIMULATE_MINIMUM_MODE) {
			return;
		}

		List<String> voiceNames = sapi4Repository.getVoices();
		for (String voiceName : voiceNames) {
			SpeechAPI4 sapi = SpeechAPI4.start(audioEngine, voiceName, runtimeConfig.getSAPI4Path());
			if (sapi != null) {
				sapi4s.put(voiceName, sapi);
			}
		}
	}

	@Override
	public @NonNull SpeechEngine.SpeakStatus speak(
		VoiceID voiceID,
		String text,
		Supplier<Float> gainSupplier,
		String lineName
	) {
		if (!Objects.equals(voiceID.modelName, SAPI4_MODEL_NAME)) {
			return SpeakStatus.REJECT;
		}

		SpeechAPI4 sapi = sapi4s.get(voiceID.id);
		if (sapi == null) {
			return SpeakStatus.REJECT;
		}

		sapi.speak(text, gainSupplier, lineName);
		return SpeakStatus.ACCEPT;
	}

	@Deprecated(since="Do not start engines directly, use TextToSpeech::startEngine.")
	@Override
	@Synchronized("lock")
	public ListenableFuture<StartResult> start(ExecutorService executorService) {

		if (!Platforms.IS_WINDOWS) {
			log.trace("Not windows, SAPI4 skipping");
			return Futures.immediateFuture(StartResult.NOT_INSTALLED);
		}

		return Futures.submit(() -> {
			synchronized (lock) {
				StartResult result;
				// SAPI4 models don't have lifecycles and does not need to be cleared on stop
				if (sapi4s.isEmpty()) {
					started = false;
					result = StartResult.FAILED;
				}
				else {

					sapi4s.forEach((voiceName, sapi) -> {
						voiceManager.register(new VoiceID(SAPI4_MODEL_NAME, voiceName), sapi.getGender());
					});

					started = true;
					result = StartResult.SUCCESS;


				}
				return result;
			}
		}, executorService);
	}

	@Deprecated(since="Do not stop engines directly, use TextToSpeech::stopEngine")
	@Override
	@Synchronized("lock")
	public void stop() {
		sapi4s.forEach((voiceName, sapi) -> {
			voiceManager.unregister(new VoiceID(SAPI4_MODEL_NAME, voiceName));
		});
		started = false;
	}

	@Override
	public boolean contains(VoiceID voiceID) {
		return sapi4s.containsKey(voiceID.id);
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		audioEngine.closeConditional(lineCondition);
	}

	@Override
	public void silenceAll() {
		audioEngine.closeAll();
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
