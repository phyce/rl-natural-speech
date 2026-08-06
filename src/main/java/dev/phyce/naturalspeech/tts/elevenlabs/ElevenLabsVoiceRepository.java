package dev.phyce.naturalspeech.tts.elevenlabs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.enums.Gender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The user's ElevenLabs voice library, fetched from {@code GET /v2/voices} with their API key.
 * <p>
 * ElevenLabs has no "pick a voice for me" mode — {@code voice_id} is a required path parameter on the
 * text-to-speech endpoint — so the plugin needs the real list before it can speak. Fetching it also
 * means cloned and custom voices work without being hardcoded, and {@code labels.gender} feeds the
 * gendered voice pools the rest of the plugin already uses.
 * <p>
 * The library is fetched when the plugin starts, when the API key changes, and when the user presses
 * Check key in the settings panel. There is no polling: a wrong key is a thing the user fixes, not a
 * thing worth re-asking the API about on a timer.
 * <p>
 * Listeners fire on the client thread so callers can touch the non-thread-safe voice maps safely.
 */
@Slf4j
@Singleton
public class ElevenLabsVoiceRepository {

	private static final int PAGE_SIZE = 100;
	/** Guard against a pathologically large library paging forever. */
	private static final int MAX_PAGES = 10;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final NaturalSpeechConfig config;
	private final ClientThread clientThread;

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean fetching = new AtomicBoolean(false);
	/** So a bad key does not spam the log once per message. */
	private final AtomicBoolean warned = new AtomicBoolean(false);

