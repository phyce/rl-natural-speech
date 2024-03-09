package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.exceptions.PiperNotAvailableException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceConfig;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.extern.slf4j.Slf4j;
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
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";
	private final ConfigManager configManager;
	private final ModelRepository modelRepository;
	/**
	 * Model ShortName -> PiperRunner
	 */
	private final Map<ModelRepository.ModelLocal, Piper> pipers = new HashMap<>();
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private VoiceConfig voiceConfig;
	private final EventBus eventbus;

	@Inject
	private TextToSpeech(
		NaturalSpeechRuntimeConfig runtimeConfig,
		ConfigManager configManager,
		ModelRepository modelRepository, EventBus eventbus) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepository = modelRepository;
		this.eventbus = eventbus;

		loadVoiceConfig(); // throws on err
	}

	public void loadVoiceConfig() throws JsonSyntaxException {
		// FIXME(Louis): Reading from local file but saving to runtimeConfig right now
		String voiceSettingsJSON = "./speaker_config.json";
		voiceConfig = new VoiceConfig(voiceSettingsJSON);


		//		//String voiceSettingsJSON = configManager.getConfiguration(CONFIG_GROUP, "VoiceConfig");
		//		try {
		//			if (!Files.exists(Paths.get(voiceSettingsJSON))) {
		//				saveSpeakerConfigurations(new HashMap<String, SpeakerConfiguration>());
		//			}
		//			try (FileReader reader = new FileReader(speakerConfig)) {
		//				Type listType = new TypeToken<List<SpeakerConfiguration>>() {}.getType();
		//				List<SpeakerConfiguration> configs = new Gson().fromJson(reader, listType);
		//				if (configs != null) {
		//					configs.forEach(config -> speakerConfigurations.put(config.getName(), config));
		//				}
		//			}
		//		} catch (Exception e) {
		//			e.printStackTrace();
		//		}
		////		if (voiceSettingsJSON == null) voiceSettingsJSON = "{}";
		//
		//		voiceConfig = new VoiceConfig(voiceSettingsJSON);
	}

	//	private void saveSpeakerConfigurations(HashMap<String, SpeakerConfiguration> configs) {
	//		try (FileWriter writer = new FileWriter(speakerConfig)) {
	//			new Gson().toJson(configs, writer);
	//		} catch (Exception e) {
	//			e.printStackTrace();
	//		}
	//	}
	//	public void saveSpeakerConfigurations() {
	//		saveSpeakerConfigurations(speakerConfigurations);
	//	}


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

	/**
	 * Starts Piper for specific ModelLocal
	 */
	public void startPiperForModelLocal(ModelRepository.ModelLocal modelLocal) throws IOException {
		if (pipers.get(modelLocal) != null) {
			pipers.remove(modelLocal).stopAll();
		}

		// @FIXME Make instanceCount configurable
		Piper piper = Piper.start(modelLocal, runtimeConfig.getPiperPath(), 2);

		pipers.put(modelLocal, piper);
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

	// Extracted distance volume algorithm from AudioPlayer
	public float getVolumeWithDistance(int distance) {
		if (distance <= 1) {
			return 0;
		}
		return -6.0f * (float) (Math.log(distance) / Math.log(2)); // Log base 2
	}

	public void stopAllPipers() {
		for (Piper piper : pipers.values()) {
			piper.stopAll();
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
		VoiceID[] results = {};
		if (message.getName().equals(PluginHelper.getClientUsername())) {
			results = voiceConfig.getPlayerVoiceIDs(message.getName());
		}
		else {
			switch (message.getType()) {
				case DIALOG:
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
				results = new VoiceID[] {model.getModelLocal().calculateVoice(message.getName())};
			}
		}
		return results;
	}
}
