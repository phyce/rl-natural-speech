package dev.phyce.naturalspeech.tts.elevenlabs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * On-disk cache of synthesized audio keyed by model, voice and text. Every hit is one fewer billed
 * ElevenLabs request, which matters a lot for repeated dialogue and system messages.
 * <p>
 * Stored under {@code .runelite/natural-speech/elevenlabs-cache}, bounded by a byte budget with
 * least-recently-used eviction.
 */
@Slf4j
public class ElevenLabsCache {

	private static final String CACHE_PATH = "natural-speech" + File.separator + "elevenlabs-cache";

	private final File dir;
	private final LongSupplier maxBytes;

	public ElevenLabsCache(LongSupplier maxBytes) {
		this.dir = new File(RuneLite.RUNELITE_DIR, CACHE_PATH);
		this.maxBytes = maxBytes;
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
	}

	/** The cached audio for the key, or null on a miss. Touches the file on a hit, for LRU. */
	public byte[] get(String key) {
		File file = fileFor(key);
		if (!file.exists()) return null;

		try {
			byte[] data = Files.readAllBytes(file.toPath());
			//noinspection ResultOfMethodCallIgnored
			file.setLastModified(System.currentTimeMillis());
			return data;
		}
		catch (IOException e) {
			return null;
		}
	}

	/** Stores audio for the key, then evicts the oldest files if over budget. */
	public void put(String key, byte[] data) {
		try {
			Files.write(fileFor(key).toPath(), data);
			evict();
		}
		catch (IOException e) {
			log.debug("Failed writing ElevenLabs cache entry", e);
		}
	}

	/** Deletes every cached clip. Returns how many were removed. */
	public static long clearAll() {
		File root = new File(RuneLite.RUNELITE_DIR, CACHE_PATH);
		File[] files = root.listFiles(File::isFile);
		if (files == null) return 0;

		long removed = 0;
		for (File file : files) {
			if (file.delete()) removed++;
		}
		return removed;
	}

	private File fileFor(String key) {
		return new File(dir, hash(key));
	}

	private void evict() {
		File[] files = dir.listFiles(File::isFile);
		if (files == null) return;

		long total = 0;
		for (File file : files) {
			total += file.length();
		}

		long max = maxBytes.getAsLong();
		if (total <= max) return;

		Arrays.sort(files, Comparator.comparingLong(File::lastModified)); // oldest first
		for (File file : files) {
			if (total <= max) break;

			long length = file.length();
			if (file.delete()) total -= length;
		}
	}

	private static String hash(String key) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));

			StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				builder.append(Character.forDigit((b >> 4) & 0xF, 16));
				builder.append(Character.forDigit(b & 0xF, 16));
			}
			return builder.toString();
		}
		catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(key.hashCode());
		}
	}
}
