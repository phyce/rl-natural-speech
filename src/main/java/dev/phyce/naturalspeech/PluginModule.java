package dev.phyce.naturalspeech;

public interface PluginModule {

	default void startUp() {}

	default void shutDown() {}

	default void resetConfiguration() {}
}
