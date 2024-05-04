package dev.phyce.naturalspeech.eventbus;

import dev.phyce.naturalspeech.singleton.PluginSingleton;
import net.runelite.client.eventbus.EventBus;

// Private event bus inside Plugin scope
// This private eventbus is entirely separate from RuneLites' EventBus
@PluginSingleton
public class PluginEventBus extends EventBus {}
