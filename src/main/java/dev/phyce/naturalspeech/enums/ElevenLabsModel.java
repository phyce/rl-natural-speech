package dev.phyce.naturalspeech.enums;

import javax.annotation.CheckForNull;
import lombok.Getter;

public enum ElevenLabsModel {
	FLASH_V2_5("Flash v2.5", "eleven_flash_v2_5", "Fast & uses less credits."),
	MULTILINGUAL_V2("Multilingual v2", "eleven_multilingual_v2", "Multilingual"),
	V3("v3", "eleven_v3", "Most expressive");

	private final String label;
	@Getter
	private final String id;
	@Getter
	private final String description;

	ElevenLabsModel(String label, String id, String description) {
		this.label = label;
		this.id = id;
		this.description = description;
	}

	@CheckForNull
	public static ElevenLabsModel fromId(String id) {
		for (ElevenLabsModel model : values()) {
			if (model.id.equals(id)) return model;
		}
		return null;
	}

	@Override
	public String toString() {
		return label;
	}
}