	private volatile List<ElevenLabsVoice> voices = Collections.emptyList();

	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "NaturalSpeech ElevenLabs voice fetch");
		thread.setDaemon(true);
		return thread;
	});

	@Inject
	public ElevenLabsVoiceRepository(
		OkHttpClient httpClient,
		Gson gson,
		NaturalSpeechConfig config,
		ClientThread clientThread) {
		this.httpClient = httpClient.newBuilder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
		this.config = config;
		this.clientThread = clientThread;
	}

	public List<ElevenLabsVoice> getVoices() {
		return voices;
	}

	public boolean isConfigured() {
		return !apiKey().isEmpty();
	}

	public boolean hasVoices() {
		return !voices.isEmpty();
	}

	/** Whether a fetch is currently in flight, so the UI can show a pending state. */
	public boolean isFetching() {
		return fetching.get();
	}

	private String apiKey() {
		String key = config.elevenLabsApiKey();
		return key == null ? "" : key.trim();
	}

	/** Call after the API key changes so the next failure is allowed to warn again. */
	public void resetWarning() {
		warned.set(false);
	}

	/** Fetches the voice library in the background, logging any problem. */
	public void refresh() {
		fetch(null);
	}

	/**
	 * Fetches the voice library and reports the outcome to {@code callback} on the client thread.
	 * Backs the Check key button, so it always hits the API rather than trusting what is cached.
	 */
	public void verify(Consumer<Result> callback) {
		fetch(callback);
	}

	private void fetch(@CheckForNull Consumer<Result> callback) {
		final String key = apiKey();

		if (key.isEmpty()) {
			Result result = new Result(false, 0, "No API key set.");
			warnOnce("ElevenLabs is selected for a message type but no API key is set. "
				+ "Add one under Natural Speech settings -> ElevenLabs.");
			publish(Collections.emptyList());
			report(callback, result);
			return;
		}

		if (!fetching.compareAndSet(false, true)) {
			log.debug("ElevenLabs voice fetch already in flight, skipping");
			report(callback, new Result(false, 0, "Already checking, hold on."));
			return;
		}

		worker.submit(() -> {
			try {
				Outcome outcome = fetchAll(key);
				List<ElevenLabsVoice> fetched = outcome.getVoices();

				if (outcome.getError() != null) {
					warnOnce("ElevenLabs voice list: " + outcome.getError());
					// A failed check should not wipe voices that are currently working
					if (!fetched.isEmpty()) publish(fetched);
					report(callback, new Result(false, fetched.size(), outcome.getError()));
					return;
				}

				publish(fetched);

				if (fetched.isEmpty()) {
					String message = "The key works, but the account has no voices.";
					warnOnce("ElevenLabs returned no voices for this API key.");
					report(callback, new Result(false, 0, message));
					return;
				}

				warned.set(false);
				log.info("Loaded {} ElevenLabs voice(s)", fetched.size());
				report(callback, new Result(true, fetched.size(),
					"Key works. " + fetched.size() + " voice(s) available."));
			}
			finally {
				fetching.set(false);
			}
		});
	}

	private void report(@CheckForNull Consumer<Result> callback, Result result) {
		if (callback == null) return;
		clientThread.invokeLater(() -> callback.accept(result));
	}

	/** Drops the cached voices, ex when the plugin stops or the key is removed. */
	public void clear() {
		publish(Collections.emptyList());
	}

	// Deliberately no shutdown(): this is a Guice singleton that outlives a plugin stop/start cycle,
	// so tearing down the executor or the listeners here would leave ElevenLabs dead until the client
	// is restarted. Stopping the plugin clears the voices via clear() instead.

	private void publish(List<ElevenLabsVoice> fetched) {
		List<ElevenLabsVoice> previous = voices;
		List<ElevenLabsVoice> next = Collections.unmodifiableList(new ArrayList<>(fetched));

		if (previous.isEmpty() && next.isEmpty()) return;

		voices = next;
		clientThread.invokeLater(() -> {
			for (Listener listener : listeners) {
				listener.onVoicesChanged(previous, next);
			}
		});
	}

	private Outcome fetchAll(String key) {
		List<ElevenLabsVoice> collected = new ArrayList<>();
		String pageToken = null;

		for (int page = 0; page < MAX_PAGES; page++) {
			HttpUrl.Builder url = HttpUrl.parse(ElevenLabs.API_BASE + "/v2/voices")
				.newBuilder()
				.addQueryParameter("page_size", Integer.toString(PAGE_SIZE))
				.addQueryParameter("include_total_count", "false");

			if (pageToken != null) {
				url.addQueryParameter("next_page_token", pageToken);
			}

			Request request = new Request.Builder()
				.url(url.build())
				.addHeader("xi-api-key", key)
				.get()
				.build();

			try (Response response = httpClient.newCall(request).execute()) {
				ResponseBody body = response.body();

				if (!response.isSuccessful() || body == null) {
					return new Outcome(collected, describe(response.code()));
				}

				JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
				collected.addAll(parseVoices(json));

				if (!optionalBoolean(json, "has_more")) return new Outcome(collected, null);

				JsonElement next = json.get("next_page_token");
				if (next == null || next.isJsonNull()) return new Outcome(collected, null);
				pageToken = next.getAsString();
			}
			catch (IOException e) {
				return new Outcome(collected, "Could not reach the ElevenLabs API: " + e.getMessage());
			}
			catch (RuntimeException e) { // includes JsonParseException on a malformed body
				return new Outcome(collected, "Could not read the response: " + e.getMessage());
			}
		}

		log.debug("Stopped paging the ElevenLabs voice list at {} pages", MAX_PAGES);
		return new Outcome(collected, null);
	}

	/** Turns an HTTP status into something a user can act on. */
	private static String describe(int code) {
		switch (code) {
			case 401:
			case 403:
				return "The API key was rejected (HTTP " + code + "). Check it was copied in full.";
			case 429:
				return "Rate limited by ElevenLabs (HTTP 429). Try again shortly.";
			default:
				return "ElevenLabs returned HTTP " + code + ".";
		}
	}

	private static List<ElevenLabsVoice> parseVoices(JsonObject json) {
		List<ElevenLabsVoice> parsed = new ArrayList<>();

		JsonElement voicesElement = json.get("voices");
		if (voicesElement == null || !voicesElement.isJsonArray()) return parsed;

		JsonArray array = voicesElement.getAsJsonArray();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) continue;
			JsonObject voice = element.getAsJsonObject();

			String id = optionalString(voice, "voice_id");
			if (id == null || id.isEmpty()) continue;

			String name = optionalString(voice, "name");
			if (name == null || name.isEmpty()) name = id;

			parsed.add(new ElevenLabsVoice(id, name, parseGender(voice)));
		}

		return parsed;
	}

	private static Gender parseGender(JsonObject voice) {
		JsonElement labels = voice.get("labels");
		if (labels == null || !labels.isJsonObject()) return Gender.OTHER;

		String gender = optionalString(labels.getAsJsonObject(), "gender");
		if (gender == null) return Gender.OTHER;

		switch (gender.toLowerCase()) {
			case "male":
				return Gender.MALE;
			case "female":
				return Gender.FEMALE;
			default:
				return Gender.OTHER;
		}
	}

	private static String optionalString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
		return element.getAsString();
	}

	private static boolean optionalBoolean(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return false;
		return element.getAsBoolean();
	}

	private void warnOnce(String message) {
		if (warned.compareAndSet(false, true)) {
			log.warn(message);
		}
		else {
			log.debug(message);
		}
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	/** Called on the client thread when the loaded voice list changes. */
	public interface Listener {
		void onVoicesChanged(List<ElevenLabsVoice> removed, List<ElevenLabsVoice> added);
	}

	/** The user-facing result of a key check. */
	@Value
	public static class Result {
		boolean success;
		int voiceCount;
		String message;
	}

	/** Internal fetch result: whatever was collected, plus why it stopped early. */
	@Value
	private static class Outcome {
		List<ElevenLabsVoice> voices;
		@CheckForNull
		String error;
	}
}
