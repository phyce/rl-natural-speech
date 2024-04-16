package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.tts.piper.PiperModel;
import dev.phyce.naturalspeech.tts.piper.PiperProcess;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperProcessCrashed {
	PiperModel model;
	PiperProcess process;
}
