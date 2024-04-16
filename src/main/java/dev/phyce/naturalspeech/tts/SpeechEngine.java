package dev.phyce.naturalspeech.tts;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.NonNull;

public interface SpeechEngine {

	enum SpeakResult {
		ACCEPT,
		REJECT,
	}

	enum StartResult {
		SUCCESS,
		FAILED
	}

	enum EngineType {
		BUILTIN_PLUGIN,
		BUILTIN_OS,
		EXTERNAL_DEPENDENCY,
		// NETWORKED (some day in the future?)
	}

	/**
	 * {@link TextToSpeech} will call speak for in-coming VoiceID,
	 * if the engine can speak the voiceID, speak and return true.
	 * Otherwise, returning false will allow TextToSpeech to find other engines to speak.
	 *
	 * @param voiceID      the voiceID to speak
	 * @param text         the text to speak
	 * @param gainSupplier a supplier that provides the dynamic gain value for the speech
	 * @param lineName     the name of the AudioEngine line to speak on
	 *
	 * @return {@link SpeakResult#ACCEPT} if speak was successful, {@link SpeakResult#REJECT} if the engine cannot speak this VoiceID.
	 */
	@NonNull
	SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName);

	@NonNull
	StartResult start();

	default ListenableFuture<StartResult> asyncStart(ExecutorService executorService) {
		return Futures.submit(this::start, executorService);
	}

	void stop();

	boolean isStarted();

	boolean canSpeak(VoiceID voiceID);

	/**
	 * Cancels queued and processing speak tasks. Cancel should not try to silence ongoing AudioEngine lines.
	 * For silencing + canceling, use {@link TextToSpeech#silence(Predicate)}
	 */
	void silence(Predicate<String> lineCondition);

	void silenceAll();

	@NonNull
	EngineType getEngineType();

	@NonNull
	String getEngineName();

}
