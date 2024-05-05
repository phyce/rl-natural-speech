package dev.phyce.naturalspeech.configs;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpeechEngineDatum {
	String engineName;
	boolean enabled;
}
