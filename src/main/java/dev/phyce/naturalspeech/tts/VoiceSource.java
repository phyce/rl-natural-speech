package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import javax.annotation.CheckForNull;
import lombok.Value;

@Value
public class VoiceSource {
	public static final VoiceSource PIPER = new VoiceSource(SpeechEngine.PIPER, null);
	public static final VoiceSource ELEVENLABS = new VoiceSource(SpeechEngine.ELEVENLABS, null);

	SpeechEngine engine;
	@CheckForNull
	String modelName;

	public static VoiceSource of(SpeechEngine engine) {
		return engine == SpeechEngine.ELEVENLABS ? ELEVENLABS : PIPER;
	}

	public static VoiceSource ofModel(String modelName) {
		return new VoiceSource(SpeechEngine.forModel(modelName), modelName);
	}

	@CheckForNull
	public static VoiceSource fromConfigValue(@CheckForNull String value) {
		if (value == null || value.isBlank()) return null;

		for (SpeechEngine engine : SpeechEngine.values()) {
			if (engine.name().equals(value)) return of(engine);
		}
		return ofModel(value);
	}

	public String toConfigValue() {
		return modelName == null ? engine.name() : modelName;
	}

	public boolean includes(VoiceID voiceID) {
		return voiceID.getEngine() == engine && (modelName == null || modelName.equals(voiceID.getModelName()));
	}

	public String getModelLabel() {
		if (modelName == null) return engine.toString();

		ElevenLabsModel model = ElevenLabsModel.fromId(modelName);
		return model == null ? modelName : model.toString();
	}

	@Override
	public String toString() {
		return modelName == null ? engine.toString() : engine + ": " + getModelLabel();
	}
}
