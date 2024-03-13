package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;


import java.util.ArrayList;
import java.util.List;
import lombok.Value;

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
