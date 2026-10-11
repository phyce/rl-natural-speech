package dev.phyce.naturalspeech.tts;

import com.google.gson.annotations.SerializedName;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeech;
import lombok.Data;

@Data
public class VoiceID {
	public String modelName;
	@SerializedName(value="id", alternate="piperVoiceID")
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

		if (split[1].isBlank()) return null;

		VoiceID voiceID = new VoiceID(split[0], split[1]);
		if (!NativeSpeech.isNativeModel(split[0]) && voiceID.getPiperVoiceID() < 0) return null;

		return voiceID;
	}

	public String toVoiceIDString() {
		return modelName + ":" + id;
	}

	public String toString() {
		return toVoiceIDString();
	}
}
