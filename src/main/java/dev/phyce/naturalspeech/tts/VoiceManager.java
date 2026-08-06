package dev.phyce.naturalspeech.tts;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabs;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeechEngine;
import dev.phyce.naturalspeech.tts.nativespeech.NativeVoice;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

	public final static String VOICE_CONFIG_FILE = "speaker_config.json";
	private final VoiceConfig voiceConfig;
	private final TextToSpeech textToSpeech;
	private final ConfigManager configManager;
	private final NaturalSpeechConfig config;
	private final CharacterVoices characterVoices;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final GenderedVoiceMap genderedVoiceMap;

	private final Multimap<ModelRepository.ModelLocal, VoiceID> activeVoiceMap = HashMultimap.create();
	private final List<VoiceID> nativeVoiceIDs = new ArrayList<>();
	private final List<VoiceID> elevenLabsVoiceIDs = new ArrayList<>();
	/** So a missing key does not log once per spoken line. Cleared when voices arrive. */
	private boolean elevenLabsWarned = false;

	@Inject
	public VoiceManager(
		TextToSpeech textToSpeech,
		ConfigManager configManager,
		NaturalSpeechConfig config,
		CharacterVoices characterVoices,
		ElevenLabsVoiceRepository elevenLabsVoiceRepository) {
		this.textToSpeech = textToSpeech;
		this.configManager = configManager;
		this.config = config;
		this.characterVoices = characterVoices;
		this.elevenLabsVoiceRepository = elevenLabsVoiceRepository;
		this.genderedVoiceMap = new GenderedVoiceMap();
		voiceConfig = new VoiceConfig();

		// Fires on the client thread, so touching the voice maps here is safe
		elevenLabsVoiceRepository.addListener((removed, added) -> {
			for (ElevenLabsVoice voice : removed) {
				VoiceID voiceID = voice.toVoiceID();
				genderedVoiceMap.removeVoice(voice.getGender(), voiceID);
				elevenLabsVoiceIDs.remove(voiceID);
			}

			for (ElevenLabsVoice voice : added) {
				VoiceID voiceID = voice.toVoiceID();
				genderedVoiceMap.addVoice(voice.getGender(), voiceID);
				elevenLabsVoiceIDs.add(voiceID);
			}

			if (!added.isEmpty()) elevenLabsWarned = false;

			log.debug("Registered {} ElevenLabs voice(s)", added.size());
		});

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

				@Override
				public void onNativeSpeechStart(NativeSpeechEngine engine) {
					for (NativeVoice voice : engine.getVoices()) {
						VoiceID voiceID = voice.toVoiceID();
						genderedVoiceMap.addVoice(voice.getGender(), voiceID);
						nativeVoiceIDs.add(voiceID);
					}
					log.debug("Registered {} system voice(s)", engine.getVoices().size());
				}

				@Override
				public void onNativeSpeechExit(NativeSpeechEngine engine) {
					for (NativeVoice voice : engine.getVoices()) {
						genderedVoiceMap.removeVoice(voice.getGender(), voice.toVoiceID());
					}
					nativeVoiceIDs.clear();
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

	private static boolean matches(@CheckForNull SpeechEngine engine, @NonNull VoiceID voiceID) {
		return engine == null || TextToSpeech.engineOfModel(voiceID.getModelName()) == engine;
	}

	private static List<VoiceID> filter(@NonNull List<VoiceID> voiceIDs, @CheckForNull SpeechEngine engine) {
		if (engine == null) return voiceIDs;

		List<VoiceID> matching = new ArrayList<>();
		for (VoiceID voiceID : voiceIDs) {
			if (voiceID != null && matches(engine, voiceID)) matching.add(voiceID);
		}
		return matching;
	}

	/**
	 * ElevenLabs costs real money per character, so it never borrows another engine's voices: if it
	 * has nothing to speak with, the message stays silent rather than quietly coming out in piper.
	 */
	private static boolean isStrict(@CheckForNull SpeechEngine engine) {
		return engine == SpeechEngine.ELEVENLABS;
	}

	private boolean hasVoicesFor(@CheckForNull SpeechEngine engine) {
		return !filter(allActiveVoiceIDs(), engine).isEmpty();
	}

	private List<VoiceID> allActiveVoiceIDs() {
		List<VoiceID> all = new ArrayList<>(activeVoiceMap.values());
		all.addAll(nativeVoiceIDs);
		all.addAll(elevenLabsVoiceIDs);
		return all;
	}

	private List<VoiceID> activeVoiceIDs(@CheckForNull SpeechEngine engine) {
		List<VoiceID> all = allActiveVoiceIDs();
		List<VoiceID> matching = filter(all, engine);

		if (!matching.isEmpty()) return matching;

		return isStrict(engine) ? matching : all;
	}

	@CheckForNull
	public VoiceID randomVoiceFromActiveModels(String standardized_username, @CheckForNull SpeechEngine engine) {
		int hashCode = standardized_username.hashCode();

		List<VoiceID> voiceIDs = activeVoiceIDs(engine);
		if (voiceIDs.isEmpty()) return null;

		return voiceIDs.get(Math.abs(hashCode) % voiceIDs.size());
	}

	@CheckForNull
	private VoiceID randomGenderedVoice(String standardized_username, Gender gender,
										@CheckForNull SpeechEngine engine) {
		List<VoiceID> voiceIDs = genderedVoiceMap.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			return null;
		}

		voiceIDs = filter(voiceIDs, engine);
		if (voiceIDs.isEmpty()) return null;

		int hashCode = standardized_username.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs.get(voice);
	}
	@CheckForNull
	public VoiceID anyVoiceFor(@NonNull SpeechEngine engine) {
		List<VoiceID> voiceIDs = filter(allActiveVoiceIDs(), engine);
		if (voiceIDs.isEmpty()) return null;

		// sorted so the same setting does not land on a different voice each startup
		voiceIDs.sort(java.util.Comparator.comparing(VoiceID::toVoiceIDString));
		return voiceIDs.get(0);
	}

	// Ultimate fallback
	@CheckForNull
	public VoiceID randomVoice() {
		List<VoiceID> voiceIDs = allActiveVoiceIDs();
		if (voiceIDs.isEmpty()) return null;

		return voiceIDs.get((int) (Math.random() * voiceIDs.size()));
	}

	//<editor-fold desc="> Get">
	@CheckForNull
	private VoiceID getFirstActiveVoice(@NonNull List<VoiceID> voiceIdAndFallbacks,
										@CheckForNull SpeechEngine engine) {
		VoiceID wrongEngine = null;

		for (VoiceID voiceID : voiceIdAndFallbacks) {
			// if the config is invalid, a null might be present
			if (voiceID == null) continue;
			if (!textToSpeech.isModelActive(voiceID.getModelName())) continue;

			if (matches(engine, voiceID)) return voiceID;
			if (wrongEngine == null) wrongEngine = voiceID;
		}

		if (isStrict(engine)) return null;

		return hasVoicesFor(engine) ? null : wrongEngine;
	}

	/**
	 * The ElevenLabs voice configured for your own character under settings -> ElevenLabs -> Your
	 * voice. Kept separate from the piper/system personal voice so both can be set at once and the
	 * right one is used depending on which engine the message type asks for.
	 */
	@CheckForNull
	private VoiceID elevenLabsPersonalVoice(@NonNull String identity, @CheckForNull SpeechEngine engine) {
		if (engine != SpeechEngine.ELEVENLABS) return null;
		if (!MagicUsernames.LOCAL_USER.equals(identity)) return null;

		String personal = config.elevenLabsPersonalVoice();
		if (personal == null || personal.trim().isEmpty()) return null;

		return ElevenLabs.voiceID(personal.trim());
	}

	/**
	 * A voice from the gender set on a Custom Characters row. Scoped to ElevenLabs because that is
	 * where the panel puts the setting — piper and system voices carry readable names, so a gender
	 * hint earns its place only for ElevenLabs' opaque ids.
	 * <p>
	 * This is a manual override of the automatic pick, not a pinned voice: it applies only once no
	 * explicit voice has matched. It is most useful for NPCs, which have no in-game gender to infer.
	 */
	@CheckForNull
	private VoiceID genderedOverride(@NonNull String identity, @CheckForNull SpeechEngine engine) {
		if (engine != SpeechEngine.ELEVENLABS) return null;

		Gender gender = characterVoices.getGender(identity);
		if (gender == null) return null;

		VoiceID result = randomGenderedVoice(identity, gender, engine);

		if (result == null) {
			// Gender comes from labels.gender on each voice, which plenty of libraries leave unset
			log.debug("{} is set to {}, but no ElevenLabs voice is labelled with that gender", identity, gender);
		}

		return result;
	}

	@NonNull
	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName) throws VoiceSelectionOutOfOption {
		return getVoiceIDFromNPCId(npcId, npcName, null);
	}

	@NonNull
	public VoiceID getVoiceIDFromNPCId(int npcId, String npcName, @CheckForNull SpeechEngine engine)
		throws VoiceSelectionOutOfOption {
		npcName = Text.standardize(npcName);

		VoiceID result = null;

		{
			List<VoiceID> globalResults = voiceConfig.findUsername(MagicUsernames.GLOBAL_NPC);
			if (globalResults != null) {
				result = getFirstActiveVoice(globalResults, engine);
				if (result != null) {
					log.debug("Global NPC voice overriding per-NPC config for NPC id:{} npcName:{}, using {}",
						npcId, npcName, result);
				}
			}
		}

		// A character curated in the Custom Characters tab is described entirely by its row, so the
		// npc-id and npc-name layers below are skipped for them. Merely ranking the row higher is not
		// enough: clearing its id would then fall through to whatever the right-click flow wrote
		// against the npc id, and the row's gender would never get a say.
		final boolean curated = characterVoices.contains(npcName);

		if (result == null && curated) {
			result = curatedCharacterVoice(npcName, engine);
			if (result != null) {
				log.debug("Custom Characters entry for NPC id:{} npcName:{}, using {}", npcId, npcName, result);
			}
		}

		if (result == null && !curated) {
			List<VoiceID> results = voiceConfig.findNpcId(npcId);
			if (results != null) {
				result = getFirstActiveVoice(results, engine);
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

		if (result == null && !curated) {
			List<VoiceID> results = voiceConfig.findNpcName(npcName);
			if (results != null) {
				result = getFirstActiveVoice(results, engine);
			}
			if (result == null) {
				log.debug("No NPC ID voice found, NPC Name is also not available for NPC id:{} npcName:{}",
					npcId, npcName);
			} else {
				log.debug("No NPC ID voice found, falling back to NPC Name for NPC id:{} npcName:{}, using {}",
					npcId, npcName, result);
			}
		}

		// No pinned voice matched, so a manually configured gender gets to steer the automatic pick
		if (result == null) {
			result = genderedOverride(npcName, engine);
		}

		if (result == null) {
			result = randomVoiceFromActiveModels(npcName, engine);
		}

		if (result == null) {
			warnIfElevenLabsUnusable(engine);
			throw new VoiceSelectionOutOfOption();
		}

		return result;
	}

	/**
	 * ElevenLabs deliberately stays silent rather than borrowing another engine's voice, which is
	 * indistinguishable from a broken plugin unless we say why. Warns once per outage.
	 */
	private void warnIfElevenLabsUnusable(@CheckForNull SpeechEngine engine) {
		if (engine != SpeechEngine.ELEVENLABS) return;
		if (elevenLabsWarned) return;

		elevenLabsWarned = true;

		if (!elevenLabsVoiceRepository.isConfigured()) {
			log.warn("A message type is set to ElevenLabs but no API key is configured, so those "
				+ "messages stay silent. Add one under Natural Speech settings -> ElevenLabs.");
		}
		else {
			log.warn("A message type is set to ElevenLabs but no voices have loaded for this API key, "
				+ "so those messages stay silent. Press Check key in the Natural Speech panel to see why.");
		}
	}

	@NonNull
	public VoiceID getVoiceIdForLocalPlayer() throws VoiceSelectionOutOfOption {
		return getVoiceIDFromUsername(MagicUsernames.LOCAL_USER);
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
		return getVoiceIDFromUsername(standardized_username, null);
	}

	@NonNull
	public VoiceID getVoiceIDFromUsername(@NonNull String standardized_username, @CheckForNull SpeechEngine engine)
		throws VoiceSelectionOutOfOption {
		{
			VoiceID personal = elevenLabsPersonalVoice(standardized_username, engine);
			if (personal != null) {
				log.debug("Using the configured ElevenLabs personal voice {}", personal);
				return personal;
			}
		}

		List<VoiceID> voiceAndFallback = voiceConfig.findUsername(standardized_username);

		VoiceID result;
		if (voiceAndFallback != null) {
			result = getFirstActiveVoice(voiceAndFallback, engine);
		} else {
			result = null;
		}

		if (result == null) {
			Player player = PluginHelper.findPlayerWithUsername(standardized_username);
			// A manually configured gender beats the one read off the player's in-game appearance
			VoiceID voiceID = genderedOverride(standardized_username, engine);

			if (voiceID != null) {
				log.debug("Using the gender configured for {} in Custom Characters", standardized_username);
			}
			else if (player != null) {
				Gender gender = Gender.parseInt(player.getPlayerComposition().getGender());
				log.debug("No existing settings found for {}, using randomize gendered voice.", standardized_username);
				voiceID = randomGenderedVoice(standardized_username, gender, engine);
			}
			else {
				log.debug("No Player object found with {}, using random voice.", standardized_username);
			}

			if (voiceID == null) {
				voiceID = randomVoiceFromActiveModels(standardized_username, engine);
			}

			if (voiceID == null) {
				warnIfElevenLabsUnusable(engine);
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

	/**
	 * The pinned voice for a character the user has actually added to the Custom Characters tab,
	 * filtered to the engine being asked for. Null for anyone not curated, which leaves the ordinary
	 * npc-id and npc-name lookups untouched for everyone else.
	 */
	@CheckForNull
	private VoiceID curatedCharacterVoice(@NonNull String name, @CheckForNull SpeechEngine engine) {
		if (!characterVoices.contains(name)) return null;

		return getFirstActiveVoice(voiceConfig.findCharacterVoices(CharacterVoices.normalize(name)), engine);
	}

	/** The voice pinned for a character on one engine, or null when that engine has none. */
	@CheckForNull
	public VoiceID getCharacterVoice(@NonNull String name, @NonNull SpeechEngine engine) {
		for (VoiceID voiceID : voiceConfig.findCharacterVoices(CharacterVoices.normalize(name))) {
			if (voiceID != null && TextToSpeech.engineOfModel(voiceID.getModelName()) == engine) {
				return voiceID;
			}
		}

		return null;
	}

	/**
	 * Pins a character's voice on one engine, or clears it when {@code voiceID} is null. The voices
	 * configured for the other engines are left alone, so a character can carry one per engine.
	 */
	public void setCharacterVoice(@NonNull String name, @NonNull SpeechEngine engine,
								  @CheckForNull VoiceID voiceID) {
		// findNpcName lowercases its lookup key, so anything written here has to be normalized or it
		// would never be found again
		String key = CharacterVoices.normalize(name);

		List<VoiceID> voiceIDs = voiceConfig.findCharacterVoices(key);
		voiceIDs.removeIf(
			existing -> existing == null || TextToSpeech.engineOfModel(existing.getModelName()) == engine);

		if (voiceID != null) voiceIDs.add(voiceID);

		voiceConfig.setCharacterVoices(key, voiceIDs);
		saveVoiceConfig();
	}

	/**
	 * Applies a voice picked from the game UI and lists the character in the Custom Characters tab.
	 * <p>
	 * Must run on the client thread: {@link #setActorVoiceID} reads NPC composition and name.
	 */
	public void applyConfiguredVoice(@CheckForNull NPC npc, @NonNull String standardActorName,
									 @NonNull VoiceID voiceID) {
		if (npc != null) {
			setActorVoiceID(npc, voiceID);
		}
		else {
			setDefaultVoiceIDForUsername(standardActorName, voiceID);
		}

		// Also store it by name, which is the only key the Custom Characters panel can look up
		setCharacterVoice(standardActorName, TextToSpeech.engineOfModel(voiceID.getModelName()), voiceID);
		characterVoices.add(standardActorName);

		saveVoiceConfig();

		// add() is a no-op for an already-listed character, so nudge the panel explicitly
		characterVoices.notifyChanged();
	}

	/** Clears a character's configured voice from the game UI. Must run on the client thread. */
	public void clearConfiguredVoice(@CheckForNull NPC npc, @NonNull String standardActorName) {
		if (npc != null) {
			resetVoiceIDForNPC(npc);
		}
		else {
			resetForUsername(standardActorName);
		}

		clearCharacterVoices(standardActorName);
		saveVoiceConfig();

		characterVoices.notifyChanged();
	}

	/** Clears every pinned voice for a character, across all engines. */
	public void clearCharacterVoices(@NonNull String name) {
		voiceConfig.setCharacterVoices(CharacterVoices.normalize(name), new ArrayList<>());
		saveVoiceConfig();
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
