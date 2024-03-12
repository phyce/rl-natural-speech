package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;

import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

@Value
public class NPCNameVoiceConfigDatum {

	// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
	VoiceID[] voiceIDs;

	/**
	 * Can be wildcard, ex *Bat matches Giant Bat, Little Bat, etc.
	 */
	String npcName;

}
