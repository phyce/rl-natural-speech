package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.VOICE_CONFIG_FILE;
import dev.phyce.naturalspeech.configs.ModelConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.exceptions.PiperNotAvailableException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;

import java.io.IOException;
import java.util.*;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;
import net.runelite.client.eventbus.EventBus;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {
	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";
	private final ConfigManager configManager;
	private final ModelRepository modelRepository;
	/**
	 * Model ShortName -> PiperRunner
	 */
	private final Map<ModelRepository.ModelLocal, Piper> pipers = new HashMap<>();
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private VoiceConfig voiceConfig;

	@Getter
	private ModelConfig modelConfig;
	private final List<TextToSpeechListener> textToSpeechListeners = new ArrayList<>();

	@Getter
	private boolean started = false;

	@Inject
	private TextToSpeech(
		NaturalSpeechRuntimeConfig runtimeConfig,
		ConfigManager configManager,
		NaturalSpeechPlugin plugin, EventBus eventbus) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepository = plugin.getModelRepository();

		loadVoiceConfig(); // throws on err
		loadModelConfig();
	}

	public void start() {
		started = true;
		for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
			try {
				if (modelRepository.hasModelLocal(modelURL.getModelName()) &&
					modelConfig.isModelEnabled(modelURL.getModelName())) {
					ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(modelURL.getModelName());
					startPiperForModelLocal(modelLocal);
				}
			} catch (IOException e) {
				log.error("Failed to start {}", modelURL.getModelName(), e);
			}
		}
		triggerOnStart();
	}

	public void stop() {
		started = false;
		for (Piper piper : pipers.values()) {
			piper.stop();
			triggerOnPiperExit(piper);
		}
		pipers.clear();
		triggerOnStop();
	}

	public void loadVoiceConfig() throws JsonSyntaxException {
		// FIXME(Louis): Reading from local file but saving to runtimeConfig right now
		//fetch from config manager
		//if empty, load in the
		voiceConfig = new VoiceConfig(VOICE_CONFIG_FILE);

		VoiceConfig customConfig = null;
		//fetch from config manager
		String json = configManager.getConfiguration(CONFIG_GROUP, VOICE_CONFIG_FILE);
		if (json != null) {
			customConfig = new VoiceConfig(json);
		}

		if (customConfig != null) {
			for (String key : voiceConfig.player.keySet()) {
				PlayerNameVoiceConfigDatum customDatum = customConfig.player.remove(key);
				if (customDatum != null) voiceConfig.player.put(key, customDatum);
			}

			for (Integer key : voiceConfig.npcID.keySet()) {
				NPCIDVoiceConfigDatum customDatum = customConfig.npcID.remove(key);
				if (customDatum != null) voiceConfig.npcID.put(key, customDatum);
			}

			for (String key : voiceConfig.npcName.keySet()) {
				NPCNameVoiceConfigDatum customDatum = customConfig.npcName.remove(key);
				if (customDatum != null) voiceConfig.npcName.put(key, customDatum);
			}

			voiceConfig.player.putAll(customConfig.player);
			voiceConfig.npcID.putAll(customConfig.npcID);
			voiceConfig.npcName.putAll(customConfig.npcName);
		}
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, "VoiceConfig", voiceConfig.toJson());
	}

	public void loadModelConfig() {
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG);

		// no existing configs
		if (json == null) {
			// default text to speech config with libritts
			ModelConfigDatum datum = new ModelConfigDatum();
			datum.getPiperConfigData().add(new PiperConfigDatum("libritts", true, 1));
			this.modelConfig = ModelConfig.fromDatum(datum);
		}
		else { // has existing config, just load the json
			this.modelConfig = ModelConfig.fromJson(json);
		}
	}

	public void saveModelConfig() {
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG, modelConfig.toJson());
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

			ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(voiceID);

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

	public void speak(NPC npc, String actorName) {
		int distance = PluginHelper.getNPCDistance(npc);
		VoiceID voiceId = getModelAndVoiceFromNPC(npc)[0];
		speak(voiceId, npc.getOverheadText(), distance, actorName);
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

		Piper piper = Piper.start(
			modelLocal,
			runtimeConfig.getPiperPath(),
			modelConfig.getModelProcessCount(modelLocal.getModelName())
		);

		piper.addPiperListener(
			new Piper.PiperProcessLifetimeListener() {
				@Override
				public void onPiperProcessExit(PiperProcess process) {
					triggerOnPiperExit(piper);
				}
			}
		);

		pipers.put(modelLocal, piper);

		triggerOnPiperStart(piper);
	}

	public void stopPiperForModelLocal(ModelRepository.ModelLocal modelLocal)
		throws PiperNotAvailableException {
		Piper piper;
		if ((piper = pipers.remove(modelLocal)) != null) {
			piper.stop();
//			triggerOnPiperExit(piper);
		}
		else {
			throw new RuntimeException("Removing piper for {}, but there are no pipers running that model");
		}
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
		VoiceID[] results = {};
		if (message.getName().equals(PluginHelper.getClientUsername())) {
			results = voiceConfig.getPlayerVoiceIDs(message.getName());
		}
		else {
			switch (message.getType()) {
				case DIALOG:
				case WELCOME:
				case GAMEMESSAGE:
				case CONSOLE:
					//TODO add way to find out NPC ID
					results = voiceConfig.getNpcVoiceIDs(message.getName());
					break;
				default:
					results = voiceConfig.getPlayerVoiceIDs(message.getName());
					break;
			}
		}
		if (results == null) {
			for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
				Piper model = pipers.get(modelLocal);

				if (message.getType() == ChatMessageType.PUBLICCHAT) {
					int gender = PluginHelper.getFromUsername(message.getName()).getPlayerComposition().getGender();
					results = new VoiceID[] {model.getModelLocal().calculateGenderedVoice(message.getName(), gender)};
				}
				else {results = new VoiceID[] {model.getModelLocal().calculateVoice(message.getName())};}
			}
		}
		return results;
	}

	public VoiceID[] getModelAndVoiceFromNPC(NPC npc) {
		VoiceID[] results = {};
		//TODO get custom VoiceID results first
		results = voiceConfig.getNpcVoiceIDs(npc.getName());

		if (results == null) {
			for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
				Piper model = pipers.get(modelLocal);
				results = new VoiceID[] {model.getModelLocal().calculateVoice(npc.getName())};
			}
		}

		return results;
	}

	public void triggerOnPiperStart(Piper piper) {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onPiperStart(piper);
		}
	}

	public void triggerOnPiperExit(Piper piper) {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onPiperExit(piper);
		}
	}

	public void triggerOnStart() {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onStart();
		}
	}

	public void triggerOnStop() {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onStop();
		}
	}

	public void addTextToSpeechListener(TextToSpeechListener listener) {
		textToSpeechListeners.add(listener);
	}

	public void removeTextToSpeechListener(TextToSpeechListener listener) {
		textToSpeechListeners.remove(listener);
	}

	public interface TextToSpeechListener {
		default void onPiperStart(Piper piper) {}

		default void onPiperExit(Piper piper) {}

		default void onStart() {}

		default void onStop() {}
	}
}
