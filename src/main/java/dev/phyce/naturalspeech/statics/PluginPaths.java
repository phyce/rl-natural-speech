package dev.phyce.naturalspeech.statics;


import java.nio.file.Path;
import net.runelite.client.RuneLite;

public interface PluginPaths {

	Path NATURAL_SPEECH_PATH = RuneLite.RUNELITE_DIR.toPath().resolve("NaturalSpeech");
}
