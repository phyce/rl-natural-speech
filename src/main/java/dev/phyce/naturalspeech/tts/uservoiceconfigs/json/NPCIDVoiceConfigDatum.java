package dev.phyce.naturalspeech.tts.uservoiceconfigs.json;

import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.Value;

@Value
public class NPCIDVoiceConfigDatum {

	// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
	private VoiceID[] voiceIDs;

	private int npcId;
}
