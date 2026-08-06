package dev.phyce.naturalspeech.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Selectable ElevenLabs TTS models. Labels include a price/speed hint shown in the dropdown. */
@Getter
@RequiredArgsConstructor
public enum ElevenLabsModel {
	FLASH_V2_5("Flash v2.5 — fast, cheapest", "eleven_flash_v2_5"),
	MULTILINGUAL_V2("Multilingual v2 — best quality, ~2x price", "eleven_multilingual_v2"),
	V3("v3 — most expressive, higher price + slower", "eleven_v3");

	private final String label;
	private final String id;

	@Override
	public String toString() {
		return label;
	}
}
