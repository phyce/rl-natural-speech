package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.guice.PluginSingleton;
import net.runelite.client.eventbus.EventBus;

// Private event bus inside Plugin scope
@PluginSingleton
public class PluginEventBus extends EventBus {}
