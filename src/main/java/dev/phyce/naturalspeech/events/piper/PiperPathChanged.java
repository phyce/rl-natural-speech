package dev.phyce.naturalspeech.events.piper;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PiperPathChanged {
	Path newPath;
}
