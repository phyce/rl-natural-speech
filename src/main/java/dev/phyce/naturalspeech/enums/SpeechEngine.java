package dev.phyce.naturalspeech.enums;

public enum SpeechEngine {
	OFF("Off"),
	PIPER("Piper"),
	SYSTEM("System voices"),
	ELEVENLABS("ElevenLabs");

	private final String label;

	SpeechEngine(String label) {
		this.label = label;
	}

	public boolean isOff() {
		return this == OFF;
	}

	public static SpeechEngine gated(SpeechEngine gate, SpeechEngine engine) {
		return gate == OFF ? OFF : engine;
	}

	@Override
	public String toString() {
		return label;
	}
}
