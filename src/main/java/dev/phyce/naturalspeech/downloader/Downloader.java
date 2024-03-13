package dev.phyce.naturalspeech.downloader;

import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@Singleton
public class Downloader {
	@Inject
	private OkHttpClient httpClient;

	public DownloadTask create(HttpUrl url, Path destination) {
		return new DownloadTask(httpClient, destination, url, true);
	}
}