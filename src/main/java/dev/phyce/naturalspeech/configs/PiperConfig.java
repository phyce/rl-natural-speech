package dev.phyce.naturalspeech.configs;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@PluginSingleton
public class PiperConfig {
	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";
	private static final boolean DEFAULT_ENABLED = false;
	private static final int DEFAULT_PROCESS_COUNT = 1;

	private final ConfigManager configManager;

	private final Map<String, ModelConfig> configs = Collections.synchronizedMap(new HashMap<>());

	@Inject
	public PiperConfig(ConfigManager configManager) {
		this.configManager = configManager;
		load();
	}

	public void setEnabled(String modelName, boolean enabled) {
		ModelConfig config = configs.getOrDefault(modelName, new ModelConfig(modelName));
		config.enabled = enabled;
		configs.put(modelName, config);
	}

	public void setProcessCount(String modelName, int processCount) {
		ModelConfig config = configs.getOrDefault(modelName, new ModelConfig(modelName));
		config.processCount = processCount;
		configs.put(modelName, config);
	}

	public void unset(String modelName) {
		configs.remove(modelName);
	}

	public boolean isEnabled(String modelName) {
		ModelConfig config = configs.get(modelName);
		if (config == null) {
			return DEFAULT_ENABLED;
		}
		return config.isEnabled();
	}

	public int getProcessCount(String modelName) {
		ModelConfig config = configs.get(modelName);
		return config == null ? DEFAULT_PROCESS_COUNT : config.getProcessCount();
	}

	public void save() {
		String json = RuneLiteAPI.GSON.toJson(new ConfigJson(configs.values()));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG, json);
	}

	private void load() {
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG);
		if (json != null) {
			ConfigJson configJson = RuneLiteAPI.GSON.fromJson(json, ConfigJson.class);
			configJson.modelConfigs.forEach(c -> configs.put(c.getModelName(), c));
		}
	}

	@JsonAdapter(ConfigJson.JSONAdaptor.class)
	private static class ConfigJson {
		final List<ModelConfig> modelConfigs = new ArrayList<>();

		ConfigJson(Collection<ModelConfig> modelConfigs) {
			this.modelConfigs.addAll(modelConfigs);
		}

		ConfigJson() {}

		private static class JSONAdaptor implements JsonDeserializer<ConfigJson> {

			@Override
			public ConfigJson deserialize(
				JsonElement jsonElement,
				Type type,
				JsonDeserializationContext context
			) throws JsonParseException {
				JsonObject jsonObj = jsonElement.getAsJsonObject();
				if (jsonObj == null) return new ConfigJson();

				JsonArray modelConfigsArray = jsonObj.getAsJsonArray("modelConfigs");
				if (modelConfigsArray == null) return new ConfigJson();

				Type listType = new TypeToken<List<ModelConfig>>() {}.getType();
				List<ModelConfig> modelConfigs = context.deserialize(modelConfigsArray, listType);

				return new ConfigJson(modelConfigs);
			}
		}
	}

	@Data
	@AllArgsConstructor
	private static class ModelConfig {
		private String modelName;
		private boolean enabled;
		private int processCount;

		private ModelConfig(String modelName) {
			this.modelName = modelName;
			this.enabled = DEFAULT_ENABLED;
			this.processCount = DEFAULT_PROCESS_COUNT;
		}
	}
}
