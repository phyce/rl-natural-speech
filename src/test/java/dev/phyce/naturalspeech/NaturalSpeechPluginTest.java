package dev.phyce.naturalspeech;
//package net.runelite.client.plugins.naturalspeech.src.test.java.dev.phyce.naturalspeech;

import com.google.common.annotations.VisibleForTesting;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.downloader.DownloadTask;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.externalplugins.ExternalPluginManager;
import okhttp3.*;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public class NaturalSpeechPluginTest
{
	public static void main(String[] args) throws Exception
	{


		ExternalPluginManager.loadBuiltin(NaturalSpeechPlugin.class);
		RuneLite.main(args);
	}
}