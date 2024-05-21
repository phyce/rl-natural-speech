package dev.phyce.naturalspeech.events;

import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Value;

@Deprecated(since="1.3.0 We have an installer which installs to a standard location, no more path changes.")
@Value
@AllArgsConstructor
public class PiperPathChanged {
	Path newPath;
}
