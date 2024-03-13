package dev.phyce.naturalspeech.configs;

import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.VoiceConfigDatum;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import net.runelite.http.api.RuneLiteAPI;

public class VoiceConfig {
	public final Map<String, PlayerNameVoiceConfigDatum> playerVoices;
	public final Map<Integer, NPCIDVoiceConfigDatum> npcIDVoices;
	public final Map<String, NPCNameVoiceConfigDatum> npcNameVoices;

	public VoiceConfig() {
		playerVoices = new HashMap<>();
		npcIDVoices = new HashMap<>();
		npcNameVoices = new HashMap<>();
	}

	public void setDefaultPlayerVoice(@NonNull String username, VoiceID voiceID) {
		playerVoices.putIfAbsent(username, new PlayerNameVoiceConfigDatum(username));
		PlayerNameVoiceConfigDatum datum = playerVoices.get(username);
		if (datum.getVoiceIDs().isEmpty()) {
			datum.getVoiceIDs().add(voiceID);
		} else {
			// remove duplicates
			datum.getVoiceIDs().remove(voiceID);
			// prepend
			datum.getVoiceIDs().add(0, voiceID);
		}
	}

	public void setDefaultNpcIdVoice(int npcID, VoiceID voiceID) {
		npcIDVoices.putIfAbsent(npcID, new NPCIDVoiceConfigDatum(npcID));
		NPCIDVoiceConfigDatum datum = npcIDVoices.get(npcID);
		if (datum.getVoiceIDs().isEmpty()) {
			datum.getVoiceIDs().add(voiceID);
		} else {
			// remove duplicates
			datum.getVoiceIDs().remove(voiceID);
			// prepend
			datum.getVoiceIDs().add(0, voiceID);
		}
	}

	public void setDefaultNpcNameVoice(@NonNull String npcName, VoiceID voiceID) {
		npcNameVoices.putIfAbsent(npcName, new NPCNameVoiceConfigDatum(npcName));
		NPCNameVoiceConfigDatum datum = npcNameVoices.get(npcName);
		if (datum.getVoiceIDs().isEmpty()) {
			datum.getVoiceIDs().add(voiceID);
		} else {
			// remove duplicates
			datum.getVoiceIDs().remove(voiceID);
			// prepend
			datum.getVoiceIDs().add(0, voiceID);
		}
	}

	public void removePlayerVoice(@NonNull String username, VoiceID voiceID) {
		PlayerNameVoiceConfigDatum datum = playerVoices.get(username);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) playerVoices.remove(username);
	}

	public void removeNpcIdVoice(int npcID, VoiceID voiceID) {
		NPCIDVoiceConfigDatum datum = npcIDVoices.get(npcID);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) npcIDVoices.remove(npcID);
	}

	public void removeNpcNameVoice(@NonNull String npcName, VoiceID voiceID) {
		NPCNameVoiceConfigDatum datum = npcNameVoices.get(npcName);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) npcNameVoices.remove(npcName);
	}

	public void clearPlayerVoices(@NonNull String username) {
		playerVoices.remove(username);
	}

	public void clearNpcIdVoices(int npcID) {
		npcIDVoices.remove(npcID);
	}

	public void clearNpcNameVoices(@NonNull String npcName) {
		npcNameVoices.remove(npcName);
	}

	public static VoiceConfig fromDatum(@NonNull VoiceConfigDatum data) {
		VoiceConfig voiceConfig = new VoiceConfig();
		voiceConfig.loadDatum(data);
		return voiceConfig;
	}

	public static VoiceConfig fromJson(@NonNull String json) throws JsonSyntaxException {
		VoiceConfigDatum datum = RuneLiteAPI.GSON.fromJson(json, VoiceConfigDatum.class);
		return fromDatum(datum);
	}

	public void clear() {
		playerVoices.clear();
		npcIDVoices.clear();
		npcNameVoices.clear();
	}

	public void loadJSON(String jsonString) throws JsonSyntaxException {
		clear();

		VoiceConfigDatum data = RuneLiteAPI.GSON.fromJson(jsonString, VoiceConfigDatum.class);
		loadDatum(data);
	}

	public String toJson() {
		return RuneLiteAPI.GSON.toJson(toDatum());
	}

	public void loadDatum(VoiceConfigDatum data) {
		clear();

		for (PlayerNameVoiceConfigDatum datum : data.getPlayerNameVoiceConfigData()) {
			playerVoices.put(datum.getPlayerName(), datum);
		}

		for (NPCIDVoiceConfigDatum datum : data.getNpcIDVoiceConfigData()) {
			npcIDVoices.put(datum.getNpcId(), datum);
		}

		for (NPCNameVoiceConfigDatum datum : data.getNpcNameVoiceConfigData()) {
			npcNameVoices.put(datum.getNpcName(), datum);
		}
	}

	public VoiceConfigDatum toDatum() {
		VoiceConfigDatum voiceConfigDatum = new VoiceConfigDatum();
		voiceConfigDatum.getPlayerNameVoiceConfigData().addAll(playerVoices.values());
		voiceConfigDatum.getNpcIDVoiceConfigData().addAll(npcIDVoices.values());
		voiceConfigDatum.getNpcNameVoiceConfigData().addAll(npcNameVoices.values());
		return voiceConfigDatum;
	}

	public List<VoiceID> getWithUsername(@NonNull String playerUserName) {
		if (Objects.equals(PluginHelper.getLocalPlayerUsername(), playerUserName)) {
			VoiceID voice = VoiceID.fromIDString(PluginHelper.getConfig().personalVoiceID());
			// FIXME(Louis) Merge personal voice into player username
			if (voice != null) return List.of(voice);
		}
		PlayerNameVoiceConfigDatum playerNameVoiceConfigDatum = this.playerVoices.get(playerUserName.toLowerCase());

		if (playerNameVoiceConfigDatum == null) return null;

		return playerNameVoiceConfigDatum.getVoiceIDs();
	}

	public List<VoiceID> getWithNpcId(int npcID) {
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = this.npcIDVoices.get(npcID);

		if (npcIDVoiceConfigDatum == null) return null;

		return npcIDVoiceConfigDatum.getVoiceIDs();
	}

	public List<VoiceID> getWithNpcName(@NonNull String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = this.npcNameVoices.get(npcName.toLowerCase());

		if (npcNameVoiceConfigDatum == null) return null;

		return npcNameVoiceConfigDatum.getVoiceIDs();
	}

}
