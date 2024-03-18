package dev.phyce.naturalspeech.tts;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.ModelRepository.Gender;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.VOICE_CONFIG_FILE;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckForNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;


@Slf4j
@Singleton
public class VoiceManager {

	private final VoiceConfig voiceConfig;
	private final TextToSpeech textToSpeech;
	private final ConfigManager configManager;
	private final GenderedVoiceMap genderedVoiceMap;

	private VoiceID npcVoice;
	private VoiceID systemVoice;

	private final Multimap<ModelRepository.ModelLocal, VoiceID> activeVoiceMap = HashMultimap.create();

	@Inject
	public VoiceManager(TextToSpeech textToSpeech, ConfigManager configManager) {
		this.textToSpeech = textToSpeech;
		this.configManager = configManager;
		this.genderedVoiceMap = new GenderedVoiceMap();
		voiceConfig = new VoiceConfig();

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				@Override
				public void onPiperStart(Piper piper) {
					ModelRepository.ModelLocal modelLocal = piper.getModelLocal();
					genderedVoiceMap.addModel(modelLocal);
					for (ModelRepository.VoiceMetadata voiceMetadata : modelLocal.getVoiceMetadata()) {
						activeVoiceMap.put(modelLocal, voiceMetadata.toVoiceID());
					}
				}

				@Override
				public void onPiperExit(Piper piper) {
					genderedVoiceMap.removeModel(piper.getModelLocal());
					activeVoiceMap.removeAll(piper.getModelLocal());
				}
			}
		);

		loadVoiceConfig();
	}

	@CheckForNull
	public List<VoiceID> checkVoiceIDWithUsername(@NonNull String standardized_username) {
		return voiceConfig.findUsername(standardized_username);
	}

	public void loadVoiceConfig() {
		// try to load from existing json in configManager
		String json = configManager.getConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE);
		if (json != null) {
			try {
				voiceConfig.loadJSON(json);
				log.info("Loaded {} voice config entries from existing VoiceConfig JSON from ConfigManager.",
					voiceConfig.countAll());
				return;
			} catch (JsonSyntaxException ignored) {
				// fallback to default json
				log.error("Invalid voiceConfig stored in ConfigManager, falling back to default: {}", json);
			}
		}
		else {
			log.error("No existing voiceConfig stored in ConfigManager, falling back to default");
		}

		// if configManager fails, load default from resources
		try {
			URL resourceUrl = Objects.requireNonNull(NaturalSpeechPlugin.class.getResource(VOICE_CONFIG_FILE));
			//noinspection UnstableApiUsage
			json = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Default voice config file failed to load. " +
				"Either JSON is incorrect, file path is incorrect, or the file doesn't exist.");
		}

		try {
			voiceConfig.loadJSON(json);
			log.info("Loaded default JSON from ResourceFile " + VOICE_CONFIG_FILE);
		} catch (JsonSyntaxException e) {
			throw new RuntimeException("Failed to parse the default voice config JSON: " + json, e);
		}
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE, voiceConfig.toJson());
	}

	@CheckForNull
	public VoiceID randomVoiceFromActiveModels(String standardized_username) {
		int hashCode = standardized_username.hashCode();

		long count = activeVoiceMap.values().size();
		Optional<VoiceID> first = activeVoiceMap.values().stream().skip(Math.abs(hashCode) % count).findFirst();

		return first.orElse(null);
	}

	@CheckForNull
	private VoiceID randomGenderedVoice(String standardized_username, Gender gender) {
		List<VoiceID> voiceIDs = genderedVoiceMap.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			return null;
		}
		int hashCode = standardized_username.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs.get(voice);
	}
	// Ultimate fallback
	@CheckForNull
	public VoiceID randomVoice() {
		long count = activeVoiceMap.values().size();
		Optional<VoiceID> first = activeVoiceMap.values().stream().skip((int) (Math.random() * count)).findFirst();

		return first.orElse(null);
	}

	//<editor-fold desc="> Get">
	@CheckForNull
	private VoiceID getFirstActiveVoice(@NonNull List<VoiceID> voiceIdAndFallbacks) {
		for (VoiceID voiceID : voiceIdAndFallbacks) {
			if (textToSpeech.isModelActive(voiceID.getModelName())) {
				return voiceID;
			}
		}
		return null;
	}

	@NonNull
	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName) throws VoiceSelectionOutOfOption {
		if (npcVoice != null) return npcVoice;
		npcName = npcName.toLowerCase();


		VoiceID result;
		{
			List<VoiceID> results = voiceConfig.findNpcId(npcId);
			if (results != null) {
				result = getFirstActiveVoice(results);
				if (result == null) {
					log.debug("Existing NPC ID voice found for NPC id:{} npcName:{}, but model is not active", npcId, npcName);
				} else {
					log.debug("Existing NPC ID voice found for NPC id:{} npcName:{}, using {}",
						npcId, npcName, result);
				}
			} else {
				result = null;
				log.debug("No existing NPC ID voice was found for NPC id:{} npcName:{}", npcId, npcName);
			}

		}

		if (result == null) {
			List<VoiceID> results = voiceConfig.findNpcName(npcName);
			if (results != null) {
				result = getFirstActiveVoice(results);
			}
			if (result == null) {
				log.debug("No NPC ID voice found, NPC Name is also not available for NPC id:{} npcName:{}",
					npcId, npcName);
			} else {
				log.debug("No NPC ID voice found, falling back to NPC Name for NPC id:{} npcName:{}, using {}",
					npcId, npcName, result);
			}
		}

		if (result == null) {
			result = randomVoiceFromActiveModels(npcName);
			log.debug("NPC_ID:{} and NPC_Name:{} both unavailable, using random voice from active models",
				npcId, npcName);
		}

		if (result == null) {
			throw new VoiceSelectionOutOfOption();
		}

		return result;
	}

	@NonNull
	public VoiceID getVoiceIdForLocalPlayer() throws VoiceSelectionOutOfOption {
		String localPlayerUsername = PluginHelper.getLocalPlayerUsername();
		if (localPlayerUsername == null) {
			log.debug("local player username was null, returning random voice.");
			VoiceID voiceId = randomVoice();
			if (voiceId == null) {
				throw new VoiceSelectionOutOfOption();
			}
			return voiceId;
		}
		return getVoiceIDFromUsername(localPlayerUsername);
	}
	public VoiceID getSystemVoiceID() {
		if (systemVoice != null) return systemVoice;

		VoiceID result = null;
		try {
			result = getVoiceIDFromUsername("&system");
		}
		catch(VoiceSelectionOutOfOption e) {
			throw new RuntimeException(e);
		}

		return result;
	}
	@NonNull
	public VoiceID getVoiceIDFromUsername(@NonNull String standardized_username) throws VoiceSelectionOutOfOption {
		List<VoiceID> voiceAndFallback = voiceConfig.findUsername(standardized_username);

		VoiceID result;
		if (voiceAndFallback != null) {
			result = getFirstActiveVoice(voiceAndFallback);
		} else {
			result = null;
		}

		if (result == null) {
			Player player = PluginHelper.findPlayerWithUsername(standardized_username);
			if (player != null) {
				Gender gender = Gender.parseInt(player.getPlayerComposition().getGender());
				log.debug("No existing settings found for {}, using randomize gendered voice.", standardized_username);
				VoiceID voiceID = randomGenderedVoice(standardized_username, gender);
				if (voiceID != null) {
					return voiceID;
				} else {
					throw new VoiceSelectionOutOfOption();
				}
			}
			else {
				log.debug("No Player object found with {}, using random voice.", standardized_username);
				VoiceID voiceID = randomVoiceFromActiveModels(standardized_username);
				if (voiceID == null) {
					throw new VoiceSelectionOutOfOption();
				}
				return voiceID;
			}
		} else {
			log.debug("Existing settings found for {} and model is active. using {}.",
				standardized_username, result);
			return result;
		}
	}
	//</editor-fold>

	//<editor-fold desc="> Set">
	public void setActorVoiceID(@NonNull Actor actor, VoiceID voiceId) {
		if (actor instanceof NPC) {
			NPC npc = ((NPC) actor);
			// I have no idea what a Composition is
			var compId = npc.getComposition().getId();
			// This is to solve the issue where the ModelID does not match the NPCID
			voiceConfig.setDefaultNpcIdVoice(npc.getId(), voiceId);
			voiceConfig.setDefaultNpcIdVoice(compId, voiceId);

			log.debug("Setting Default NPC Voice for NpcID: {} CompID: {} NpcName: {} to {}",
				npc.getId(), compId, npc.getName(), voiceId);
		}
		else if (actor instanceof Player) {
			String standardized_username = Text.standardize(Objects.requireNonNull(actor.getName()));
			voiceConfig.setDefaultPlayerVoice(standardized_username, voiceId);
			log.debug("Setting Default Player Voice for {} to {}", actor.getName(), voiceId);
		}
		else {
			log.error("Tried setting a voice for neither NPC or player. Possibly for an object.");
		}
	}
	public void setVoiceIDForUsername(@NonNull String standardized_username, VoiceID voiceID) {
		voiceConfig.setDefaultPlayerVoice(standardized_username, voiceID);
	}

	public void setVoiceIDForNPC(@NonNull String npcName, VoiceID voiceId) {
		voiceConfig.setDefaultNpcNameVoice(npcName, voiceId);
	}

	public void setVoiceIDForNPCs(@NonNull VoiceID voiceId) {
		this.npcVoice = voiceId;
	}
	public void setVoiceIDForSystem(@NonNull VoiceID voiceId) {
		this.systemVoice = voiceId;
	}
	//</editor-fold>

	//<editor-fold desc="> Reset">
	public void resetForUsername(@NonNull String standardized_username) {
		voiceConfig.resetPlayerVoice(standardized_username);
	}
	public void resetVoiceIDForSystem() {
		this.systemVoice = null;
	}
	public void resetVoiceIDForNPC(@NonNull Actor actor) {
		if (actor instanceof NPC) {
			voiceConfig.resetNpcIdVoices(((NPC) actor).getId());
			voiceConfig.resetNpcIdVoices(((NPC) actor).getComposition().getId());
			NPC npc = ((NPC) actor);
		}
	}

	public void resetVoiceIDForNPC(@NonNull String npcName) {
		voiceConfig.resetNpcNameVoices(npcName);
	}

	public void resetVoiceIDForNPCs() {
		this.npcVoice = null;
	}
	//</editor-fold>
}
