package dev.phyce.naturalspeech.tts;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import java.io.IOException;
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

	public int getPiperVoiceID() {
		try {
			return Integer.parseUnsignedInt(id);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	public SpeechEngine getEngine() {
		return SpeechEngine.forModel(modelName);
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

		if (SpeechEngine.forModel(split[0]) == SpeechEngine.ELEVENLABS) {
			return split[1].isBlank() ? null : new VoiceID(split[0], split[1].trim());
		}

		// verify voice ID
		int piperVoiceID;

		try {
			piperVoiceID = Integer.parseUnsignedInt(split[1]);
		} catch (NumberFormatException ignored) {
			return null;
		}

		return new VoiceID(split[0], piperVoiceID);
	}

	public String toVoiceIDString() {
		return modelName + ":" + id;
	}

	public String toString() {
		return toVoiceIDString();
	}

	static final class Adapter extends TypeAdapter<VoiceID> {
		private static final String MODEL_NAME = "modelName";
		private static final String ID = "id";
		private static final String PIPER_VOICE_ID = "piperVoiceID";

		@Override
		public void write(JsonWriter writer, VoiceID voiceID) throws IOException {
			if (voiceID == null) {
				writer.nullValue();
				return;
			}

			writer.beginObject();
			writer.name(MODEL_NAME).value(voiceID.modelName);
			if (voiceID.getEngine() == SpeechEngine.PIPER && voiceID.getPiperVoiceID() >= 0) {
				writer.name(PIPER_VOICE_ID).value(voiceID.getPiperVoiceID());
			}
			else {
				writer.name(ID).value(voiceID.id);
			}
			writer.endObject();
		}

		@Override
		public VoiceID read(JsonReader reader) throws IOException {
			if (reader.peek() == JsonToken.NULL) {
				reader.nextNull();
				return null;
			}

			String modelName = null;
			String id = null;
			String piperVoiceID = null;

			reader.beginObject();
			while (reader.hasNext()) {
				String name = reader.nextName();
				if (reader.peek() == JsonToken.NULL) {
					reader.skipValue();
					continue;
				}

				switch (name) {
					case MODEL_NAME:
						modelName = reader.nextString();
						break;
					case ID:
						id = reader.nextString();
						break;
					case PIPER_VOICE_ID:
						piperVoiceID = reader.nextString();
						break;
					default:
						reader.skipValue();
				}
			}
			reader.endObject();

			if (id == null) id = piperVoiceID;
			return modelName == null || id == null ? null : new VoiceID(modelName, id);
		}
	}
}
