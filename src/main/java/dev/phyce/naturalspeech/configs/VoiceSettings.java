package dev.phyce.naturalspeech.configs;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.JsonArray;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;
import org.checkerframework.checker.nullness.qual.NonNull;

@Slf4j
@JsonAdapter(VoiceSettings.JSONAdaptor.class)
public class VoiceSettings {

	static final int VERSION = 1;

	private final Map<EntityID, VoiceID> settings = Collections.synchronizedMap(new HashMap<>());

	private VoiceSettings(Map<EntityID, VoiceID> settingsMap) {
		this.settings.putAll(settingsMap);
	}

	public static Map<EntityID, VoiceID> fromJSON(String json) {
		try {
			if (isOldConfig(json)) json = convertOldConfig(json);

			VoiceSettings settings = RuneLiteAPI.GSON.fromJson(json, VoiceSettings.class);

			return settings.settings;
		} catch (JsonSyntaxException e) {
			return Collections.synchronizedMap(new HashMap<>());
		}
	}

	private static boolean isOldConfig(String json) {
		try {
			JsonObject jsonObject = RuneLiteAPI.GSON.fromJson(json, JsonObject.class);
			return !jsonObject.has("version") && jsonObject.has("playerNameVoiceConfigData");
		} catch (Exception e) {
			log.error("Error parsing JSON in isOldConfig: {}", e.getMessage());
			return false;
		}
	}

	private static String convertOldConfig(String oldJson) {
		try {
			JsonObject oldStructure = RuneLiteAPI.GSON.fromJson(oldJson, JsonObject.class);
			JsonObject result = new JsonObject();
			JsonArray settings = new JsonArray();

			BiConsumer<JsonObject, Consumer<JsonObject>> processVoiceEntry = (entry, entityConfigurer) -> {
				JsonArray voiceIDs = entry.getAsJsonArray("voiceIDs");
				if (voiceIDs != null && voiceIDs.size() > 0) {
					JsonObject voice = voiceIDs.get(0).getAsJsonObject();
					if (voice.has("piperVoiceID") && !voice.get("piperVoiceID").isJsonNull()) {
						JsonObject convertedVoice = new JsonObject();

						JsonObject entityID = new JsonObject();
						entityID.addProperty("version", 1);
						entityConfigurer.accept(entityID);
						convertedVoice.add("entityID", entityID);

						JsonObject voiceID = new JsonObject();
						voiceID.addProperty("modelName", voice.get("modelName").getAsString());
						voiceID.addProperty("id", String.valueOf(voice.get("piperVoiceID").getAsInt()));
						voiceID.addProperty("version", 1);
						convertedVoice.add("voiceID", voiceID);

						settings.add(convertedVoice);
					} else {
						log.error("Invalid voice id for entry: {}", entry);
					}
				}
			};

			if (oldStructure.has("playerNameVoiceConfigData")) {
				JsonArray playersConfig = oldStructure.getAsJsonArray("playerNameVoiceConfigData");
				for (JsonElement playerConfig : playersConfig) {
					JsonObject playerEntry = playerConfig.getAsJsonObject();
					processVoiceEntry.accept(playerEntry, entity ->
						entity.addProperty("name", playerEntry.get("playerName").getAsString())
					);
				}
			}

			if (oldStructure.has("npcIDVoiceConfigData")) {
				JsonArray npcsConfig = oldStructure.getAsJsonArray("npcIDVoiceConfigData");
				for (JsonElement npcConfig : npcsConfig) {
					JsonObject npcEntry = npcConfig.getAsJsonObject();
					processVoiceEntry.accept(npcEntry, entity ->
						entity.addProperty("id", npcEntry.get("npcId").getAsInt())
					);
				}
			}

			result.add("settings", settings);
			result.addProperty("version", 1);

			return RuneLiteAPI.GSON.toJson(result);
		} catch (Exception e) {
			log.error("Error converting old config: {}", e.getMessage());
			// Fallback: return the original JSON if conversion fails
			return oldJson;
		}
	}

	public static String toJSON(Map<EntityID, VoiceID> settings) {
		return RuneLiteAPI.GSON.toJson(new VoiceSettings(settings));
	}

	@Data
	@AllArgsConstructor
	private static class Setting {
		@NonNull
		EntityID entityID;
		@NonNull
		VoiceID voiceID;
	}

