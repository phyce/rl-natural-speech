package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.tts.SpeechEngine;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class SpeechEngineStarted {
	SpeechEngine speechEngine;
}
