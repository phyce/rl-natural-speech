package net.runelite.client.plugins.naturalspeech.src.test.java.dev.phyce.naturalspeech;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.NaturalSpeechPlugin;

public class NaturalSpeechPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NaturalSpeechPlugin.class);
		RuneLite.main(args);
	}
}