	public static class JSONAdaptor implements JsonDeserializer<VoiceSettings>, JsonSerializer<VoiceSettings> {

		@Override
		public JsonElement serialize(
			VoiceSettings voiceSettings,
			Type type,
			JsonSerializationContext context
		) {
			List<Setting> settings = new ArrayList<>();
			voiceSettings.settings.forEach((k, v) -> settings.add(new Setting(k, v)));

			JsonObject jsonObject = new JsonObject();
			jsonObject.add("settings", context.serialize(settings));
			jsonObject.add("version", context.serialize(VoiceSettings.VERSION));

			return jsonObject;
		}

		@Override
		public VoiceSettings deserialize(
			JsonElement jsonElement,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			// read deprecated members and convert to SID
			JsonObject json = jsonElement.getAsJsonObject();

			final JsonElement versionElement = json.get("version");
			final int version = versionElement == null ? 0 : versionElement.getAsInt();

			List<Setting> settings;

			if (version == 0) settings = deserializeVersion0(context, json);
			else if (version == 1) settings = deserializeVersion1(context, json);
			else throw new JsonSyntaxException("Unknown VoiceSettings Schema Version: " + version);

			Map<EntityID, VoiceID> settingMap = new HashMap<>();
			settings.forEach(setting -> settingMap.put(setting.entityID, setting.voiceID));

			return new VoiceSettings(settingMap);
		}

		private static List<Setting> deserializeVersion1(JsonDeserializationContext context, JsonObject json) {
			List<Setting> settings;
			// version 1.3 settings
			Type voiceSettingDataType = new TypeToken<ArrayList<Setting>>() {}.getType();
			// settings can be null pre-version 1.3
			settings = context.deserialize(json.get("settings"), voiceSettingDataType);
			return settings;
		}

		private static List<Setting> deserializeVersion0(
			JsonDeserializationContext context,
			JsonObject json
		) {
			List<Setting> settings = new ArrayList<>();
			Type userNameVoiceDataType = new TypeToken<ArrayList<UsernameVoice>>() {}.getType();

			List<UsernameVoice> usernameVoiceData =
				context.deserialize(json.get("playerNameVoiceConfigData"), userNameVoiceDataType);

			if (usernameVoiceData == null) {
				log.error("Failed to deserialize usernameVoiceData");
				return null;
			}

			usernameVoiceData.forEach(
				usernameVoice -> {
					// only care about the first one, no more fallback voices
					if (usernameVoice.voiceIDs.get(0) == null) {
						// this can happen if an invalid voice id json was deserialized
						// usually development or manual editing
						log.error("Invalid voice id for username: {}", usernameVoice);
						return;
					}
					EntityID entityID = EntityID.name(usernameVoice.playerName);
					settings.add(new Setting(entityID, usernameVoice.voiceIDs.get(0)));
				}
			);

			Type npcIDVoiceDataType = new TypeToken<ArrayList<NPCVoice>>() {}.getType();

			List<NPCVoice> npcIDVoiceData =
				context.deserialize(json.get("npcIDVoiceConfigData").getAsJsonArray(), npcIDVoiceDataType);

			if (npcIDVoiceData == null) {
				log.error("Failed to deserialize npcIDVoiceData");
				return null;
			}

			npcIDVoiceData.forEach(
				npcVoice -> {
					// only care about the first one, no more fallback voices
					if (npcVoice.voiceIDs.get(0) == null) {
						// this can happen if an invalid voice id json was deserialized
						// usually development or manual editing
						log.error("Invalid voice id for npc: {}", npcVoice);
						return;
					}
					EntityID entityID = EntityID.id(npcVoice.npcId);
					settings.add(new Setting(entityID, npcVoice.voiceIDs.get(0)));
				}
			);
			return settings;
		}
	}

	// Used for JSON Serialization
	@Data
	@Deprecated(since="1.3 Using VoiceSetting now.")
	public static class UsernameVoice {

		List<VoiceID> voiceIDs = new ArrayList<>();

		String playerName;

		public UsernameVoice(String playerName) {
			this.playerName = playerName;
		}

	}

	// Used for JSON Serialization
	@Data
	@Deprecated(since="1.3 Using VoiceSetting now.")
	public static class NPCVoice {

		// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
		List<VoiceID> voiceIDs = new ArrayList<>();

		int npcId;

		public NPCVoice(int npcId) {
			this.npcId = npcId;
		}
	}

}
