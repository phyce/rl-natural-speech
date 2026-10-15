package dev.phyce.naturalspeech.enums;

public enum SpeechEngine {
	PIPER("Piper"),
	ELEVENLABS("ElevenLabs");

	private final String label;

	SpeechEngine(String label) {
		this.label = label;
	}

	public static SpeechEngine forModel(String modelName) {
		return ElevenLabsModel.fromId(modelName) != null ? ELEVENLABS : PIPER;
	}

	@Override
	public String toString() {
		return label;
	}
}
