package dev.phyce.naturalspeech.texttospeech;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import java.lang.reflect.Type;
import javax.annotation.CheckForNull;
import lombok.Data;

/**
 * VoiceID represents the model and id for a speak request. <br>
 * Similar to URLs for http, representing server and document.<br>
 */
@Data
@JsonAdapter(VoiceID.VoiceIDSerializer.class)
public class VoiceID {
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
	static class VoiceIDSerializer implements JsonDeserializer<VoiceID> {
		@Override
		public VoiceID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
			JsonParseException {
			// find field piperVoiceID
			if (!json.isJsonObject()) {
				throw new JsonParseException("Expected JsonObject, got " + json);
			}

			JsonObject obj = json.getAsJsonObject();

			String modelName = obj.get("modelName").getAsString();

			String id;

			// migrate old ID, `int piperVoiceID` was migrated to `String id` in 3d3681d (version 1.3)
			// ensure users VoiceID of previous versions can deserialize properly.
			JsonElement deprecatedPiperVoiceID = obj.get("piperVoiceID");
			if (deprecatedPiperVoiceID != null) {
				id = deprecatedPiperVoiceID.getAsString();
			} else {
				id = obj.get("id").getAsString();
			}

			return new VoiceID(modelName, id);

		}
	}
}
