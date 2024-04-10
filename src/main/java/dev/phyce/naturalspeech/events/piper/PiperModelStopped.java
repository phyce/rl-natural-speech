package dev.phyce.naturalspeech.events.piper;

import dev.phyce.naturalspeech.tts.piper.PiperModel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperModelStopped {
	PiperModel piper;
}
