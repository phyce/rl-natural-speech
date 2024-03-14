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

	private final Multimap<ModelRepository.ModelLocal, VoiceID> activeVoiceMap = HashMultimap.create();

	@Inject
	public VoiceManager(NaturalSpeechPlugin plugin, ConfigManager configManager) {
		this.textToSpeech = plugin.getTextToSpeech();
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
	private VoiceID findFirstActiveVoice(@NonNull List<VoiceID> voiceIdAndFallbacks) {
		for (VoiceID voiceID : voiceIdAndFallbacks) {
			if (textToSpeech.isModelActive(voiceID.getModelName())) {
				return voiceID;
			}
		}
		return null;
	}

	@NonNull
	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName) throws VoiceSelectionOutOfOption {
		npcName = npcName.toLowerCase();

		VoiceID result;
		{
			List<VoiceID> results = voiceConfig.findNpcId(npcId);
			if (results != null) {
				result = findFirstActiveVoice(results);
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
				result = findFirstActiveVoice(results);
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

	@NonNull
	public VoiceID getVoiceIDFromUsername(@NonNull String standardized_username) throws VoiceSelectionOutOfOption {
		List<VoiceID> voiceAndFallback = voiceConfig.findUsername(standardized_username);

		VoiceID result;
		if (voiceAndFallback != null) {
			result = findFirstActiveVoice(voiceAndFallback);
		} else {
			result = null;
		}

		if (result == null) {
			Player player = PluginHelper.findPlayerWithUsername(standardized_username);
			if (player != null) {
				Gender gender = Gender.parseInt(player.getPlayerComposition().getGender());
				log.debug("No existing settings found for {}, using randomize gendered voice.", standardized_username);
				return randomGenderedVoice(standardized_username, gender);
			}
			else {
				log.debug("No Player object found with {}, using random voice.", standardized_username);
				VoiceID voiceID = randomVoice();
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

	public void setActorVoiceID(@NonNull Actor actor, VoiceID voiceId) {
		if (actor instanceof NPC) {
			NPC npc = ((NPC) actor);
			voiceConfig.setDefaultNpcIdVoice(npc.getId(), voiceId);
			log.debug("Setting Default NPC Voice for {} to {}", npc.getName(), voiceId);
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


	@CheckForNull
	public VoiceID randomVoiceFromActiveModels(String username) {
		int hashCode = username.hashCode();

		long count = activeVoiceMap.values().size();
		Optional<VoiceID> first = activeVoiceMap.values().stream().skip(Math.abs(hashCode) % count).findFirst();

		return first.orElse(null);
	}

	public VoiceID randomGenderedVoice(String username,
									   Gender gender) {
		List<VoiceID> voiceIDs = genderedVoiceMap.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			throw new IllegalArgumentException("No voices available for the specified gender");
		}
		int hashCode = username.hashCode();
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
}
