package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;

import dev.phyce.naturalspeech.tts.VoiceID;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

// Used for JSON Serialization
@Data
public class NPCNameVoiceConfigDatum {

	List<VoiceID> voiceIDs = new ArrayList<>();

	/**
	 * Can be wildcard, ex *Bat matches Giant Bat, Little Bat, etc.
	 */
	String npcName;

	public NPCNameVoiceConfigDatum(String npcName) {
		this.npcName = npcName;
	}

}
