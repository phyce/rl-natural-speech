package dev.phyce.naturalspeech.tts;

import lombok.Data;

@Data
public class VoiceID {
	public String modelName;
	public int piperVoiceID;

	public VoiceID() {

	}

	public VoiceID(String modelName, int piperVoiceID) {
		this.modelName = modelName;
		this.piperVoiceID = piperVoiceID;
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

		// verify voice ID
		int voiceID;

		try {
			voiceID = Integer.parseUnsignedInt(split[1]);
		} catch (NumberFormatException ignored) {
			return null;
		}

		return new VoiceID(split[0], voiceID);
	}

	public String toVoiceIDString() {
		return String.format("%s:%d", modelName, piperVoiceID);
	}

	public String toString() {
		return toVoiceIDString();
	}
}
