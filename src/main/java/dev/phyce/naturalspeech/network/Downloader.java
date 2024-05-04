package dev.phyce.naturalspeech.network;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.nio.file.Path;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@PluginSingleton
public class Downloader {
	@Inject
	private OkHttpClient httpClient;

	public DownloadTask create(HttpUrl url, Path destination) {
		return new DownloadTask(httpClient, destination, url, true);
	}
}