package dev.phyce.naturalspeech.configs;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.VoiceConfigDatum;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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

	public VoiceID[] getPlayerVoiceIDs(@NonNull String playerUserName) {
		if (Objects.equals(PluginHelper.getLocalPlayerUsername(), playerUserName)) {
			VoiceID voice = VoiceID.fromIDString(PluginHelper.getConfig().personalVoiceID());
			if (voice != null) return new VoiceID[] {voice};
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

	public VoiceID[] getNpcVoiceIDs(@NonNull String npcName) {
		NPCNameVoiceConfigDatum npcNameVoiceConfigDatum = this.npcNameVoices.get(npcName.toLowerCase());

		//TODO loop through and see if there are any entries with the same name in NPCIDs
		NPCIDVoiceConfigDatum npcIDVoiceConfigDatum = this.npcIDVoices.get(npcName.toLowerCase());

		if (npcNameVoiceConfigDatum == null && npcIDVoiceConfigDatum == null) return null;

		VoiceID[] nameBasedVoices =
			npcNameVoiceConfigDatum != null? npcNameVoiceConfigDatum.getVoiceIDs(): new VoiceID[0];
		VoiceID[] idBasedVoices = npcIDVoiceConfigDatum != null? npcIDVoiceConfigDatum.getVoiceIDs(): new VoiceID[0];

		VoiceID[] combinedVoices = new VoiceID[nameBasedVoices.length + idBasedVoices.length];
		System.arraycopy(nameBasedVoices, 0, combinedVoices, 0, nameBasedVoices.length);
		System.arraycopy(idBasedVoices, 0, combinedVoices, nameBasedVoices.length, idBasedVoices.length);

		return combinedVoices;
	}

}
