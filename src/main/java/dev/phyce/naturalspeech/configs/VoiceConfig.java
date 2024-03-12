package dev.phyce.naturalspeech.configs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.VoiceConfigDatum;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import dev.phyce.naturalspeech.helpers.PluginHelper;


import java.util.HashMap;

public class VoiceConfig {
	public HashMap<String, PlayerNameVoiceConfigDatum> playerVoices;
	public HashMap<Integer, NPCIDVoiceConfigDatum> npcIDVoices;
	public HashMap<String, NPCNameVoiceConfigDatum> npcNameVoices;
	public final static Gson gson = new Gson();

	public VoiceConfig(@NonNull VoiceConfigDatum data) {

		playerVoices = new HashMap<>();
		npcIDVoices = new HashMap<>();
		npcNameVoices = new HashMap<>();

		loadDatum(data);
	}

	public VoiceConfig(@NonNull String json) throws JsonSyntaxException {

		playerVoices = new HashMap<>();
		npcIDVoices = new HashMap<>();
		npcNameVoices = new HashMap<>();

		try {
		    VoiceConfigDatum data = gson.fromJson(json, VoiceConfigDatum.class);
			loadDatum(data);
		} catch (JsonSyntaxException e) {
			json = loadResourceFile(json);
			loadJSON(json);
		}
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

	public static String loadResourceFile(String fileName) {
		String fullPath = "/dev/phyce/naturalspeech/" + fileName;
		InputStream inputStream = VoiceConfig.class.getResourceAsStream(fullPath);
		if (inputStream == null) {
			throw new IllegalArgumentException("File not found! " + fullPath);
		} else {
			try (InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
				BufferedReader reader = new BufferedReader(streamReader)) {
				StringBuilder result = new StringBuilder();
				for(String line = reader.readLine(); line != null; line = reader.readLine()){
					result.append(line);
				}
				return result.toString();
			} catch (Exception e) {
				throw new RuntimeException("Failed to read the resource file: " + fullPath, e);
			}
		}
	}
	public String readFileToString(String resourceName) {
		String resourcePath = "dev/phyce/naturalspeech/" + resourceName;
		try (InputStream is = Objects.requireNonNull(
			Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath),
			"Resource not found: " + resourcePath);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

			return reader.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException | NullPointerException e) {
			System.err.println("Error reading resource: " + e.getMessage());
			return "";
		}
	}

	public void GetCurrentWorkingDirectory() {
		String currentWorkingDirectory = System.getProperty("user.dir");
	}

	public String toJson() {
		return gson.toJson(exportDatum());
	}

	public void loadDatum(VoiceConfigDatum data) {
		playerVoices.clear();
		npcIDVoices.clear();
		npcNameVoices.clear();

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

	public VoiceConfigDatum exportDatum() {
		VoiceConfigDatum voiceConfigDatum = new VoiceConfigDatum();
		voiceConfigDatum.getPlayerNameVoiceConfigData().addAll(playerVoices.values());
		voiceConfigDatum.getNpcIDVoiceConfigData().addAll(npcIDVoices.values());
		voiceConfigDatum.getNpcNameVoiceConfigData().addAll(npcNameVoices.values());
		return voiceConfigDatum;
	}

	public VoiceID[] getPlayerVoiceIDs(String playerUserName) {
		if (PluginHelper.getClientUsername().equals(playerUserName)) {
			VoiceID voice = VoiceID.fromIDString(PluginHelper.getConfig().personalVoiceID());
			if(voice != null) return new VoiceID[]{voice};
		}
		PlayerNameVoiceConfigDatum playerNameVoiceConfigDatum = this.playerVoices.get(playerUserName.toLowerCase());

		if (playerNameVoiceConfigDatum == null) return null;

		return playerNameVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] getNpcVoiceIDs(int npcID) {
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = this.npcIDVoices.get(npcID);

		if (npcIDVoiceConfigDatum == null) return null;

		return npcIDVoiceConfigDatum.getVoiceIDs();
	}

	public VoiceID[] getNpcVoiceIDs(String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = this.npcNameVoices.get(npcName.toLowerCase());

		//TODO loop through and see if there are any entries with the same name in NPCIDs
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = this.npcIDVoices.get(npcName.toLowerCase());

		if (npcNameVoiceConfigDatum == null && npcIDVoiceConfigDatum == null) return null;

		VoiceID[] nameBasedVoices = npcNameVoiceConfigDatum != null ? npcNameVoiceConfigDatum.getVoiceIDs() : new VoiceID[0];
		VoiceID[] idBasedVoices = npcIDVoiceConfigDatum != null ? npcIDVoiceConfigDatum.getVoiceIDs() : new VoiceID[0];

		VoiceID[] combinedVoices = new VoiceID[nameBasedVoices.length + idBasedVoices.length];
		System.arraycopy(nameBasedVoices, 0, combinedVoices, 0, nameBasedVoices.length);
		System.arraycopy(idBasedVoices, 0, combinedVoices, nameBasedVoices.length, idBasedVoices.length);

		return combinedVoices;
	}

}
