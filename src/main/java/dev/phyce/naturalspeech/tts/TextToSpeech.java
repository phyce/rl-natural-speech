package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.VOICE_CONFIG_FILE;
import dev.phyce.naturalspeech.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.exceptions.PiperNotAvailableException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceConfig;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.json.VoiceConfigDatum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.widgets.ComponentID;

import java.io.IOException;
import java.util.*;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";
	private final ConfigManager configManager;
	private final ModelRepository modelRepository;
	//Model ShortName -> PiperRunner
	private final Map<ModelRepository.ModelLocal, Piper> pipers = new HashMap<>();
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private VoiceConfig voiceConfig;

	private final List<PiperLifetimeListener> piperLifetimeListeners = new ArrayList<>();

	@Inject
	private TextToSpeech(
		NaturalSpeechRuntimeConfig runtimeConfig,
		ConfigManager configManager,
		ModelRepository modelRepository, EventBus eventbus) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepository = modelRepository;

		loadVoiceConfig();
	}

	public void loadVoiceConfig() throws JsonSyntaxException {
		// FIXME(Louis): Reading from local file but saving to runtimeConfig right now
		//fetch from config manager
		//if empty, load in the
		voiceConfig = new VoiceConfig(VOICE_CONFIG_FILE);

		VoiceConfig customConfig = runtimeConfig.getCustomVoices();

		if(customConfig != null) {
			for(String key : voiceConfig.player.keySet()) {
				PlayerNameVoiceConfigDatum customDatum = customConfig.player.remove(key);
				if (customDatum != null) voiceConfig.player.put(key, customDatum);
			}

			for(Integer key : voiceConfig.npcID.keySet()) {
				NPCIDVoiceConfigDatum customDatum = customConfig.npcID.remove(key);
				if (customDatum != null) voiceConfig.npcID.put(key, customDatum);
			}

			for(String key : voiceConfig.npcName.keySet()) {
				NPCNameVoiceConfigDatum customDatum = customConfig.npcName.remove(key);
				if (customDatum != null) voiceConfig.npcName.put(key, customDatum);
			}

			voiceConfig.player.putAll(customConfig.player);
			voiceConfig.npcID.putAll(customConfig.npcID);
			voiceConfig.npcName.putAll(customConfig.npcName);
		}
	}

	public void setActorVoiceID(Actor actor, String model, int voiceId) {
		if (actor instanceof NPC) {
			NPC npc = ((NPC) actor);

			NPCIDVoiceConfigDatum voiceConfigDatum = new NPCIDVoiceConfigDatum(
				new VoiceID[]{new VoiceID(model, voiceId)},
				npc.getId()
			);

			voiceConfig.npcID.put(npc.getId(), voiceConfigDatum);
		} else {
			PlayerNameVoiceConfigDatum voiceConfigDatum = new PlayerNameVoiceConfigDatum(
				new VoiceID[]{new VoiceID(model, voiceId)},
				actor.getName()
			);

			voiceConfig.player.put(actor.getName(), voiceConfigDatum);
		}
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, "VoiceConfig", voiceConfig.exportJSON());
	}

	public boolean isPiperForModelRunning(ModelRepository.ModelLocal modelLocal) {
		Piper piper = pipers.get(modelLocal);
		return piper != null && piper.countAlive() > 0;
	}

	public int activePiperInstanceCount() {
		int result = 0;
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			result += model.countAlive();
		}
		return result;
	}

	public void speak(VoiceID voiceID, String text, int distance, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotAvailableException {

		try {
			if (!modelRepository.hasModelLocal(voiceID.modelName)) {
				throw new ModelLocalUnavailableException(text, voiceID);
			}

			ModelRepository.ModelLocal modelLocal = modelRepository.getModelLocal(voiceID);

			// Check if the piper for the model is running, if not, throw
			if (!isPiperForModelRunning(modelLocal)) {
				throw new PiperNotAvailableException(text, voiceID);
			}

			List<String> fragments = splitSentence(text);
			for (String sentence : fragments) {
				pipers.get(modelLocal).speak(sentence, voiceID, getVolumeWithDistance(distance), audioQueueName);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error loading " + voiceID, e);
		}
	}

	public void speak(ChatMessage message, int distance)
		throws
			ModelLocalUnavailableException,
			PiperNotAvailableException {
		VoiceID voiceId = getModelAndVoiceFromChatMessage(message)[0];
		speak(voiceId, message.getMessage().toLowerCase(), distance, message.getName());
	}

//	public void speak(NPC npc, String npcName) {
//
//		VoiceID voiceId = getModelAndVoiceFromNPC(npc)[0];
//		speak(voiceId, npc.getOverheadText(), distance, npcName);
//	}

	public void speak(int npcId, String npcName, int distance, String message) {
		VoiceID voiceId = getModelAndVoiceFromNPCId(npcId, npcName)[0];
		speak(voiceId, message, distance, npcName);
	}

	public void speak(String message) {
		String username = PluginHelper.getClientUsername();
		VoiceID voiceId = getModelAndVoiceFromUsername(username)[0];
		speak(voiceId, message.toLowerCase(), 0, username);
	}

	public float getVolumeWithDistance(int distance) {
		if (distance <= 1) {
			return 0;
		}
		return -6.0f * (float) (Math.log(distance) / Math.log(2)); // Log base 2
	}

	/**
	 * Starts Piper for specific ModelLocal
	 */
	public void startPiperForModelLocal(ModelRepository.ModelLocal modelLocal) throws IOException {
		if (pipers.get(modelLocal) != null) {
			log.warn("Starting piper for {} when there are already pipers running for the model.",
				modelLocal.getModelName());
			Piper duplicate = pipers.remove(modelLocal);
			duplicate.stop();
			triggerOnPiperExit(duplicate);
		}

		// @FIXME Make instanceCount configurable
		Piper piper = Piper.start(modelLocal, runtimeConfig.getPiperPath(), 2);

		pipers.put(modelLocal, piper);

		triggerOnPiperStart(piper);
	}

	public void stopPiperForModelLocal(ModelRepository.ModelLocal modelLocal)
		throws PiperNotAvailableException {
		Piper piper;
		if ((piper = pipers.remove(modelLocal)) != null) {
			piper.stop();
			triggerOnPiperExit(piper);
		}
		else {
			throw new RuntimeException("Removing piper for {}, but there are no pipers running that model");
		}
	}

	public void stopAllPipers() {
		for (Piper piper : pipers.values()) {
			piper.stop();
			triggerOnPiperExit(piper);
		}
		pipers.clear();
	}

	/**
	 * Clears all, no more audio after the current ones are finished
	 */
	public void clearAllAudioQueues() {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			pipers.get(modelLocal).clearQueue();
		}
	}

	public void clearOtherPlayersAudioQueue(String username) {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper piper = pipers.get(modelLocal);
			for (String audioQueueName : piper.getNamedAudioQueueMap().keySet()) {
				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
				if (audioQueueName.equals(PluginHelper.getClientUsername())) continue;
				if (audioQueueName.equals(username)) continue;
				piper.getNamedAudioQueueMap().get(audioQueueName).queue.clear();
			}
		}
	}

	public void clearPlayerAudioQueue(String username) {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			for (String audioQueueName : pipers.get(modelLocal).getNamedAudioQueueMap().keySet()) {
				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
				if (audioQueueName.equals(username)) {
					pipers.get(modelLocal).getNamedAudioQueueMap().get(audioQueueName).queue.clear();
				}
			}
		}
	}

	public VoiceID[] getModelAndVoiceFromChatMessage(ChatMessage message) {
		VoiceID []results;
		if(message.getName().equals(PluginHelper.getClientUsername())) results = voiceConfig.getPlayerVoiceIDs(message.getName());
		else switch(message.getType()) {
//			case DIALOG:
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
				results = voiceConfig.getNpcVoiceIDs(message.getName());
				break;
			default:
				results = voiceConfig.getPlayerVoiceIDs(message.getName());
				break;
		}
		if(results == null) {
			for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
				Piper model = pipers.get(modelLocal);

				if(message.getType() == ChatMessageType.PUBLICCHAT) {
					int gender = PluginHelper.getFromUsername(message.getName()).getPlayerComposition().getGender();
					results = new VoiceID[]{model.getModelLocal().calculateGenderedVoice(message.getName(), gender)};
				}
				else results = new VoiceID[]{model.getModelLocal().calculateVoice(message.getName())};
			}
		}
		return results;
	}

	public VoiceID[] getModelAndVoiceFromNPCId(int npcId, String npcName) {
		VoiceID []results = {};
		results = voiceConfig.getNpcVoiceIDs(npcId);

		if(results == null) results = voiceConfig.getNpcVoiceIDs(npcName);

		if(results == null) for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			results = new VoiceID[]{model.getModelLocal().calculateVoice(npcName)};
		}

		return results;
	}

	public VoiceID[] getModelAndVoiceFromNPC(NPC npc) {
		VoiceID []results = {};
		//TODO get custom VoiceID results first
		results = voiceConfig.getNpcVoiceIDs(npc.getName());

		if(results == null) for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			results = new VoiceID[]{model.getModelLocal().calculateVoice(npc.getName())};
		}

		return results;
	}

	public VoiceID[] getModelAndVoiceFromUsername(String username) {
		VoiceID []results = {};
		results = voiceConfig.getPlayerVoiceIDs(username);

		if(results == null) for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			results = new VoiceID[]{model.getModelLocal().calculateVoice(username)};
		}

		return results;
	}

	public void triggerOnPiperStart(Piper piper) {
		for (PiperLifetimeListener listener : piperLifetimeListeners) {
			listener.onPiperStart(piper);
		}
	}

	public void triggerOnPiperExit(Piper piper) {
		for (PiperLifetimeListener listener : piperLifetimeListeners) {
			listener.onPiperExit(piper);
		}
	}

	public void addPiperLifetimeListener(PiperLifetimeListener listener) {
		piperLifetimeListeners.add(listener);
	}

	public void removePiperLifetimeListener(PiperLifetimeListener listener) {
		piperLifetimeListeners.remove(listener);
	}

	public interface PiperLifetimeListener {
		default void onPiperStart(Piper piper) {}

		default void onPiperExit(Piper piper) {}
	}
}
