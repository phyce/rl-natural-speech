package dev.phyce.naturalspeech.events.piper;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperRepositoryChanged {
	public String modelName;
}
