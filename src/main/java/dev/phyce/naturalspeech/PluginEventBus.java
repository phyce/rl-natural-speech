package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.guice.PluginSingleton;
import net.runelite.client.eventbus.EventBus;

// Private event bus inside Plugin scope
// This private eventbus is entirely separate from RuneLites' EventBus
@PluginSingleton
public class PluginEventBus extends EventBus {}
