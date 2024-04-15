package dev.phyce.naturalspeech.tts.wsapi5;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.tts.SpeechEngine;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import static dev.phyce.naturalspeech.tts.wsapi5.SAPI5Process.AUDIO_FORMAT;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.AudioInputStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class SAPI5Engine implements SpeechEngine {

	private final AudioEngine audioEngine;
	private final VoiceManager voiceManager;

	private SAPI5Process process;

	public static final String SAPI5_MODEL_NAME = "microsoft";

	@Getter
	private final List<SAPI5Process.SAPI5Voice> availableModels;

	@Getter
	private boolean started = false;

	@Inject
	private SAPI5Engine(
		AudioEngine audioEngine, VoiceManager voiceManager
	) {
		this.audioEngine = audioEngine;
		this.voiceManager = voiceManager;

		SAPI5Process sapi5 = SAPI5Process.start();
		if (sapi5 != null) {
			availableModels = Collections.unmodifiableList(sapi5.getAvailableVoices());
			sapi5.destroy();
		} else {
			availableModels = Collections.unmodifiableList(new ArrayList<>());
		}
	}

	@Override
	public SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {
		if (!canSpeak(voiceID)) return SpeakResult.REJECT;

		String sapiName = SAPI5Alias.modelToSapiName.getOrDefault(voiceID.id, voiceID.id);

		process.generateAudio(sapiName, text,
			(audio) -> {
				if (audio == null) return; // already logged error on null

				ByteArrayInputStream byteStream = new ByteArrayInputStream(audio);
				try (AudioInputStream audioStream = new AudioInputStream(byteStream, AUDIO_FORMAT, audio.length)) {
					audioEngine.play(lineName, audioStream, gainSupplier);
				} catch (IOException e) {
					log.error("Failed to stream audio bytes(length:{})into AudioInputStream for playback.",
						audio.length);
				}
			}
		);

		return SpeakResult.ACCEPT;
	}

	@Override
	public StartResult start() {
		if (!OSValidator.IS_WINDOWS) {
			log.trace("Not windows, WSAPI5 fail.");
			return StartResult.FAILED;
		}

		if (started) {
			stop();
		}

		process = SAPI5Process.start();

		if (process == null) {
			log.error("WSAPI5 process failed to start for WSAPI5 Engine");
			return StartResult.FAILED;
		}

		for (SAPI5Process.SAPI5Voice model : availableModels) {
			String sapiName = model.getName();
			String modelName = SAPI5Alias.sapiToVoiceName.getOrDefault(sapiName, sapiName);
			// SAPI5 have a virtual "microsoft" model, the actual model fits in the id.
			voiceManager.registerVoiceID(new VoiceID(SAPI5_MODEL_NAME, modelName), model.getGender());
		}

		started = true;
		return StartResult.SUCCESS;
	}

	@Override
	public void stop() {
		process.destroy();
		process = null;

		for (SAPI5Process.SAPI5Voice model : availableModels) {
			String sapiName = model.getName();
			String modelName = SAPI5Alias.sapiToVoiceName.getOrDefault(sapiName, sapiName);
			// SAPI5 have a virtual "microsoft" model, the actual model fits in the id.
			voiceManager.unregisterVoiceID(new VoiceID(SAPI5_MODEL_NAME, modelName));
		}

	}

	@Override
	public boolean canSpeakAny() {
		return !process.getAvailableVoices().isEmpty();
	}

	@Override
	public boolean canSpeak(VoiceID voiceID) {
		if (availableModels == null) return false;

		if (!voiceID.modelName.equals(SAPI5_MODEL_NAME)) {
			return false;
		}

		String sapiName = SAPI5Alias.modelToSapiName.getOrDefault(voiceID.id, voiceID.id);

		return availableModels.stream().anyMatch(voice -> voice.getName().equals(sapiName));
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
