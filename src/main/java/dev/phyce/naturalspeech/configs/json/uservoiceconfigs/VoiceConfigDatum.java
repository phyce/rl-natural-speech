package dev.phyce.naturalspeech.configs.json.uservoiceconfigs;


import java.util.ArrayList;
import java.util.List;
import lombok.Value;

// Used for JSON Serialization
@Value
public class VoiceConfigDatum {

	public List<PlayerNameVoiceConfigDatum> playerNameVoiceConfigData = new ArrayList<>();
	public List<NPCIDVoiceConfigDatum> npcIDVoiceConfigData = new ArrayList<>();
	public List<NPCNameVoiceConfigDatum> npcNameVoiceConfigData = new ArrayList<>();

}
