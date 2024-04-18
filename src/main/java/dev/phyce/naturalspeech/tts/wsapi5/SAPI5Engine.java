package dev.phyce.naturalspeech.tts.wsapi5;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
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
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.sound.sampled.AudioInputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginSingleton
public class SAPI5Engine implements SpeechEngine {
	// need lombok to expose secret lock because future thread needs to synchronize on the lock
	private final Object lock = new Object[0];

	private final AudioEngine audioEngine;
	private final VoiceManager voiceManager;

	private SAPI5Process process;

	public static final String SAPI5_MODEL_NAME = "microsoft";

	@Getter
	private final List<SAPI5Process.SAPI5Voice> availableSAPI5s;

	@Getter
	private boolean started = false;

	@Inject
	private SAPI5Engine(
		AudioEngine audioEngine, VoiceManager voiceManager
	) {
		this.audioEngine = audioEngine;
		this.voiceManager = voiceManager;

		if (NaturalSpeechPlugin._SIMULATE_NO_TTS) {
			availableSAPI5s = Collections.unmodifiableList(new ArrayList<>());
			return;
		}

		if (!OSValidator.IS_WINDOWS) {
			log.trace("Not windows, SAPI5 skipping");
			availableSAPI5s = Collections.unmodifiableList(new ArrayList<>());
			return;
		}


		SAPI5Process sapi5 = SAPI5Process.start();
		if (sapi5 != null) {
			availableSAPI5s = Collections.unmodifiableList(sapi5.getAvailableVoices());
			sapi5.destroy();

			for (SAPI5Process.SAPI5Voice model : availableSAPI5s) {
				String sapiName = model.getName();
				String modelName = SAPI5Alias.sapiToModelName.getOrDefault(sapiName, sapiName);
				// SAPI5 have a virtual "microsoft" model, the actual model fits in the id.
				voiceManager.registerVoiceID(new VoiceID(SAPI5_MODEL_NAME, modelName), model.getGender());
			}
		}
		else {
			availableSAPI5s = Collections.unmodifiableList(new ArrayList<>());
		}
	}

	@Override
	public @NonNull SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {
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

	@Deprecated(since = "Do not start engines directly, use TextToSpeech::startEngine.")
	@Override
	@Synchronized("lock")
	public ListenableFuture<StartResult> start(ExecutorService executorService) {

		return Futures.submit(() -> {
			synchronized (lock) {
				if (NaturalSpeechPlugin._SIMULATE_NO_TTS) {
					return StartResult.FAILED;
				}

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


				started = true;

				return StartResult.SUCCESS;
			}
		}, executorService);
	}


	@Deprecated(since="Do not stop engines directly, use TextToSpeech::stopEngine")
	@Override
	@Synchronized("lock")
	public void stop() {

		process.destroy();
		process = null;

		for (SAPI5Process.SAPI5Voice model : availableSAPI5s) {
			String sapiName = model.getName();
			String modelName = SAPI5Alias.sapiToModelName.getOrDefault(sapiName, sapiName);
		}

		started = false;
	}

	@Override
	public boolean canSpeak(VoiceID voiceID) {
		if (availableSAPI5s == null) return false;

		if (!voiceID.modelName.equals(SAPI5_MODEL_NAME)) {
			return false;
		}

		String sapiName = SAPI5Alias.modelToSapiName.getOrDefault(voiceID.id, voiceID.id);

		return availableSAPI5s.stream().anyMatch(voice -> voice.getName().equals(sapiName));
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		audioEngine.closeLineConditional(lineCondition);
	}

	@Override
	public void silenceAll() {
		audioEngine.closeAll();
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
