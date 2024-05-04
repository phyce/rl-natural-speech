package dev.phyce.naturalspeech.texttospeech;

import com.google.common.base.Preconditions;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.collections.GenderedVoiceMap;
import dev.phyce.naturalspeech.statics.AudioLineNames;
import dev.phyce.naturalspeech.utils.ClientHelper;
import dev.phyce.naturalspeech.utils.Standardize;
import java.util.Collections;
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


/**
 * VoiceManager interfaces between user configs and voices registered by the running speech engines.
 * <br><br>
 * <b>Speech Engines must remember to unregister their voices on stop; otherwise VoiceManager will assume the voices
 * are still speakable.</b>
 */
@Slf4j
@PluginSingleton
public class VoiceManager {

	private final ConfigManager configManager;
	private final ClientHelper clientHelper;

	/**
	 * VoiceConfig contains the user's voice settings for NPCs and other players.
	 */
	private final VoiceConfig voiceConfig = new VoiceConfig();

	/**
	 * Gendered Voice Map contains the voices registered with the VoiceManager,
	 * able to query by gender.
	 */
	private final GenderedVoiceMap genderedVoiceMap = new GenderedVoiceMap();

	/**
	 * Active Voice Map contains the active voices that are registered with the VoiceManager.
	 * @see #registerVoiceID(VoiceID, Gender)
	 * @see #unregisterVoiceID(VoiceID)
	 */
	private final Set<VoiceID> activeVoiceMap = Collections.synchronizedSet(new HashSet<>());

	@Inject
	public VoiceManager(ConfigManager configManager, ClientHelper clientHelper) {
		this.configManager = configManager;
		this.clientHelper = clientHelper;

		// try to load from existing json in configManager
		String json = configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY);
		if (json != null) {
			try {
				voiceConfig.loadJSON(json);
				log.info("Loaded {} voice config entries from existing profile settings.",
					voiceConfig.countAll());
			} catch (JsonSyntaxException ignored) {
				// fallback to default json
				log.error("Invalid voice config stored in profile, falling back to default. Invalid JSON: {}", json);
			}
		}
		else {
			log.info("No existing voice config stored in profile, falling back to default");
			// if configManager fails, load default from resources
			voiceConfig.loadDefault();
		}
	}

	/**
	 * helper function for Piper, registers each voice inside the model with {@link #registerVoiceID(VoiceID, Gender)}.
	 * <b>Unregister the voice when no longer speakable by the engine.</b>
	 */
	public void registerPiperModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata voiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			registerVoiceID(voiceMetadata.toVoiceID(), voiceMetadata.getGender());
		}
	}

	/**
	 * helper function for Piper, unregisters each voice inside the model with {@link #unregisterVoiceID(VoiceID)}
	 */
	public void unregisterPiperModel(PiperRepository.ModelLocal modelLocal) {
		for (PiperRepository.PiperVoiceMetadata voiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			unregisterVoiceID(voiceMetadata.toVoiceID());
		}
	}

	/**
	 * Registered voices must be ready to speak.
	 * <b>Unregister the voice when no longer speakable by the engine.</b>
	 *
	 * @param voiceID Voice
	 * @param gender  Gender
	 *
	 * @see #unregisterVoiceID(VoiceID)
	 */
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

	/**
	 * When the voice is no longer speakable, unregister the voice.
	 *
	 * @param voiceID Voice
	 *
	 * @see #registerVoiceID(VoiceID, Gender)
	 */
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

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY, voiceConfig.toJSON());
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
	@SuppressWarnings("unused")
	@CheckForNull
	public VoiceID randomVoice() {
		long count = activeVoiceMap.size();
		Optional<VoiceID> first = activeVoiceMap.stream().skip((int) (Math.random() * count)).findFirst();

		return first.orElse(null);
	}

	//<editor-fold desc="> Get">
	@CheckForNull
	private VoiceID contains(@NonNull List<VoiceID> voiceIdAndFallbacks) {
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
	public VoiceID getVoiceIDFromNPCId(int npcId, @NonNull String npcName) throws VoiceSelectionOutOfOption {
		npcName = Standardize.getStandardName(npcName);
		Preconditions.checkNotNull(npcName);

		VoiceID result = null;

		List<VoiceID> results = voiceConfig.findUsername(AudioLineNames.GLOBAL_NPC);
		if (results != null) {
			result = contains(results);
		}

		if (results == null) {
			// Check NPC ID, takes priority over everything.
			results = voiceConfig.findNpcId(npcId);
			if (results != null) {
				result = contains(results);
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
				log.debug("No existing NPC ID voice was found for NPC id:{} npcName:{}", npcId, npcName);
			}
		}

		if (result == null) {
			// Check NPC Name
			results = voiceConfig.findNpcName(npcName);
			if (results != null) result = contains(results);

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
			result = contains(voiceAndFallback);
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
			Player player = clientHelper.findPlayerWithUsername(standardized_username);
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

	//</editor-fold>
}
