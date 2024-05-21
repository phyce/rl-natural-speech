package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class SpeechEngineSkippedEngine {
	SpeechEngine engine;
}
