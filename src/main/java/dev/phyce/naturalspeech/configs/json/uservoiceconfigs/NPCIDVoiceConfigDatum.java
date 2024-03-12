package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;

import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

@Value
public class NPCIDVoiceConfigDatum {

	// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
	VoiceID[] voiceIDs;

	int npcId;
}
