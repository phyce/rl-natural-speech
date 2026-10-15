package dev.phyce.naturalspeech.tts;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.CharacterVoiceDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.enums.SpeechType;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.tts.piper.Piper;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
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

	public final static String VOICE_CONFIG_FILE = "speaker_config.json";
	private static final String SOURCE_KEY_PREFIX = "voiceSource_";
	private final VoiceConfig voiceConfig;
	private final VoiceConfig defaultVoiceConfig;
	private final TextToSpeech textToSpeech;
	private final ConfigManager configManager;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final GenderedVoiceMap genderedVoiceMap;
	private final List<Runnable> characterListeners = new CopyOnWriteArrayList<>();
	private final Map<String, VoiceID> heardPlayers = new LinkedHashMap<>();
	private final Map<String, VoiceID> heardNpcs = new LinkedHashMap<>();

	private final Multimap<ModelRepository.ModelLocal, VoiceID> activeVoiceMap = HashMultimap.create();

	@Inject
	public VoiceManager(TextToSpeech textToSpeech, ConfigManager configManager,
						ElevenLabsVoiceRepository elevenLabsVoiceRepository) {
		this.textToSpeech = textToSpeech;
		this.configManager = configManager;
		this.elevenLabsVoiceRepository = elevenLabsVoiceRepository;
		this.genderedVoiceMap = new GenderedVoiceMap();
		voiceConfig = new VoiceConfig();
		defaultVoiceConfig = VoiceConfig.fromJson(readDefaultVoiceConfig());

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
		json = readDefaultVoiceConfig();

		try {
			voiceConfig.loadJSON(json);
			log.info("Loaded default JSON from ResourceFile " + VOICE_CONFIG_FILE);
		} catch (JsonSyntaxException e) {
			throw new RuntimeException("Failed to parse the default voice config JSON: " + json, e);
		}
	}

	private static String readDefaultVoiceConfig() {
		try {
			URL resourceUrl = Objects.requireNonNull(NaturalSpeechPlugin.class.getResource(VOICE_CONFIG_FILE));
			//noinspection UnstableApiUsage
			return Resources.toString(resourceUrl, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Default voice config file failed to load. " +
				"Either JSON is incorrect, file path is incorrect, or the file doesn't exist.");
		}
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE, voiceConfig.toJson());
	}

	public VoiceSource getSource(SpeechType speechType) {
		VoiceSource source = VoiceSource.fromConfigValue(
			configManager.getConfiguration(CONFIG_GROUP, SOURCE_KEY_PREFIX + speechType.name()));
		return source == null ? VoiceSource.PIPER : source;
	}

	public void setSource(SpeechType speechType, VoiceSource source) {
		configManager.setConfiguration(CONFIG_GROUP, SOURCE_KEY_PREFIX + speechType.name(), source.toConfigValue());
	}

	@CheckForNull
	public VoiceID randomVoiceFromActiveModels(String standardized_username, VoiceSource source) {
		int hashCode = standardized_username.hashCode();

		List<VoiceID> voiceIDs = activeVoices(source, null);
		if (voiceIDs.isEmpty()) return null;

		return voiceIDs.get(Math.abs(hashCode) % voiceIDs.size());
	}

	@CheckForNull
	private VoiceID randomGenderedVoice(String standardized_username, Gender gender, VoiceSource source) {
		List<VoiceID> voiceIDs = activeVoices(source, gender);
		if (voiceIDs.isEmpty()) {
			return null;
		}
		int hashCode = standardized_username.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs.get(voice);
	}

	private List<VoiceID> activeVoices(VoiceSource source, @CheckForNull Gender gender) {
		Collection<VoiceID> voiceIDs;
		if (source.getEngine() == SpeechEngine.ELEVENLABS) {
			voiceIDs = activeElevenLabsVoiceIDs(gender);
		}
		else {
			voiceIDs = gender == null ? activeVoiceMap.values() : genderedVoiceMap.find(gender);
		}
		return voiceIDs.stream().filter(source::includes).collect(Collectors.toList());
	}

	private List<VoiceID> activeElevenLabsVoiceIDs(@CheckForNull Gender gender) {
		List<VoiceID> voiceIDs = new ArrayList<>();
		if (!textToSpeech.isElevenLabsRunning()) return voiceIDs;

		for (ElevenLabsModel model : elevenLabsVoiceRepository.getEnabledModels()) {
			for (ElevenLabsVoice voice : elevenLabsVoiceRepository.getVoices()) {
				if (gender == null || voice.getGender() == gender) voiceIDs.add(voice.toVoiceID(model));
			}
		}
		return voiceIDs;
	}

	@CheckForNull
	private VoiceID fallbackVoice(String name, @CheckForNull Gender gender, VoiceSource source,
								  @CheckForNull List<VoiceID> configuredVoices) {
		Set<VoiceSource> sources = new LinkedHashSet<>(List.of(source, VoiceSource.of(source.getEngine())));
		if (source.getEngine() == SpeechEngine.ELEVENLABS) sources.add(VoiceSource.PIPER);

		for (VoiceSource candidate : sources) {
			VoiceID voiceID = gender == null ? null : randomGenderedVoice(name, gender, candidate);
			if (voiceID == null) voiceID = randomVoiceFromActiveModels(name, candidate);
			if (voiceID == null) continue;

			if (configuredVoices != null) {
				log.debug("Configured voices for {} are unavailable, saving {} as a fallback", name, voiceID);
				configuredVoices.add(voiceID);
				saveVoiceConfig();
				notifyCharactersChanged();
			}
			return voiceID;
		}
		return null;
	}

	// Ultimate fallback
	@CheckForNull
	public VoiceID randomVoice() {
		long count = activeVoiceMap.values().size();
		Optional<VoiceID> first = activeVoiceMap.values().stream().skip((int) (Math.random() * count)).findFirst();

		return first.orElse(null);
	}

	@CheckForNull
	public VoiceID randomVoice(@NonNull Gender gender, @NonNull VoiceSource source) {
		List<VoiceID> voiceIDs = activeVoices(source, gender);
		if (voiceIDs.isEmpty()) return null;
		return voiceIDs.get((int) (Math.random() * voiceIDs.size()));
	}

	//<editor-fold desc="> Get">
	@CheckForNull
	private VoiceID getFirstActiveVoice(@NonNull List<VoiceID> voiceIdAndFallbacks) {
		for (VoiceID voiceID : voiceIdAndFallbacks) {
			// if the config is invalid, a null might be present
			if (voiceID == null) continue;

			if (textToSpeech.isModelActive(voiceID.getModelName())) {
				return voiceID;
			}
		}
		return null;
	}

	@NonNull
	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName, VoiceSource source)
		throws VoiceSelectionOutOfOption {
		npcName = Text.standardize(npcName);

		VoiceID result = null;
		List<VoiceID> configuredVoices = null;

		{
			List<VoiceID> globalResults = voiceConfig.findUsername(MagicUsernames.GLOBAL_NPC);
			if (globalResults != null) {
				result = getFirstActiveVoice(globalResults);
				if (result != null) {
					log.debug("Global NPC voice overriding per-NPC config for NPC id:{} npcName:{}, using {}",
						npcId, npcName, result);
				}
			}
		}

		if (result == null) {
			List<VoiceID> results = voiceConfig.findNpcId(npcId);
			if (results != null) {
				configuredVoices = results;
				result = getFirstActiveVoice(results);
				if (result == null) {
					log.debug("Existing NPC ID voice found for NPC id:{} npcName:{}, but model is not active", npcId, npcName);
				} else {
					log.debug("Existing NPC ID voice found for NPC id:{} npcName:{}, using {}",
						npcId, npcName, result);
				}
			} else {
				log.debug("No existing NPC ID voice was found for NPC id:{} npcName:{}", npcId, npcName);
			}
		}

		if (result == null) {
			List<VoiceID> results = voiceConfig.findNpcName(npcName);
			if (results != null) {
				if (configuredVoices == null) configuredVoices = results;
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
			result = fallbackVoice(npcName, null, source, configuredVoices);
		}

		if (result == null) {
			throw new VoiceSelectionOutOfOption();
		}

		return result;
	}

	@NonNull
	public VoiceID getVoiceIdForLocalPlayer() throws VoiceSelectionOutOfOption {
		return getVoiceIDFromUsername(MagicUsernames.LOCAL_USER, VoiceSource.PIPER);
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
	public VoiceID getVoiceIDFromUsername(@NonNull String standardized_username, VoiceSource source)
		throws VoiceSelectionOutOfOption {
		List<VoiceID> voiceAndFallback = voiceConfig.findUsername(standardized_username);

		VoiceID result = null;
		if (voiceAndFallback != null) {
			result = getFirstActiveVoice(voiceAndFallback);
		}

		if (result == null) {
			Gender gender = null;
			Player player = PluginHelper.findPlayerWithUsername(standardized_username);
			if (player != null) {
				log.debug("No existing settings found for {}, using randomize gendered voice.", standardized_username);
				gender = Gender.parseInt(player.getPlayerComposition().getGender());
			}
			else {
				log.debug("No Player object found with {}, using random voice.", standardized_username);
			}

			VoiceID voiceID = fallbackVoice(standardized_username, gender, source, voiceAndFallback);
			if (voiceID == null) {
				throw new VoiceSelectionOutOfOption();
			}
			return voiceID;
		} else {
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
			String npcName = npc.getName() == null ? null : Text.standardize(Text.removeTags(npc.getName()));
			// I have no idea what a Composition is
			var compId = npc.getComposition().getId();
			// This is to solve the issue where the ModelID does not match the NPCID
			voiceConfig.setDefaultNpcIdVoice(npc.getId(), npcName, voiceId);
			voiceConfig.setDefaultNpcIdVoice(compId, npcName, voiceId);

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

	public void applyConfiguredVoice(@CheckForNull NPC npc, @NonNull String standardActorName, @NonNull VoiceID voiceID) {
		if (npc != null) {
			setActorVoiceID(npc, voiceID);
		}
		else {
			setDefaultVoiceIDForUsername(standardActorName, voiceID);
		}
		saveVoiceConfig();
		notifyCharactersChanged();
	}

	public void clearConfiguredVoice(@CheckForNull NPC npc, @NonNull String standardActorName) {
		if (npc != null) {
			resetVoiceIDForNPC(npc);
		}
		else {
			resetForUsername(standardActorName);
		}
		saveVoiceConfig();
		notifyCharactersChanged();
	}

	public List<PlayerNameVoiceConfigDatum> listPlayerVoices() {
		return voiceConfig.listPlayerVoices().stream()
			.filter(datum -> !MagicUsernames.isMagicUsername(datum.getPlayerName()))
			.filter(datum -> defaultVoiceConfig.findUsername(datum.getPlayerName()) == null)
			.collect(Collectors.toList());
	}

	public List<NPCIDVoiceConfigDatum> listNpcIdVoices() {
		return voiceConfig.listNpcIdVoices().stream()
			.filter(datum -> defaultVoiceConfig.findNpcId(datum.getNpcId()) == null)
			.collect(Collectors.toList());
	}

	public List<NPCNameVoiceConfigDatum> listNpcNameVoices() {
		return voiceConfig.listNpcNameVoices().stream()
			.filter(datum -> defaultVoiceConfig.findNpcName(datum.getNpcName()) == null)
			.collect(Collectors.toList());
	}

	public void addPlayer(@NonNull String name) {
		voiceConfig.addPlayer(Text.standardize(name));
		saveVoiceConfig();
		notifyCharactersChanged();
	}

	public void addNpc(@NonNull String name) {
		voiceConfig.addNpcName(Text.standardize(name));
		saveVoiceConfig();
		notifyCharactersChanged();
	}

	public void removeCharacter(@NonNull CharacterVoiceDatum datum) {
		voiceConfig.remove(datum);
		saveVoiceConfig();
		notifyCharactersChanged();
	}

	public void setPlayerVoices(@NonNull String name, @NonNull List<VoiceID> voiceIDs) {
		setCharacterVoices(voiceConfig.addPlayer(Text.standardize(name)), voiceIDs);
	}

	public void setNpcVoices(@NonNull String name, @NonNull List<VoiceID> voiceIDs) {
		setCharacterVoices(voiceConfig.addNpcName(Text.standardize(name)), voiceIDs);
	}

	public void rememberHeardPlayer(@NonNull String standardized_username, @NonNull VoiceID voiceID) {
		if (MagicUsernames.isMagicUsername(standardized_username) || voiceConfig.hasPlayer(standardized_username)) return;

		VoiceID previous = heardPlayers.remove(standardized_username);
		heardPlayers.put(standardized_username, voiceID);
		if (!voiceID.equals(previous)) notifyCharactersChanged();
	}

	public void rememberHeardNpc(int npcId, @NonNull String npcName, @NonNull VoiceID voiceID) {
		String standardNpcName = Text.standardize(npcName);
		if (voiceConfig.findNpcId(npcId) != null || voiceConfig.hasNpcName(standardNpcName)) return;

		VoiceID previous = heardNpcs.remove(standardNpcName);
		heardNpcs.put(standardNpcName, voiceID);
		if (!voiceID.equals(previous)) notifyCharactersChanged();
	}

	public Map<String, VoiceID> getHeardPlayers() {
		Map<String, VoiceID> heard = new LinkedHashMap<>(heardPlayers);
		heard.keySet().removeIf(voiceConfig::hasPlayer);
		return heard;
	}

	public Map<String, VoiceID> getHeardNpcs() {
		Map<String, VoiceID> heard = new LinkedHashMap<>(heardNpcs);
		heard.keySet().removeIf(voiceConfig::hasNpcName);
		return heard;
	}

	public void setCharacterVoices(@NonNull CharacterVoiceDatum datum, @NonNull List<VoiceID> voiceIDs) {
		datum.getVoiceIDs().clear();
		datum.getVoiceIDs().addAll(voiceIDs);
		saveVoiceConfig();
		notifyCharactersChanged();
	}

	private void notifyCharactersChanged() {
		for (Runnable listener : characterListeners) {
			listener.run();
		}
	}

	public void addCharacterListener(Runnable listener) {
		characterListeners.add(listener);
	}

	public void removeCharacterListener(Runnable listener) {
		characterListeners.remove(listener);
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
