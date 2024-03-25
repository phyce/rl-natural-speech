package dev.phyce.naturalspeech.tts.wsapi4;

import dev.phyce.naturalspeech.tts.VoiceID;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeechAPI4 {

	private final String modelName;

	private final Path sapi4Path;

	private SpeechAPI4(String modelName, Path sapi4Path) {
		this.modelName = modelName;
		this.sapi4Path = sapi4Path;
	}

	public static SpeechAPI4 start(String modelName, Path sapi4Path) {
		return new SpeechAPI4(modelName, sapi4Path);
	}

	public void speak(
		String text,
		VoiceID voiceID,
		float volumnDb,
		String audioQueueName) {
		log.debug("Fake Speech API 4 speaking with voice {}: {}", modelName, text);
	}

}
