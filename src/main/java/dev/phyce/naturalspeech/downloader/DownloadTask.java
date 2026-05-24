package dev.phyce.naturalspeech.downloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class DownloadTask implements Supplier<File> {
	@Getter
	private final Path destination;
	private final HttpUrl url;
	private final boolean overwrite;
	private final OkHttpClient httpClient;
	@Getter
	private volatile boolean downloading = false;
	@Getter
	private volatile boolean finished = false;
	@Getter
	private volatile float progress = 0;
	@Getter
	private volatile int error = 0;
	@Getter
	private volatile String errorMessage = null;

	// TODO(Louis) Add a callback that updates the download progress
	public DownloadTask(OkHttpClient httpClient, Path destination, HttpUrl url, boolean overwrite) {
		this.destination = destination;
		this.url = url;
		this.overwrite = overwrite;
		this.httpClient = httpClient;
	}

	public void download(ProgressListener progressListener) {

		if (!destination.toFile().exists() || overwrite) {
			downloading = true;
			Request req = new Request.Builder().url(url).build();
			try (Response response = httpClient.newCall(req).execute()) {
				if (!response.isSuccessful()) {
					error = response.code();
					throw new IOException("Server returned HTTP " + response.code() + " " + response.message());
				}

				int length = (int) Objects.requireNonNull(response.body()).contentLength();
				if (length < 0) {
					length = 3 * 1024 * 1024;
				}
				final int flength = length;

				// try-with-resources to ensure the input stream is closed
				try (InputStream in = response.body().byteStream();
					 OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE,
						 StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
					byte[] buffer = new byte[1024];
					int bytesRead;
					int totalRead = 0;
					while ((bytesRead = in.read(buffer)) != -1) {
						// check if the connection was closed
						if (bytesRead == 0) throw new IOException("Connection closed");

						out.write(buffer, 0, bytesRead);
						totalRead += bytesRead;
						progress = (float) totalRead / flength * 100;
						if (progressListener != null) progressListener.onProgress(progress);
					}
					progress = 100;
					finished = true;
					downloading = false;
				}
			} catch (IOException e) {
				errorMessage = describe(e);
				log.error("Error downloading {}: {}", url, errorMessage);
				progress = 0; // Reset progress if download fails
				if (error == 0) error = 1;
				downloading = false;
			}
		}
	}

	private String describe(IOException e) {
		if (e instanceof AccessDeniedException) {
			return "Permission denied writing to " + destination;
		}
		if (e instanceof NoSuchFileException) {
			return "Folder does not exist: " + destination.getParent();
		}
		if (e instanceof FileSystemException) {
			FileSystemException fse = (FileSystemException) e;
			String reason = fse.getReason() != null ? fse.getReason() : e.getClass().getSimpleName();
			return "Cannot write to " + destination + " (" + reason + ")";
		}
		if (e instanceof UnknownHostException) {
			return "Cannot resolve host: " + url.host() + " (check your internet connection)";
		}
		if (e instanceof ConnectException) {
			return "Cannot connect to " + url.host() + ": " + e.getMessage();
		}
		if (e instanceof SSLException) {
			return "Secure connection to " + url.host() + " failed: " + e.getMessage();
		}
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}

	@Override
	public synchronized File get() {
		if (!finished && !downloading && error == 0) download(null);
		if (error > 0) return null;

		return destination.toFile();
	}

	public interface ProgressListener {
		void onProgress(float progress);
	}
}

