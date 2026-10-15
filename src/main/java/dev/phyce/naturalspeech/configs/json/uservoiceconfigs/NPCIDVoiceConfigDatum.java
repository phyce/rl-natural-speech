package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;

import dev.phyce.naturalspeech.tts.VoiceID;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Value;

// Used for JSON Serialization
@Data
public class NPCIDVoiceConfigDatum implements CharacterVoiceDatum {

	// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
	List<VoiceID> voiceIDs = new ArrayList<>();

	int npcId;

	String npcName;

	public NPCIDVoiceConfigDatum(int npcId) {
		this.npcId = npcId;
	}
}
