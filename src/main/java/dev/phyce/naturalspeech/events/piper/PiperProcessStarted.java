package dev.phyce.naturalspeech.events.piper;

import dev.phyce.naturalspeech.tts.piper.PiperModel;
import dev.phyce.naturalspeech.tts.piper.PiperProcess;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperProcessStarted {
	PiperModel piper;
	PiperProcess process;
}
