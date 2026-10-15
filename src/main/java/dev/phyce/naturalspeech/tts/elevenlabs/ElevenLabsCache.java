package dev.phyce.naturalspeech.tts.elevenlabs;

import com.google.common.hash.Hashing;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

@Slf4j
public class ElevenLabsCache {
	private static final String EXTENSION = ".pcm";

	private final Filepath directory;
	private final NaturalSpeechConfig config;

	public ElevenLabsCache(Filepath directory, NaturalSpeechConfig config) {
		this.directory = directory;
		this.config = config;
	}

	@CheckForNull
	public byte[] get(String key) {
		Filepath file = fileFor(key);
		if (!file.isFile()) return null;

		try (InputStream inputStream = file.openInputStream()) {
			return inputStream.readAllBytes();
		} catch (IOException e) {
			log.debug("Failed to read cached ElevenLabs audio {}", file, e);
			return null;
		}
	}

	public void put(String key, byte[] audio) {
		try {
			directory.createDirectories();
			fileFor(key).write(audio);
			trim();
		} catch (IOException e) {
			log.warn("Failed to cache ElevenLabs audio", e);
		}
	}

	public void clear() {
		try {
			for (CachedFile cachedFile : listFiles()) {
				cachedFile.getFile().deleteIfExists();
			}
		} catch (IOException e) {
			log.warn("Failed to clear the ElevenLabs cache", e);
		}
	}

	private void trim() throws IOException {
		long budget = config.elevenLabsCacheSize() * 1024L * 1024L;

		List<CachedFile> cachedFiles = listFiles();
		long total = cachedFiles.stream().mapToLong(CachedFile::getSize).sum();
		if (total <= budget) return;

		cachedFiles.sort(Comparator.comparingLong(CachedFile::getLastModified));
		for (CachedFile cachedFile : cachedFiles) {
			if (total <= budget) break;
			cachedFile.getFile().deleteIfExists();
			total -= cachedFile.getSize();
		}
	}

	private List<CachedFile> listFiles() throws IOException {
		if (!directory.isDirectory()) return new ArrayList<>();

		List<CachedFile> cachedFiles = new ArrayList<>();
		try (Stream<Filepath> files = directory.walk(1)) {
			for (Filepath file : files.filter(Filepath::isFile).collect(Collectors.toList())) {
				cachedFiles.add(new CachedFile(file, file.size(), file.getLastModifiedTime().toMillis()));
			}
		}
		return cachedFiles;
	}

	private Filepath fileFor(String key) {
		return directory.joinSegment(Hashing.sha256().hashString(key, StandardCharsets.UTF_8) + EXTENSION);
	}

	@Value
	private static class CachedFile {
		Filepath file;
		long size;
		long lastModified;
	}
}
