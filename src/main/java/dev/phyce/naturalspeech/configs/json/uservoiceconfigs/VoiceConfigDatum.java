package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;


import lombok.Value;

import java.util.ArrayList;
import java.util.List;

// Used for JSON Serialization
@Value
public class VoiceConfigDatum {

	public List<PlayerNameVoiceConfigDatum> playerNameVoiceConfigData;
	public List<NPCIDVoiceConfigDatum> npcIDVoiceConfigData;
	public List<NPCNameVoiceConfigDatum> npcNameVoiceConfigData;

	public VoiceConfigDatum() {
		this.playerNameVoiceConfigData = new ArrayList<>();
		this.npcIDVoiceConfigData = new ArrayList<>();
		this.npcNameVoiceConfigData = new ArrayList<>();
	}

}
