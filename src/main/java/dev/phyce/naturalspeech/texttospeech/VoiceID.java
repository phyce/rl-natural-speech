package dev.phyce.naturalspeech.texttospeech;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import java.lang.reflect.Type;
import javax.annotation.CheckForNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * VoiceID represents the model and id for a speak request. <br>
 * Similar to URLs for http, representing server and document.<br>
 */
@Data
@JsonAdapter(VoiceID.VoiceIDSerializer.class)
public class VoiceID {

	static final int VERSION = 1;

	public String modelName;
	public String id;

	public VoiceID(String modelName, String id) {
		this.modelName = modelName;
		this.id = id;
	}

	@CheckForNull
	public Integer getIntId() {
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Returns the ModelAndVoice in the format of "modelShortName:VoiceID",
	 * ex libritts:360
	 *
	 * @return null if format is invalid, ModelAndVoice otherwise.
	 * Does not verify Model and Voice's actual existence.
	 */
	public static VoiceID fromIDString(String idString) {
		String[] split = idString.split(":");

		// incorrect format
		if (split.length != 2) return null;

		// verify model short name
		if (split[0].isEmpty() || split[0].isBlank()) return null;

		// verify id
		if (split[1].isEmpty() || split[1].isBlank()) return null;

		return new VoiceID(split[0], split[1]);
	}

	public String toVoiceIDString() {
		return String.format("%s:%s", modelName, id);
	}

	public String toString() {
		return toVoiceIDString();
	}

	//gson
	@Slf4j
	static class VoiceIDSerializer implements JsonSerializer<VoiceID>, JsonDeserializer<VoiceID> {
		@Override
		public VoiceID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
			JsonParseException {
			// find field piperVoiceID
			if (!json.isJsonObject()) {
				throw new JsonParseException("Expected JsonObject, got " + json);
			}

			JsonObject obj = json.getAsJsonObject();
			JsonElement versionObj = obj.get("version");
			final int version = versionObj == null ? 0 : versionObj.getAsInt();

			String modelName = obj.get("modelName").getAsString();

			String id;

			// migrate old ID, `int piperVoiceID` was migrated to `String id` in 3d3681d (version 1.3)
			// ensure users VoiceID of previous versions can deserialize properly.
			if (version == 0) {
				JsonElement piperIdJson = obj.get("piperVoiceID");
				if (piperIdJson == null) {
					log.error("Missing piperVoiceID in VoiceID version 0: {}", json);
					return null;
				}
				id = piperIdJson.getAsString();
			}
			else if (version == 1) {
				JsonElement idJson = obj.get("id");
				if (idJson == null) {
					log.error("Missing id in VoiceID version 1: {}", json);
					return null;
				}
				id = idJson.getAsString();
			}
			else {
				log.error("VoiceID Unknown version: {}", json);
				return null;
			}

			return new VoiceID(modelName, id);

		}

		@Override
		public JsonElement serialize(VoiceID src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject json = new JsonObject();
			json.add("modelName", context.serialize(src.modelName));
			json.add("id", context.serialize(src.id));
			json.add("version", context.serialize(VoiceID.VERSION));
			return json;
		}
	}
}
