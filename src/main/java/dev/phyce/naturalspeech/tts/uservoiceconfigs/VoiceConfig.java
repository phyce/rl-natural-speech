package dev.phyce.naturalspeech.tts.uservoiceconfigs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.VoiceConfigDatum;
import lombok.NonNull;

import java.util.HashMap;

public class VoiceConfig {
	private final HashMap<String, PlayerNameVoiceConfigDatum> playerNameToPlayerNameVoiceConfigData;
	private final HashMap<Integer, NPCIDVoiceConfigDatum> npcidToVoiceConfigDataHashMap;
	private final HashMap<String, NPCNameVoiceConfigDatum> npcNameToNPCNameVoiceConfigDataHashMap;
	private final Gson gson;

	public VoiceConfig(@NonNull VoiceConfigDatum data) {

		playerNameToPlayerNameVoiceConfigData = new HashMap<>();
		npcidToVoiceConfigDataHashMap = new HashMap<>();
		npcNameToNPCNameVoiceConfigDataHashMap = new HashMap<>();
		gson = new Gson();

		loadDatum(data);
	}

	public VoiceConfig(@NonNull String json) throws JsonSyntaxException {

		playerNameToPlayerNameVoiceConfigData = new HashMap<>();
		npcidToVoiceConfigDataHashMap = new HashMap<>();
		npcNameToNPCNameVoiceConfigDataHashMap = new HashMap<>();
		gson = new Gson();

		loadJSON(json);
	}

	public void loadJSON(String json) throws JsonSyntaxException {
		VoiceConfigDatum data = gson.fromJson(json, VoiceConfigDatum.class);
		loadDatum(data);
	}

	public String exportJSON() {
		return gson.toJson(exportDatum());
	}

	public void loadDatum(VoiceConfigDatum data) {
		playerNameToPlayerNameVoiceConfigData.clear();
		npcidToVoiceConfigDataHashMap.clear();
		npcNameToNPCNameVoiceConfigDataHashMap.clear();

		for (PlayerNameVoiceConfigDatum datum : data.getPlayerNameVoiceConfigData()) {
			playerNameToPlayerNameVoiceConfigData.put(datum.getPlayerName(), datum);
		}

		for (NPCIDVoiceConfigDatum datum : data.getNpcIDVoiceConfigData()) {
			npcidToVoiceConfigDataHashMap.put(datum.getNpcId(), datum);
		}

		for (NPCNameVoiceConfigDatum datum : data.getNpcNameVoiceConfigData()) {
			npcNameToNPCNameVoiceConfigDataHashMap.put(datum.getNpcName(), datum);
		}
	}

	public VoiceConfigDatum exportDatum() {
		VoiceConfigDatum voiceConfigDatum = new VoiceConfigDatum();
		voiceConfigDatum.getPlayerNameVoiceConfigData().addAll(playerNameToPlayerNameVoiceConfigData.values());
		voiceConfigDatum.getNpcIDVoiceConfigData().addAll(npcidToVoiceConfigDataHashMap.values());
		voiceConfigDatum.getNpcNameVoiceConfigData().addAll(npcNameToNPCNameVoiceConfigDataHashMap.values());
		return voiceConfigDatum;
	}

	public VoiceID[] findVoiceIDsWithPlayerUserName(String playerUserName) {
		PlayerNameVoiceConfigDatum playerNameVoiceConfigDatum = playerNameToPlayerNameVoiceConfigData.get(playerUserName);

		if (playerNameVoiceConfigDatum == null) return null;

		return playerNameVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] findVoiceIDsWithNPCID(int npcID) {
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = npcidToVoiceConfigDataHashMap.get(npcID);

		if (npcIDVoiceConfigDatum == null) return null;

		return npcIDVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] findVoiceIDsWithNPCName(String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = npcNameToNPCNameVoiceConfigDataHashMap.get(npcName);

		if (npcNameVoiceConfigDatum == null) return null;

		return npcNameVoiceConfigDatum.getVoiceIDs();
	}
}
