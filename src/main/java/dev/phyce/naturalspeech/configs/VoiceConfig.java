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
	private final Map<String, UsernameVoice> playerVoices = Collections.synchronizedMap(new HashMap<>());
	private final Map<Integer, NPCVoice> npcVoices = Collections.synchronizedMap(new HashMap<>());

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

	public String toJSON() {
		VoiceConfigJSON voiceConfigJSON = new VoiceConfigJSON();
		voiceConfigJSON.getUsernameVoiceData().addAll(playerVoices.values());
		voiceConfigJSON.getNpcIDVoiceData().addAll(npcVoices.values());
		return RuneLiteAPI.GSON.toJson(voiceConfigJSON);
	}

	private void loadDatum(VoiceConfigJSON data) {
		clear();

		for (UsernameVoice datum : data.getUsernameVoiceData()) {
			playerVoices.put(datum.getPlayerName(), datum);
		}

		for (NPCVoice datum : data.getNpcIDVoiceData()) {
			npcVoices.put(datum.getNpcId(), datum);
		}
	}

	public void setDefaultPlayerVoice(@NonNull String standardized_username, VoiceID voiceID) {
		playerVoices.putIfAbsent(standardized_username, new UsernameVoice(standardized_username));
		UsernameVoice datum = playerVoices.get(standardized_username);
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
		npcVoices.putIfAbsent(npcID, new NPCVoice(npcID));
		NPCVoice datum = npcVoices.get(npcID);
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
		UsernameVoice datum = playerVoices.get(standardized_username);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) playerVoices.remove(standardized_username);
	}


	public void removeNpcIdVoice(int npcID, VoiceID voiceID) {
		NPCVoice datum = npcVoices.get(npcID);
		if (datum == null) return;
		datum.getVoiceIDs().remove(voiceID);
		if (datum.getVoiceIDs().isEmpty()) npcVoices.remove(npcID);
	}


	public void clearPlayerVoices(@NonNull String standardized_username) {
		playerVoices.remove(standardized_username);
	}

	public int countAll() {
		return playerVoices.size() + npcVoices.size() ;
	}

	public void clear() {
		playerVoices.clear();
		npcVoices.clear();
	}

	@CheckForNull
	public List<VoiceID> findUsername(@NonNull String standardized_username) {
		UsernameVoice usernameVoice = this.playerVoices.get(standardized_username);

		if (usernameVoice == null) return null;
		if (usernameVoice.getVoiceIDs().isEmpty()) return null;

		return usernameVoice.getVoiceIDs();
	}

	@CheckForNull
	public List<VoiceID> findNpcId(int npcID) {
		NPCVoice npcIDVoice = this.npcVoices.get(npcID);

		if (npcIDVoice == null) return null;
		if (npcIDVoice.getVoiceIDs().isEmpty()) return null;

		return npcIDVoice.getVoiceIDs();
	}


	//<editor-fold desc="> Reset">
	public void resetNpcIdVoices(int npcID) {
		npcVoices.remove(npcID);
	}

	public void resetPlayerVoice(@NonNull String username) {
		playerVoices.remove(username);
	}

	//</editor-fold>
	// Used for JSON Serialization
	@Value
	public static class VoiceConfigJSON {

		public List<UsernameVoice> usernameVoiceData = new ArrayList<>();
		public List<NPCVoice> npcIDVoiceData = new ArrayList<>();

	}
	// Used for JSON Serialization
	@Data
	public static class UsernameVoice {
		List<VoiceID> voiceIDs = new ArrayList<>();

		String playerName;

		public UsernameVoice(String playerName) {
			this.playerName = playerName;
		}



	}
	// Used for JSON Serialization
	@Data
	public static class NPCVoice {

		// implicitly implements ModelAndVoiceConfig::getModelAndVoice through lombok@Data
		List<VoiceID> voiceIDs = new ArrayList<>();

		int npcId;
		public NPCVoice(int npcId) {
			this.npcId = npcId;
		}
	}

}
