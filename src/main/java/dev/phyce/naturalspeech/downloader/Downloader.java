package dev.phyce.naturalspeech.downloader;

import dev.phyce.naturalspeech.guice.PluginSingleton;
import java.nio.file.Path;
import javax.inject.Inject;
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