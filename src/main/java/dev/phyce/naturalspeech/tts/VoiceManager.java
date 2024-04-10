package dev.phyce.naturalspeech.tts;

import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;


@Slf4j
@PluginSingleton
public class VoiceManager {

	public final static String VOICE_CONFIG_FILE = "speaker_config.json";
	private final VoiceConfig voiceConfig;
	private final ConfigManager configManager;
	private final GenderedVoiceMap genderedVoiceMap;

	private final Set<VoiceID> activeVoiceMap = new HashSet<>();

	@Inject
	public VoiceManager(ConfigManager configManager) {
		this.configManager = configManager;
		this.genderedVoiceMap = new GenderedVoiceMap();
		voiceConfig = new VoiceConfig();

		loadVoiceConfig();
	}

	public void registerPiperModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata voiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			registerVoiceID(voiceMetadata.toVoiceID(), voiceMetadata.getGender());
		}
	}

	public void unregisterPiperModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata voiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			unregisterVoiceID(voiceMetadata.toVoiceID());
		}
	}

	public void registerVoiceID(VoiceID voiceID, Gender gender) {
		if (activeVoiceMap.contains(voiceID)) {
			log.error(
				"Attempting to register duplicate VoiceID. Likely another SpeechEngine is using the same VoiceID. {}",
				voiceID);
			return;
		}
		activeVoiceMap.add(voiceID);
		genderedVoiceMap.addVoiceID(gender, voiceID);
	}

	public void unregisterVoiceID(VoiceID voiceID) {
		if (!activeVoiceMap.contains(voiceID)) {
			log.error("Attempting to unregister VoiceID that was never registered. {}", voiceID);
			return;
		}
		activeVoiceMap.remove(voiceID);
		genderedVoiceMap.removeVoiceID(voiceID);
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

		long count = activeVoiceMap.size();

		Optional<VoiceID> first = activeVoiceMap.stream().skip(Math.abs(hashCode) % count).findFirst();

		return first.orElse(null);
	}

	@CheckForNull
	private VoiceID randomGenderedVoice(String standardized_username, Gender gender) {
		Set<VoiceID> voiceIDs = genderedVoiceMap.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			return null;
		}
		int hashCode = standardized_username.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs.stream().skip(voice).findFirst().orElse(null);
	}

	// Ultimate fallback
	@CheckForNull
	public VoiceID randomVoice() {
		long count = activeVoiceMap.size();
		Optional<VoiceID> first = activeVoiceMap.stream().skip((int) (Math.random() * count)).findFirst();

		return first.orElse(null);
	}

	//<editor-fold desc="> Get">
	@CheckForNull
	private VoiceID getFirstActiveVoice(@NonNull List<VoiceID> voiceIdAndFallbacks) {
		for (VoiceID voiceID : voiceIdAndFallbacks) {
			// if the config is invalid, a null might be present
			if (voiceID == null) continue;

			if (activeVoiceMap.contains(voiceID)) {
				return voiceID;
			}
		}
		return null;
	}

	@NonNull
	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName) throws VoiceSelectionOutOfOption {
		npcName = Text.standardize(npcName);

		VoiceID result = null;

		List<VoiceID> results = voiceConfig.findUsername(AudioLineNames.GLOBAL_NPC);
		if (results != null) return getFirstActiveVoice(results);

		if (results == null) {
			// Check NPC ID, takes priority over everything.
			results = voiceConfig.findNpcId(npcId);
			if (results != null) {
				result = getFirstActiveVoice(results);
				if (result == null) {
					log.debug("Existing NPC ID voice found for NPC id:{} npcName:{}, but model is not active", npcId,
						npcName);
				}
				else {
					log.debug("Existing NPC ID voice found for NPC id:{} npcName:{}, using {}",
						npcId, npcName, result);
				}
			}
			else {
				result = null;
				log.debug("No existing NPC ID voice was found for NPC id:{} npcName:{}", npcId, npcName);
			}
		}

		if (result == null) {
			// Check NPC Name
			results = voiceConfig.findNpcName(npcName);
			if (results != null) result = getFirstActiveVoice(results);

			if (result == null) {
				log.debug("No NPC ID voice found, NPC Name is also not available for NPC id:{} npcName:{}",
					npcId, npcName);
			}
			else {
				log.debug("No NPC ID voice found, falling back to NPC Name for NPC id:{} npcName:{}, using {}",
					npcId, npcName, result);
			}
		}

		if (result == null) {
			// If no NPC Global is available, randomize using npc name
			result = randomVoiceFromActiveModels(npcName);
			log.debug("No global NPC voice found, using random voice {}", result);
		}

		if (result == null) {
			log.debug("Voice selection out of options. Likely no models are active.");
			throw new VoiceSelectionOutOfOption();
		}

		return result;
	}

	@NonNull
	public VoiceID getVoiceIdForLocalPlayer() throws VoiceSelectionOutOfOption {
		return getVoiceIDFromUsername(AudioLineNames.LOCAL_USER);
	}

	public boolean containsUsername(@NonNull String standardized_username) {
		List<VoiceID> voiceAndFallback = voiceConfig.findUsername(standardized_username);
		return voiceAndFallback != null && !voiceAndFallback.isEmpty();
	}

	public boolean containsNPC(int npcId, @NonNull String standardized_name) {
		{
			List<VoiceID> voiceAndFallback = voiceConfig.findNpcId(npcId);
			if (voiceAndFallback != null && !voiceAndFallback.isEmpty()) {
				return true;
			}
		}

		{
			List<VoiceID> voiceAndFallback = voiceConfig.findNpcName(standardized_name);
			return voiceAndFallback != null && !voiceAndFallback.isEmpty();
		}
	}

	@NonNull
	public VoiceID getVoiceIDFromUsername(@NonNull String standardized_username) throws VoiceSelectionOutOfOption {
		List<VoiceID> voiceAndFallback = voiceConfig.findUsername(standardized_username);

		VoiceID result;
		if (voiceAndFallback != null) {
			result = getFirstActiveVoice(voiceAndFallback);
			if (result == null) {
				log.debug("Existing settings {} found for username {}, but model is not active.", voiceAndFallback,
					standardized_username);
			}
		}
		else {
			result = null;
			log.debug("No existing settings found for username {}, generate random voice.", standardized_username);
		}

		if (result == null) {
			Player player = PluginHelper.findPlayerWithUsername(standardized_username);
			if (player != null) {
				Gender gender = Gender.parseInt(player.getPlayerComposition().getGender());
				log.debug("Using randomize gendered voice for {}.", standardized_username);
				VoiceID voiceID = randomGenderedVoice(standardized_username, gender);
				if (voiceID != null) {
					return voiceID;
				}
				else {
					throw new VoiceSelectionOutOfOption();
				}
			}
			else {
				log.debug("Could not determine gender, no Player object found with {}, using random voice.",
					standardized_username);
				VoiceID voiceID = randomVoiceFromActiveModels(standardized_username);
				if (voiceID == null) {
					throw new VoiceSelectionOutOfOption();
				}
				return voiceID;
			}
		}
		else {
			log.debug("Existing settings found for {} and model is active. using {}.",
				standardized_username, result);
			return result;
		}
	}

	public void setDefaultVoiceIDForUsername(@NonNull String standardized_username, VoiceID voiceID) {
		voiceConfig.setDefaultPlayerVoice(standardized_username, voiceID);
	}


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

	public void setDefaultVoiceIDForNPC(@NonNull String npcName, VoiceID voiceId) {
		voiceConfig.setDefaultNpcNameVoice(npcName, voiceId);
	}

	//</editor-fold>

	//<editor-fold desc="> Reset">
	public void resetForUsername(@NonNull String standardized_username) {
		voiceConfig.resetPlayerVoice(standardized_username);
	}

	public void resetVoiceIDForNPC(@NonNull NPC actor) {
		voiceConfig.resetNpcIdVoices(actor.getId());
		voiceConfig.resetNpcIdVoices(actor.getComposition().getId());

		if (actor.getName() != null) {
			String standardNpcName = Text.standardize(Text.removeTags(actor.getName()));
			voiceConfig.resetNpcNameVoices(standardNpcName);
		}
	}

	public void resetVoiceIDForNPCName(@NonNull String npcName) {
		voiceConfig.resetNpcNameVoices(npcName);
	}

	//</editor-fold>
}
