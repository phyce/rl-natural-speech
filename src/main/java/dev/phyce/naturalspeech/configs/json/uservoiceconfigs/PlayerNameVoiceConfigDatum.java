package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;

import dev.phyce.naturalspeech.tts.VoiceID;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

// Used for JSON Serialization
@Data
public class PlayerNameVoiceConfigDatum {

	List<VoiceID> voiceIDs = new ArrayList<>();

	String playerName;

	public PlayerNameVoiceConfigDatum(String playerName) {
		this.playerName = playerName;
	}

}
