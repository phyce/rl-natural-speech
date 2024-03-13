package dev.phyce.naturalspeech.intruments;

import dev.phyce.naturalspeech.tts.VoiceID;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Value;

@Data
public class VoiceHistory {
	String source;
	String text;
	List<Reason> history = new ArrayList<>();

	public VoiceHistory() {
	}

	public VoiceHistory(String source, String text) {
		this.source = source;
		this.text = text;
	}

	@Value
	public static class Reason {
		VoiceID voiceID;
		String reason;
	}
}
