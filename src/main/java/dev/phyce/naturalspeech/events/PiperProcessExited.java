package dev.phyce.naturalspeech.events;

import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperModel;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperProcess;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperProcessExited {
	PiperModel piper;
	PiperProcess process;
}
