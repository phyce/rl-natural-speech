package dev.phyce.naturalspeech.texttospeech;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.collections.GenderedVoiceMap;
import dev.phyce.naturalspeech.configs.VoiceSettings;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.utils.ClientHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;


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

	private final Map<EntityID, VoiceID> settings = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Gendered Voice Map contains the voices registered with the VoiceManager,
	 * able to query by gender.
	 */
	private final GenderedVoiceMap genderedVoiceMap = new GenderedVoiceMap();

	/**
	 * Active Voice Map contains the active voices that are registered with the VoiceManager.
	 *
	 * @see #register(VoiceID, Gender)
	 * @see #unregister(VoiceID)
	 */
	private final Set<VoiceID> activeVoiceMap = Collections.synchronizedSet(new HashSet<>());

	@Inject
	public VoiceManager(ConfigManager configManager, ClientHelper clientHelper) {
		this.configManager = configManager;
		this.clientHelper = clientHelper;

		// try to load from existing json in configManager
		String json = configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY);

		if (json != null) {
			settings.putAll(VoiceSettings.fromJSON(json));
		}
		else {
			log.info("No existing voice config stored in profile, falling back to default");
			// if configManager fails, load default from resources
			settings.putAll(VoiceSettings.fromJSON(PluginResources.DEFAULT_VOICE_CONFIG_JSON));
		}
	}

	public void save() {
		configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY, VoiceSettings.toJSON(settings));
	}

	/**
	 * Registered voices must be ready to speak.
	 * <b>Unregister the voice when no longer speakable by the engine.</b>
	 *
	 * @param voiceID Voice
	 * @param gender  Gender
	 *
	 * @see #unregister(VoiceID)
	 */
	public void register(VoiceID voiceID, Gender gender) {
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
	 * @see #register(VoiceID, Gender)
	 */
	public void unregister(VoiceID voiceID) {
		if (!activeVoiceMap.contains(voiceID)) {
			log.error("Attempting to unregister VoiceID that was never registered. {}", voiceID);
			return;
		}
		activeVoiceMap.remove(voiceID);
		genderedVoiceMap.removeVoiceID(voiceID);
	}

	public boolean isActive(VoiceID voiceID) {
		return activeVoiceMap.contains(voiceID);
	}

	@CheckForNull
	public VoiceID get(@NonNull EntityID entityID) {
		return settings.get(entityID);
	}

	@NonNull
	public VoiceID resolve(@NonNull EntityID entityID) {
		Preconditions.checkState(!activeVoiceMap.isEmpty(), "No active voices.");

		// if there is setting for this entity, use that
		VoiceID voiceID = settings.get(entityID);

		if (voiceID != null && !activeVoiceMap.contains(voiceID)) {
			log.trace("Voice Setting found, but not active: {}", voiceID);
			voiceID = null;
		}

		// no settings, randomize
		if (voiceID == null) {
			log.trace("No voice setting for {}. Randomizing voice.", entityID);
			voiceID = random(entityID);
		} else {
			log.trace("Voice setting found for {}: {}", entityID, voiceID);
		}

		return voiceID;
	}

	public boolean contains(EntityID entityID) {
		return settings.containsKey(entityID);
	}

	public void set(EntityID entityID, VoiceID voiceId) {
		settings.put(entityID, voiceId);
	}

	public void unset(EntityID entityID) {
		settings.remove(entityID);
	}


	@NonNull
	private VoiceID random(EntityID eid) {
		Preconditions.checkState(!activeVoiceMap.isEmpty(), "No active voices.");

		Gender gender = clientHelper.getGender(eid);

		Set<VoiceID> voiceIDs = genderedVoiceMap.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			// no voices available for gender
			return fallback();
		}

		int hashCode = eid.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs
			.stream()
			.skip(voice)
			.findFirst()
			.orElseThrow();
	}

	// Ultimate fallback
	@NonNull
	private VoiceID fallback() {
		Preconditions.checkState(!activeVoiceMap.isEmpty(), "No active voices.");

		long count = activeVoiceMap.size();
		Optional<VoiceID> first = activeVoiceMap.stream().skip((int) (Math.random() * count)).findFirst();
		return first.orElseThrow();
	}
	//<editor-fold desc="> Get">

}
