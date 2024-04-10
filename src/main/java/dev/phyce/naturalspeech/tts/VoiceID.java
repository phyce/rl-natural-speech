package dev.phyce.naturalspeech.tts;

import com.google.gson.annotations.JsonAdapter;
import javax.annotation.CheckForNull;
import lombok.Data;

/**
 * VoiceID represents the model and id for a speak request. <br>
 * Similar to URLs for http, representing server and document.<br>
 */
@Data
@JsonAdapter(VoiceIDSerializer.class)
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

}
