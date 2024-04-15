package dev.phyce.naturalspeech.tts.wsapi4;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.tts.SpeechEngine;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class SAPI4Engine implements SpeechEngine {

	private final SAPI4Repository sapi4Repository;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final AudioEngine audioEngine;
	private final VoiceManager voiceManager;

	private final Map<String, SpeechAPI4> sapi4s = new HashMap<>();

	// "microsoft" does not denote any specific models and has no lifetime
	// The VoiceID::ids are the actual models and can be available or not.
	// We want "microsoft:sam", not "sam:0"
	// A more generalized approach can be done at a later time.
	public static final String SAPI4_MODEL_NAME = "microsoft";

	@Getter
	private boolean started = false;

	@Inject
	public SAPI4Engine(
		SAPI4Repository sapi4Repository,
		NaturalSpeechRuntimeConfig runtimeConfig,
		AudioEngine audioEngine,
		VoiceManager voiceManager
	) {
		this.sapi4Repository = sapi4Repository;
		this.runtimeConfig = runtimeConfig;
		this.audioEngine = audioEngine;
		this.voiceManager = voiceManager;

		if (!OSValidator.IS_WINDOWS) {
			return;
		}

		if (NaturalSpeechPlugin._SIMULATE_NO_TTS) {
			return;
		}

		List<String> voiceNames = sapi4Repository.getVoices();
		for (String voiceName : voiceNames) {
			SpeechAPI4 sapi = SpeechAPI4.start(audioEngine, voiceName, runtimeConfig.getSAPI4Path());
			if (sapi != null) {
				sapi4s.put(voiceName, sapi);
				voiceManager.registerVoiceID(new VoiceID(SAPI4_MODEL_NAME, voiceName), sapi.getGender());
			}
		}
	}

	@Override
	public SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {
		if (!Objects.equals(voiceID.modelName, SAPI4_MODEL_NAME)) {
			return SpeakResult.REJECT;
		}

		SpeechAPI4 sapi = sapi4s.get(voiceID.id);
		if (sapi == null) {
			return SpeakResult.REJECT;
		}

		sapi.speak(text, gainSupplier, lineName);
		return SpeakResult.ACCEPT;
	}

	@Override
	public StartResult start() {
		// SAPI4 models don't have lifecycles and does not need to be cleared on stop
		if (sapi4s.isEmpty()) {
			started = false;
			return StartResult.FAILED;
		} else {
			started = true;
			return StartResult.SUCCESS;
		}

	}

	@Override
	public void stop() {
		started = false;
	}

	@Override
	public boolean canSpeakAny() {
		if (!started) return false;

		return !sapi4s.isEmpty();
	}

	@Override
	public boolean canSpeak(VoiceID voiceID) {
		return sapi4s.containsKey(voiceID.id);
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		audioEngine.closeLineConditional(lineCondition);
	}

	@Override
	public void silenceAll() {
		audioEngine.closeAll();
	}
}
