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
	@VisibleForTesting
	static OkHttpClient buildHttpClient(boolean insecureSkipTlsVerification)
	{
		OkHttpClient.Builder builder = new OkHttpClient.Builder()
				.pingInterval(30, TimeUnit.SECONDS)
				.addInterceptor(chain ->
				{
					Request request = chain.request();
					if (request.header("User-Agent") != null)
					{
						return chain.proceed(request);
					}

					Request userAgentRequest = request
							.newBuilder()
							.header("User-Agent", "Testing")
							.build();
					return chain.proceed(userAgentRequest);
				})
				// Setup cache
				.addNetworkInterceptor(chain ->
				{
					// This has to be a network interceptor so it gets hit before the cache tries to store stuff
					Response res = chain.proceed(chain.request());
					if (res.code() >= 400 && "GET".equals(res.request().method()))
					{
						// if the request 404'd we don't want to cache it because its probably temporary
						res = res.newBuilder()
								.header("Cache-Control", "no-store")
								.build();
					}
					return res;
				});


		return builder.build();
	}


	public static void main(String[] args) throws Exception
	{

		ExternalPluginManager.loadBuiltin(NaturalSpeechPlugin.class);
		RuneLite.main(args);
	}
}