package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperModel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperModelStopped {
	PiperModel piper;
}
