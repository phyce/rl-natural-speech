package dev.phyce.naturalspeech.texttospeech;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
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
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.RuneLiteAPI;


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
	private final GenderedVoiceMap genderCache = new GenderedVoiceMap();

	/**
	 * Active Voice Map contains the active voices that are registered with the VoiceManager.
	 *
	 * @see #register(VoiceID, Gender)
	 * @see #unregister(VoiceID)
	 */
	//	private final Set<VoiceID> actives = Collections.synchronizedSet(new HashSet<>());
	private final Set<VoiceID> blacklist = Collections.synchronizedSet(new HashSet<>());
	private final Map<VoiceID, Gender> disallowed = Collections.synchronizedMap(new HashMap<>());
	private final Map<VoiceID, Gender> allowed = Collections.synchronizedMap(new HashMap<>());

	@Inject
	public VoiceManager(ConfigManager configManager, ClientHelper clientHelper) {
		this.configManager = configManager;
		this.clientHelper = clientHelper;

		// try to load from existing json in configManager
		{
			String voiceJson = configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY);
			if (voiceJson != null) {
				settings.putAll(VoiceSettings.fromJSON(voiceJson));
			}
			else {
				log.info("No existing voice config stored in profile, falling back to default");
				// if configManager fails, load default from resources
				settings.putAll(VoiceSettings.fromJSON(PluginResources.DEFAULT_VOICE_CONFIG_JSON));
			}
		}

		{
			String blacklistJson = configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_BLACKLIST_KEY);
			List<VoiceID> value;
			if (blacklistJson != null) {
				Type type = new TypeToken<List<VoiceID>>() {}.getType();
				try {
					value = RuneLiteAPI.GSON.fromJson(blacklistJson, type);
				} catch (JsonSyntaxException e) {
					log.error("Failed to parse voice blacklist JSON:{}", blacklistJson, e);
					value = List.of();
				}
			}
			else {
				value = List.of();
				log.trace("No existing voice blacklist stored in profile.");
			}
			blacklist.addAll(value);
		}
	}

	public void save() {
		configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_CONFIG_KEY,
			VoiceSettings.toJSON(settings));
		configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.VOICE_BLACKLIST_KEY,
			RuneLiteAPI.GSON.toJson(blacklist));
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
	public void register(@NonNull VoiceID voiceID, @NonNull Gender gender) {
		if (blacklist.contains(voiceID)) {
			disallowed.put(voiceID, gender);
		}
		else {
			allowed.put(voiceID, gender);
			genderCache.add(voiceID, gender);
		}
		log.trace("Registered VoiceID: {}", voiceID);
	}

	/**
	 * When the voice is no longer speakable, unregister the voice.
	 *
	 * @param voiceID Voice
	 *
	 * @see #register(VoiceID, Gender)
	 */
	public void unregister(@NonNull VoiceID voiceID) {
		Gender removedDisallow = disallowed.remove(voiceID);
		Gender removedAllow = allowed.remove(voiceID);
		if (!(removedDisallow != null || removedAllow != null)) {
			log.error("Attempting to unregister VoiceID that was not registered: {}", voiceID);
		}

		genderCache.remove(voiceID);
		log.trace("Unregistered VoiceID: {}", voiceID);
	}

	public void blacklist(@NonNull VoiceID voiceID) {
		Preconditions.checkState(!blacklist.contains(voiceID), "VoiceID already blacklisted: %s", voiceID);

		blacklist.add(voiceID);
		Gender gender = allowed.remove(voiceID);
		if (gender != null) {
			genderCache.remove(voiceID);
			disallowed.put(voiceID, gender);
		} else {
			log.error("VoiceID was is not registered: {}", voiceID);
		}
	}

	public void unblacklist(@NonNull VoiceID voiceID) {
		Preconditions.checkState(blacklist.contains(voiceID), "VoiceID not blacklisted: %s", voiceID);

		blacklist.remove(voiceID);
		Gender gender = disallowed.remove(voiceID);
		if (gender != null) {
			allowed.put(voiceID, gender);
			genderCache.add(voiceID, gender);
		} else {
			log.error("VoiceID was is not registered: {}", voiceID);
		}
	}


	public boolean isBlacklisted(@NonNull VoiceID voiceID) {
		return blacklist.contains(voiceID);
	}

	public boolean speakable(@NonNull VoiceID voiceID) {
		return allowed.containsKey(voiceID);
	}

	@NonNull
	public Optional<VoiceID> get(@NonNull EntityID entityID) {
		return Optional.ofNullable(settings.get(entityID));
	}

	@NonNull
	public VoiceID resolve(@NonNull EntityID entityID) {
		Preconditions.checkState(!allowed.isEmpty(), "No allowed voices.");

		// if there is setting for this entity, use that
		VoiceID voiceID = settings.get(entityID);

		if (voiceID != null && !contains(voiceID)) {
			log.trace("Voice Setting found, but not active: {}", voiceID);
			voiceID = null;
		}

		// no settings, randomize
		if (voiceID == null) {
			log.trace("No voice setting for {}. Randomizing voice.", entityID);
			voiceID = random(entityID);
		}
		else {
			log.trace("Voice setting found for {}: {}", entityID, voiceID);
		}

		log.trace("Resolved VoiceID {}:{}", entityID, voiceID);
		return voiceID;
	}

	public boolean contains(VoiceID voiceID) {
		return allowed.containsKey(voiceID) || disallowed.containsKey(voiceID);
	}

	public boolean isSet(EntityID entityID) {
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
		Preconditions.checkState(!allowed.isEmpty(), "No allowed voices.");

		Gender gender = clientHelper.getGender(eid);

		Set<VoiceID> voiceIDs = genderCache.find(gender);
		if (voiceIDs == null || voiceIDs.isEmpty()) {
			// no voices available for gender
			return fallback();
		}

		int hashCode = eid.hashCode();
		int voice = Math.abs(hashCode) % voiceIDs.size();

		return voiceIDs.stream().skip(voice).findFirst().orElseThrow();
	}

	// Ultimate fallback
	@NonNull
	private VoiceID fallback() {
		Preconditions.checkState(!allowed.isEmpty(), "No allowed voices.");

		long count = allowed.size();

		Optional<VoiceID> first = allowed.keySet().stream().skip((int) (Math.random() * count)).findFirst();
		Preconditions.checkState(first.isPresent(), "Random index overflowed.");
		return first.get();
	}
	//<editor-fold desc="> Get">

}
