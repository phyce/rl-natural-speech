package dev.phyce.naturalspeech.tts;

import java.util.function.Predicate;
import java.util.function.Supplier;

public interface SpeechEngine {

	enum SpeakResult {
		ACCEPT,
		REJECT,
	}

	enum StartResult {
		SUCCESS,
		FAILED
	}

	StartResult start();

	void stop();

	boolean canSpeak();

	/**
	 * {@link TextToSpeech} will call speak for in-coming VoiceID,
	 * if the engine can speak the voiceID, speak and return true.
	 * Otherwise, returning false will allow TextToSpeech to find other engines to speak.
	 *
	 * @param voiceID the voiceID to speak
	 * @param text the text to speak
	 * @param gainSupplier a supplier that provides the dynamic gain value for the speech
	 * @param lineName the name of the AudioEngine line to speak on
	 * @return true if speak was successful, false if the engine cannot speak this VoiceID.
	 */
	SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName);

	void cancel(Predicate<String> lineCondition);

	void cancelAll();

}
