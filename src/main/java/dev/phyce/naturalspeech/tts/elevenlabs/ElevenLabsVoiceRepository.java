package dev.phyce.naturalspeech.tts.elevenlabs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.Gender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class ElevenLabsVoiceRepository {
	private static final HttpUrl VOICES_URL = HttpUrl.get("https://api.elevenlabs.io/v2/voices");
	private static final int PAGE_SIZE = 100;
	private static final int MAX_PAGES = 10;
	private static final String MODEL_ENABLED_KEY_PREFIX = "elevenLabsModelEnabled_";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final NaturalSpeechConfig config;
	private final ConfigManager configManager;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	@Getter
	private volatile List<ElevenLabsVoice> voices = List.of();
	@Getter
	private volatile String error;
	@Getter
	private volatile boolean loading = false;
	private int fetchGeneration = 0;

	@Inject
	public ElevenLabsVoiceRepository(OkHttpClient httpClient, Gson gson, NaturalSpeechConfig config,
									 ConfigManager configManager) {
		this.httpClient = httpClient;
		this.gson = gson;
		this.config = config;
		this.configManager = configManager;
	}

	public boolean hasApiKey() {
		return !config.elevenLabsApiKey().isBlank();
	}

	public boolean hasVoices() {
		return !voices.isEmpty();
	}

	public boolean isModelEnabled(ElevenLabsModel model) {
		Boolean enabled = configManager.getConfiguration(CONFIG_GROUP, MODEL_ENABLED_KEY_PREFIX + model.getId(), Boolean.class);
		return enabled == null ? model == ElevenLabsModel.FLASH_V2_5 : enabled;
	}

	public void setModelEnabled(ElevenLabsModel model, boolean enabled) {
		configManager.setConfiguration(CONFIG_GROUP, MODEL_ENABLED_KEY_PREFIX + model.getId(), enabled);
		notifyListeners();
	}

	public List<ElevenLabsModel> getEnabledModels() {
		return Arrays.stream(ElevenLabsModel.values()).filter(this::isModelEnabled).collect(Collectors.toList());
	}

	public synchronized void refresh() {
		int generation = ++fetchGeneration;
		String apiKey = config.elevenLabsApiKey().trim();

		if (apiKey.isEmpty()) {
			finish(generation, List.of(), null);
			return;
		}

		loading = true;
		notifyListeners();
		fetchPage(generation, apiKey, null, new ArrayList<>(), 1);
	}

	private void fetchPage(int generation, String apiKey, @CheckForNull String pageToken,
						   List<ElevenLabsVoice> collected, int page) {
		HttpUrl.Builder url = VOICES_URL.newBuilder()
			.addQueryParameter("page_size", String.valueOf(PAGE_SIZE))
			.addQueryParameter("include_total_count", "false");
		if (pageToken != null) url.addQueryParameter("next_page_token", pageToken);

		Request request = new Request.Builder()
			.url(url.build())
			.header("xi-api-key", apiKey)
			.build();

		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.warn("Failed to load ElevenLabs voices", e);
				finish(generation, List.of(), "Could not reach ElevenLabs");
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (response) {
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body == null) {
						log.warn("Failed to load ElevenLabs voices, HTTP {}", response.code());
						finish(generation, List.of(), describe(response.code()));
						return;
					}

					JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
					if (json == null) throw new JsonParseException("Empty response");
					collected.addAll(parseVoices(json));

					String nextPageToken = getString(json, "next_page_token");
					if (getBoolean(json, "has_more") && nextPageToken != null && page < MAX_PAGES) {
						fetchPage(generation, apiKey, nextPageToken, collected, page + 1);
					}
					else {
						finish(generation, collected, null);
					}
				} catch (JsonParseException e) {
					log.warn("Failed to read ElevenLabs voices", e);
					finish(generation, List.of(), "Unexpected response from ElevenLabs");
				}
			}
		});
	}

	private synchronized void finish(int generation, List<ElevenLabsVoice> fetched, @CheckForNull String fetchError) {
		if (generation != fetchGeneration) return;

		voices = List.copyOf(fetched);
		error = fetchError;
		loading = false;
		log.debug("Loaded {} ElevenLabs voices", fetched.size());
		notifyListeners();
	}

	private static List<ElevenLabsVoice> parseVoices(JsonObject json) {
		List<ElevenLabsVoice> parsed = new ArrayList<>();
		JsonElement voices = json.get("voices");
		if (voices == null || !voices.isJsonArray()) return parsed;

		for (JsonElement element : voices.getAsJsonArray()) {
			if (!element.isJsonObject()) continue;
			JsonObject voice = element.getAsJsonObject();

			String id = getString(voice, "voice_id");
			if (id == null || id.isBlank()) continue;

			String name = getString(voice, "name");
			parsed.add(new ElevenLabsVoice(id, name == null ? id : name, parseGender(voice.get("labels"))));
		}
		return parsed;
	}

	private static Gender parseGender(@CheckForNull JsonElement labels) {
		if (labels == null || !labels.isJsonObject()) return Gender.OTHER;

		String gender = getString(labels.getAsJsonObject(), "gender");
		if ("male".equalsIgnoreCase(gender)) return Gender.MALE;
		if ("female".equalsIgnoreCase(gender)) return Gender.FEMALE;
		return Gender.OTHER;
	}

	@CheckForNull
	private static String getString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	private static boolean getBoolean(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsBoolean();
	}

	private static String describe(int code) {
		switch (code) {
			case 401:
				return "API key was rejected";
			case 429:
				return "Too many requests, try again later";
			default:
				return "ElevenLabs returned HTTP " + code;
		}
	}

	private void notifyListeners() {
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	public void removeListener(Runnable listener) {
		listeners.remove(listener);
	}
}
