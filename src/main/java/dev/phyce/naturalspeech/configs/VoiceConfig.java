package dev.phyce.naturalspeech.configs;

import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
public class VoiceConfig {
	private final Map<String, PlayerNameVoiceConfig> playerVoices = Collections.synchronizedMap(new HashMap<>());
	private final Map<Integer, NPCIDVoiceConfig> npcIDVoices = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, NPCNameVoiceConfig> npcNameVoices = Collections.synchronizedMap(new HashMap<>());

	public VoiceConfig() {
	}

	public void loadDefault() {
		loadJSON(PluginResources.defaultVoiceConfigJson);
	}

	public void loadJSON(String jsonString) throws JsonSyntaxException {
		clear();

		VoiceConfigJSON data = RuneLiteAPI.GSON.fromJson(jsonString, VoiceConfigJSON.class);
		loadDatum(data);
	}

	public static VoiceConfig fromJson(@NonNull String json) throws JsonSyntaxException {
		VoiceConfigJSON datum = RuneLiteAPI.GSON.fromJson(json, VoiceConfigJSON.class);
		VoiceConfig voiceConfig = new VoiceConfig();
		voiceConfig.loadDatum(datum);
		return voiceConfig;
	}

	public String toJSON() {
		VoiceConfigJSON voiceConfigJSON = new VoiceConfigJSON();
		voiceConfigJSON.getPlayerNameVoiceConfigData().addAll(playerVoices.values());
		voiceConfigJSON.getNpcIDVoiceConfigData().addAll(npcIDVoices.values());
		voiceConfigJSON.getNpcNameVoiceConfigData().addAll(npcNameVoices.values());
		return RuneLiteAPI.GSON.toJson(voiceConfigJSON);
	}

	private void loadDatum(VoiceConfigJSON data) {
		clear();

		for (PlayerNameVoiceConfig datum : data.getPlayerNameVoiceConfigData()) {
			playerVoices.put(datum.getPlayerName(), datum);
		}

		for (NPCIDVoiceConfig datum : data.getNpcIDVoiceConfigData()) {
			npcIDVoices.put(datum.getNpcId(), datum);
		}

		for (NPCNameVoiceConfig datum : data.getNpcNameVoiceConfigData()) {
			npcNameVoices.put(datum.getNpcName(), datum);
		}
	}

	public void setDefaultPlayerVoice(@NonNull String standardized_username, VoiceID voiceID) {
		playerVoices.putIfAbsent(standardized_username, new PlayerNameVoiceConfig(standardized_username));
		PlayerNameVoiceConfig datum = playerVoices.get(standardized_username);
		if (datum.getVoiceIDs().isEmpty()) {
			datum.getVoiceIDs().add(voiceID);
		}
		else {
			// remove duplicates
			datum.getVoiceIDs().remove(0);
			// prepend
			datum.getVoiceIDs().add(0, voiceID);
		}
	}

	public void setDefaultNpcIdVoice(int npcID, VoiceID voiceID) {
		npcIDVoices.putIfAbsent(npcID, new NPCIDVoiceConfig(npcID));
		NPCIDVoiceConfig datum = npcIDVoices.get(npcID);
		if (datum.getVoiceIDs().isEmpty()) {
			datum.getVoiceIDs().add(voiceID);
		}
		else {
			// remove duplicates
			datum.getVoiceIDs().remove(voiceID);
			// prepend
			datum.getVoiceIDs().add(0, voiceID);
		}
	}

	public void setDefaultNpcNameVoice(@NonNull String npcName, VoiceID voiceID) {
		npcNameVoices.putIfAbsent(npcName, new NPCNameVoiceConfig(npcName));
		NPCNameVoiceConfig datum = npcNameVoices.get(npcName);
		if (datum.getVoiceIDs().isEmpty()) {
			datum.getVoiceIDs().add(voiceID);
		}
		else {
			// remove duplicates
			datum.getVoiceIDs().remove(voiceID);
			// prepend
			datum.getVoiceIDs().add(0, voiceID);
		}
	}

	public void unsetPlayerVoice(@NonNull String standardized_username, VoiceID voiceID) {
		PlayerNameVoiceConfig datum = playerVoices.get(standardized_username);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) playerVoices.remove(standardized_username);
	}


	public void removeNpcIdVoice(int npcID, VoiceID voiceID) {
		NPCIDVoiceConfig datum = npcIDVoices.get(npcID);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) npcIDVoices.remove(npcID);
	}


	public void removeNpcNameVoice(@NonNull String npcName, VoiceID voiceID) {
		NPCNameVoiceConfig datum = npcNameVoices.get(npcName);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) npcNameVoices.remove(npcName);
	}

	public void clearPlayerVoices(@NonNull String standardized_username) {
		playerVoices.remove(standardized_username);
	}

	public int countAll() {
		return playerVoices.size() + npcIDVoices.size() + npcNameVoices.size();
	}

	public void clear() {
		playerVoices.clear();
		npcIDVoices.clear();
		npcNameVoices.clear();
	}

	@CheckForNull
	public List<VoiceID> findUsername(@NonNull String standardized_username) {
		PlayerNameVoiceConfig playerNameVoiceConfig = this.playerVoices.get(standardized_username);

		if (playerNameVoiceConfig == null) return null;
		if (playerNameVoiceConfig.getVoiceIDs().isEmpty()) return null;

		return playerNameVoiceConfig.getVoiceIDs();
	}

	@CheckForNull
	public List<VoiceID> findNpcId(int npcID) {
		NPCIDVoiceConfig npcIDVoiceConfig = this.npcIDVoices.get(npcID);

		if (npcIDVoiceConfig == null) return null;
		if (npcIDVoiceConfig.getVoiceIDs().isEmpty()) return null;

		return npcIDVoiceConfig.getVoiceIDs();
	}

	@CheckForNull
	public List<VoiceID> findNpcName(@NonNull String npcName) {
		NPCNameVoiceConfig npcNameVoiceConfig = this.npcNameVoices.get(npcName.toLowerCase());

		if (npcNameVoiceConfig == null) return null;
		if (npcNameVoiceConfig.getVoiceIDs().isEmpty()) return null;

		return npcNameVoiceConfig.getVoiceIDs();
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

	// Used for JSON Serialization
	@Value
	public static class VoiceConfigJSON {

		public List<PlayerNameVoiceConfig> playerNameVoiceConfigData = new ArrayList<>();
		public List<NPCIDVoiceConfig> npcIDVoiceConfigData = new ArrayList<>();
		public List<NPCNameVoiceConfig> npcNameVoiceConfigData = new ArrayList<>();

	}

	// Used for JSON Serialization
	@Data
	public static class PlayerNameVoiceConfig {

		List<VoiceID> voiceIDs = new ArrayList<>();

		String playerName;

		public PlayerNameVoiceConfig(String playerName) {
			this.playerName = playerName;
		}

	}

	// Used for JSON Serialization
	@Data
	public static class NPCIDVoiceConfig {

		// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
		List<VoiceID> voiceIDs = new ArrayList<>();

		int npcId;

		public NPCIDVoiceConfig(int npcId) {
			this.npcId = npcId;
		}
	}

	// Used for JSON Serialization
	@Data
	public static class NPCNameVoiceConfig {

		List<VoiceID> voiceIDs = new ArrayList<>();

		/**
		 * Can be wildcard, ex *Bat matches Giant Bat, Little Bat, etc.
		 */
		String npcName;

		public NPCNameVoiceConfig(String npcName) {
			this.npcName = npcName;
		}

	}
}
