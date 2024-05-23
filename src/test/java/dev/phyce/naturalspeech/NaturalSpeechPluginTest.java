package dev.phyce.naturalspeech;
//package net.runelite.client.plugins.naturalspeech.src.test.java.dev.phyce.naturalspeech;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NaturalSpeechPluginTest {
	public static void main(String[] args) throws Exception {
		//noinspection unchecked
		ExternalPluginManager.loadBuiltin(NaturalSpeechPlugin.class);
		RuneLite.main(args);
	}

}