package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.util.concurrent.ListenableFuture;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.NonNull;

public interface SpeechEngine {

	enum SpeakStatus {
		ACCEPT,
		REJECT,
	}

	enum StartResult {
		SUCCESS,
		FAILED,
		NOT_INSTALLED,
		DISABLED
	}

	enum EngineType {
		BUILTIN_PLUGIN,
		BUILTIN_OS,
		EXTERNAL_DEPENDENCY,
		// NETWORKED (some day in the future?)
	}

	/**
	 * {@link SpeechManager} will call speak for in-coming VoiceID,
	 * if the engine can speak the voiceID, speak and return true.
	 * Otherwise, returning false will allow TextToSpeech to find other engines to speak.
	 *
	 * @param voiceID      the voiceID to speak
	 * @param text         the text to speak
	 * @param gainSupplier a supplier that provides the dynamic gain value for the speech
	 * @param lineName     the name of the AudioEngine line to speak on
	 *
	 * @return {@link SpeakStatus#ACCEPT} if speak was successful, {@link SpeakStatus#REJECT} if the engine cannot speak this VoiceID.
	 */
	@NonNull
	SpeechEngine.SpeakStatus speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName);

	ListenableFuture<StartResult> start(ExecutorService executorService);

	void stop();

	boolean isStarted();

	boolean contains(VoiceID voiceID);

	/**
	 * Cancels queued and processing speak tasks. Cancel should not try to silence ongoing AudioEngine lines.
	 * For silencing + canceling, use {@link SpeechManager#silence(Predicate)}
	 */
	void silence(Predicate<String> lineCondition);

	void silenceAll();

	@NonNull
	EngineType getEngineType();

	@NonNull
	String getEngineName();

}
