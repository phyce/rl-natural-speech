package dev.phyce.naturalspeech.spamdetection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MessageDuplicateSuppressor {

	private static final long WINDOW_MS = 5000;

	private final NaturalSpeechConfig config;
	private final Map<String, Entry> lastBySource = new HashMap<>();

	@Inject
	public MessageDuplicateSuppressor(NaturalSpeechConfig config) {
		this.config = config;
	}

	public boolean shouldSuppress(String sourceKey, String text) {
		if (!config.messageDuplicateSuppressorEnabled()) return false;
		if (sourceKey == null || text == null || text.isEmpty()) return false;

		long now = System.currentTimeMillis();
		Entry last = lastBySource.get(sourceKey);

		if (last != null && last.text.equals(text) && (now - last.timestampMs) < WINDOW_MS) {
			log.trace("Suppressing repeated message from {}: {}", sourceKey, text);
			return true;
		}

		lastBySource.put(sourceKey, new Entry(text, now));
		return false;
	}

	private static final class Entry {
		final String text;
		final long timestampMs;

		Entry(String text, long timestampMs) {
			this.text = text;
			this.timestampMs = timestampMs;
		}
	}
}
