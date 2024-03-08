package dev.phyce.naturalspeech.tts.uservoiceconfigs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.VoiceConfigDatum;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;
import lombok.NonNull;
import dev.phyce.naturalspeech.helpers.PluginHelper;


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

	public VoiceConfig(@NonNull String path) throws JsonSyntaxException {

		playerNameToPlayerNameVoiceConfigData = new HashMap<>();
		npcidToVoiceConfigDataHashMap = new HashMap<>();
		npcNameToNPCNameVoiceConfigDataHashMap = new HashMap<>();
		gson = new Gson();
		String jsonContent = readFileToString(path);
		loadJSON(jsonContent);
	}

	public void loadJSON(String jsonString) {
		try {
			VoiceConfigDatum data = gson.fromJson(jsonString, VoiceConfigDatum.class);
			loadDatum(data);
		} catch (JsonSyntaxException e) {
			System.err.println("JSON syntax error: " + e.getMessage());
		} catch (IllegalStateException e) {
			System.err.println("Illegal state encountered during JSON parsing: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("An unexpected error occurred during JSON parsing: " + e.getMessage());
		}
	}

	public String readFileToString(String filePath) {
		String content = "";
		try {
			content = new String(Files.readAllBytes(Paths.get(filePath)));
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
			// Handle the error or rethrow as needed
		}
		return content;
	}

	public void GetCurrentWorkingDirectory() {
		String currentWorkingDirectory = System.getProperty("user.dir");
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

	public VoiceID[] getPlayerVoiceIDs(String playerUserName) {
		if (PluginHelper.getClientUsername().equals(playerUserName)) {
			Supplier<VoiceID> voiceIDSupplier = () -> {
				String personalVoiceID = PluginHelper.getConfig().personalVoiceID();
				try {
					String[] parts = personalVoiceID.split(":");
					if (parts.length == 2) {
						String modelName = parts[0];
						int piperVoiceID = Integer.parseInt(parts[1]);
						return new VoiceID(modelName, piperVoiceID);
					}
				} catch (Exception e) {}
				return null;
			};
			VoiceID voice = voiceIDSupplier.get();
			if(voice != null) return new VoiceID[]{voice};
//			return new VoiceID[] {voice};
		}
		PlayerNameVoiceConfigDatum playerNameVoiceConfigDatum = playerNameToPlayerNameVoiceConfigData.get(playerUserName.toLowerCase());

		if (playerNameVoiceConfigDatum == null) return null;

		return playerNameVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] findVoiceIDsWithNPCID(int npcID) {
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = npcidToVoiceConfigDataHashMap.get(npcID);

		if (npcIDVoiceConfigDatum == null) return null;

		return npcIDVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] getNpcVoiceIDs(String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = npcNameToNPCNameVoiceConfigDataHashMap.get(npcName.toLowerCase());

		if (npcNameVoiceConfigDatum == null) return null;

		return npcNameVoiceConfigDatum.getVoiceIDs();
	}
}
