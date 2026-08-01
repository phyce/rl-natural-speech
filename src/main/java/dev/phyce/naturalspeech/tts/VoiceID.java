package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import java.lang.reflect.Type;
import lombok.Data;

@Data
@JsonAdapter(VoiceID.Adapter.class)
public class VoiceID {

	public String modelName;

	public String id;

	public VoiceID() {

	}

	public VoiceID(String modelName, String id) {
		this.modelName = modelName;
		this.id = id;
	}

	public VoiceID(String modelName, int piperVoiceID) {
		this(modelName, Integer.toString(piperVoiceID));
	}

	/**
	 * @return the piper speaker index, or -1 when this voice is not numeric (in which case it does
	 * not belong to piper). -1 is what piper itself treats as "no speaker id".
	 */
	public int getPiperVoiceID() {
		try {
			return Integer.parseUnsignedInt(id);
		}
		catch (NumberFormatException ignored) {
			return -1;
		}
	}

	/**
	 * Parses "modelName:voiceID", ex libritts:360 or microsoft:Microsoft Hazel Desktop.
	 * <p>
	 * Splits on the first colon only, so voice names containing one survive.
	 *
	 * @return null if the format is invalid. Does not verify the model or voice actually exists.
	 */
	public static VoiceID fromIDString(String idString) {
		if (idString == null) return null;

		int separator = idString.indexOf(':');
		if (separator < 1) return null;

		String modelName = idString.substring(0, separator);
		String id = idString.substring(separator + 1);

		if (modelName.trim().isEmpty() || id.trim().isEmpty()) return null;

		return new VoiceID(modelName, id);
	}

	public String toVoiceIDString() {
		return modelName + ":" + id;
	}

	public String toString() {
		return toVoiceIDString();
	}

	/**
	 * Voice ids used to be the numeric {@code piperVoiceID}. Saved configs still hold that field, so
	 * it is read as a fallback for {@code id}. Writing is left to gson, which uses the fields above.
	 */
	static final class Adapter implements JsonDeserializer<VoiceID> {

		private static final String MODEL_NAME = "modelName";
		private static final String ID = "id";
		private static final String LEGACY_ID = "piperVoiceID";

		@Override
		public VoiceID deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
			if (!element.isJsonObject()) return null;

			JsonObject object = element.getAsJsonObject();

			JsonElement modelName = object.get(MODEL_NAME);
			if (modelName == null || modelName.isJsonNull()) return null;

			JsonElement id = object.get(ID);
			if (id == null || id.isJsonNull()) {
				id = object.get(LEGACY_ID);
			}
			if (id == null || id.isJsonNull()) return null;

			return new VoiceID(modelName.getAsString(), id.getAsString());
		}
	}
}
