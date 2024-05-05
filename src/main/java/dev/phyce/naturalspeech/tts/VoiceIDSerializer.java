package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;

//gson
public class VoiceIDSerializer implements JsonDeserializer<VoiceID> {
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
		// migrate old member name. `int piperVoiceID` was migrated to `String id` in 3d3681d (version 1.3)
		// ensure users with old version can deserialize properly.
		JsonElement deprecatedPiperVoiceID = obj.get("piperVoiceID");
		if (deprecatedPiperVoiceID != null) {
			id = deprecatedPiperVoiceID.getAsString();
		} else {
			id = obj.get("id").getAsString();
		}

		return new VoiceID(modelName, id);

	}
}
