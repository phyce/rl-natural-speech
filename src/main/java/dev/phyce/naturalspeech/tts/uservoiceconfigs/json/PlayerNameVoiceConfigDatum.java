package dev.phyce.naturalspeech.tts.uservoiceconfigs.json;

import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.Value;

@Value
public class PlayerNameVoiceConfigDatum {

	private VoiceID[] voiceIDs;

	private String playerName;

}
