package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;

import dev.phyce.naturalspeech.tts.VoiceID;
import lombok.Value;

@Value
public class PlayerNameVoiceConfigDatum {

	VoiceID[] voiceIDs;

	String playerName;

}
