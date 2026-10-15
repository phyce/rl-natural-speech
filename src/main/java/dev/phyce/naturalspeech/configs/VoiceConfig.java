package dev.phyce.naturalspeech.configs;

import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.CharacterVoiceDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.VoiceConfigDatum;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import lombok.NonNull;
import net.runelite.http.api.RuneLiteAPI;

public class VoiceConfig {
	private final Map<String, PlayerNameVoiceConfigDatum> playerVoices;
	private final Map<Integer, NPCIDVoiceConfigDatum> npcIDVoices;
	private final Map<String, NPCNameVoiceConfigDatum> npcNameVoices;

	public VoiceConfig() {
		playerVoices = new HashMap<>();
		npcIDVoices = new HashMap<>();
		npcNameVoices = new HashMap<>();
	}

	public void setDefaultPlayerVoice(@NonNull String standardized_username, VoiceID voiceID) {
		playerVoices.putIfAbsent(standardized_username, new PlayerNameVoiceConfigDatum(standardized_username));
		setPrimaryVoice(playerVoices.get(standardized_username).getVoiceIDs(), voiceID);
	}

	public void setDefaultNpcIdVoice(int npcID, @CheckForNull String npcName, VoiceID voiceID) {
		npcIDVoices.putIfAbsent(npcID, new NPCIDVoiceConfigDatum(npcID));
		NPCIDVoiceConfigDatum datum = npcIDVoices.get(npcID);
		if (npcName != null) datum.setNpcName(npcName);
		setPrimaryVoice(datum.getVoiceIDs(), voiceID);
	}

	public void setDefaultNpcNameVoice(@NonNull String npcName, VoiceID voiceID) {
		npcNameVoices.putIfAbsent(npcName, new NPCNameVoiceConfigDatum(npcName));
		setPrimaryVoice(npcNameVoices.get(npcName).getVoiceIDs(), voiceID);
	}

	private static void setPrimaryVoice(List<VoiceID> voiceIDs, VoiceID voiceID) {
		if (!voiceIDs.isEmpty()) voiceIDs.remove(0);
		voiceIDs.remove(voiceID);
		voiceIDs.add(0, voiceID);
	}

	public List<PlayerNameVoiceConfigDatum> listPlayerVoices() {
		return new ArrayList<>(playerVoices.values());
	}

	public List<NPCIDVoiceConfigDatum> listNpcIdVoices() {
		return new ArrayList<>(npcIDVoices.values());
	}

	public List<NPCNameVoiceConfigDatum> listNpcNameVoices() {
		return new ArrayList<>(npcNameVoices.values());
	}

	public CharacterVoiceDatum addPlayer(@NonNull String standardized_username) {
		return playerVoices.computeIfAbsent(standardized_username, PlayerNameVoiceConfigDatum::new);
	}

	public CharacterVoiceDatum addNpcName(@NonNull String npcName) {
		return npcNameVoices.computeIfAbsent(npcName, NPCNameVoiceConfigDatum::new);
	}

	public boolean hasPlayer(@NonNull String standardized_username) {
		return playerVoices.containsKey(standardized_username);
	}

	public boolean hasNpcName(@NonNull String npcName) {
		return npcNameVoices.containsKey(npcName);
	}

	public void remove(@NonNull CharacterVoiceDatum datum) {
		playerVoices.values().removeIf(existing -> existing == datum);
		npcIDVoices.values().removeIf(existing -> existing == datum);
		npcNameVoices.values().removeIf(existing -> existing == datum);
	}

	public void unsetPlayerVoice(@NonNull String standardized_username, VoiceID voiceID) {
		PlayerNameVoiceConfigDatum datum = playerVoices.get(standardized_username);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) playerVoices.remove(standardized_username);
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

	public void clearPlayerVoices(@NonNull String standardized_username) {
		playerVoices.remove(standardized_username);
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

	public int countAll() {
		return playerVoices.size() + npcIDVoices.size() + npcNameVoices.size();
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

	@CheckForNull
	public List<VoiceID> findUsername(@NonNull String standardized_username) {
		PlayerNameVoiceConfigDatum playerNameVoiceConfigDatum = this.playerVoices.get(standardized_username);

		if (playerNameVoiceConfigDatum == null) return null;
		if (playerNameVoiceConfigDatum.getVoiceIDs().isEmpty()) return null;

		return playerNameVoiceConfigDatum.getVoiceIDs();
	}

	@CheckForNull
	public List<VoiceID> findNpcId(int npcID) {
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = this.npcIDVoices.get(npcID);

		if (npcIDVoiceConfigDatum == null) return null;
		if (npcIDVoiceConfigDatum.getVoiceIDs().isEmpty()) return null;

		return npcIDVoiceConfigDatum.getVoiceIDs();
	}

	@CheckForNull
	public List<VoiceID> findNpcName(@NonNull String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = this.npcNameVoices.get(npcName.toLowerCase());

		if (npcNameVoiceConfigDatum == null) return null;
		if (npcNameVoiceConfigDatum.getVoiceIDs().isEmpty()) return null;

		return npcNameVoiceConfigDatum.getVoiceIDs();
	}


	//<editor-fold desc="> Reset">
	public void resetNpcIdVoices(int npcID) {
		npcIDVoices.remove(npcID);
	}
	public void resetNpcNameVoices(@NonNull String npcName) {
		npcNameVoices.remove(npcName);
	}
	public void resetPlayerVoice(@NonNull String username) {
		playerVoices.remove(username);
	}
	//</editor-fold>
}
