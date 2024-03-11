package dev.phyce.naturalspeech.configs.json.ttsconfigs;

import lombok.Value;

@Value
public class PiperConfigDatum {
	String modelName;
	boolean enabled;
	int processCount;
}
