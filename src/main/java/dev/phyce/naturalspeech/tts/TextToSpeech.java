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
import net.runelite.client.config.ConfigManager;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.util.*;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";
	private final ConfigManager configManager;
	private final ModelRepository modelRepository;
	/** Model ShortName -> PiperRunner */
	private final Map<ModelRepository.ModelLocal, Piper> pipers = new HashMap<>();
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private VoiceConfig voiceConfig;

	@Inject
	private TextToSpeech(
			NaturalSpeechRuntimeConfig runtimeConfig,
			ConfigManager configManager,
			ModelRepository modelRepository) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepository = modelRepository;

		loadVoiceConfig(); // throws on err
	}

	public void loadVoiceConfig() throws JsonSyntaxException {
		String voiceSettingsJSON = configManager.getConfiguration(CONFIG_GROUP, "VoiceConfig");

		if (voiceSettingsJSON == null) voiceSettingsJSON = "{}";

		voiceConfig = new VoiceConfig(voiceSettingsJSON);
	}

	public void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, "VoiceConfig", voiceConfig.exportJSON());
	}

	public boolean isPiperForModelRunning(ModelRepository.ModelLocal modelLocal) {
		return pipers.get(modelLocal).countProcessingInstances() > 0;
	}

	public int activePiperInstanceCount() {
		int result = 0;
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			result += model.countProcessingInstances();
		}
		return result;
	}

	/**
	 * Starts Piper for specific ModelLocal
	 * @param modelLocal
	 */
	public void startPiperForModelLocal(ModelRepository.ModelLocal modelLocal) throws LineUnavailableException, IOException {
		// @FIXME Make instanceCount configurable
		Piper piper = new Piper(modelLocal, runtimeConfig.getPiperPath(), 2);

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

	public void shutDownAllPipers() {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			pipers.get(modelLocal).shutDown();
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
			for (String audioQueueName : piper.getAudioQueues().keySet()) {
				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
				if (audioQueueName.equals(PluginHelper.getLocalPlayerUserName())) continue;
				if (audioQueueName.equals(username)) continue;
				piper.getAudioQueues().get(audioQueueName).queue.clear();
			}
		}
	}

	public void clearPlayerAudioQueue(String username) {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			for (String audioQueueName : pipers.get(modelLocal).getAudioQueues().keySet()) {
				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
				if (audioQueueName.equals(username)) pipers.get(modelLocal).getAudioQueues().get(audioQueueName).queue.clear();
			}
		}
	}


	// TODO Unfinished refactor, moved the save-able voice configs to JSON stored in the plugin configurations
//	public VoiceID getVoiceModel(String username) {
//		SpeakerConfiguration config = speakerConfigurations.get(username.toLowerCase());
//		if (config != null) {
//			System.out.println("config MODEL not null for: " + username);
//			System.out.println(config.getModel());
//
//			return config.getModel();
//		}
//
//		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
//			Piper model = pipers.get(modelLocal);
//			return model.getmo();
//		}
//		return "";
//	}
//
//	public int getVoiceID(String username) {
//		SpeakerConfiguration config = speakerConfigurations.get(username.toLowerCase());
//		if (config != null) {
//			System.out.println("config  VOICE ID not null for: " + username);
//			System.out.println(config.getVoiceID());
//			return config.getVoiceID();
//		}
//
//		return TTSItem.calculateVoiceIndex(username);
//	}


}
