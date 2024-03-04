package dev.phyce.naturalspeech.tts.uservoiceconfigs.json;

import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.Value;

@Value
public class NPCNameVoiceConfigDatum {

	// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
	private VoiceID[] voiceIDs;

	/**
	 * Can be wildcard, ex *Bat matches Giant Bat, Little Bat, etc.
	 */
	private String npcName;

}
