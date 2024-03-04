package dev.phyce.naturalspeech.tts;

import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.RuntimeConfig;
import dev.phyce.naturalspeech.common.PlayerCommon;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceConfig;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.phyce.naturalspeech.Settings.CONFIG_GROUP;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {

	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";
	private static final Pattern sentenceSplitter = Pattern.compile("(?<=[.!?,])\\s+|(?<=[.!?,])$");
	private final ConfigManager configManager;
	private final ModelRepository modelRepository;
	/**
	 * Model ShortName -> PiperRunner
	 */
	private final Map<ModelRepository.ModelLocal, Piper> pipers = new HashMap<>();
	private final RuntimeConfig runtimeConfig;
	private VoiceConfig voiceConfig;
	// FIXME Not implemented

	@Inject
	private TextToSpeech(
			RuntimeConfig runtimeConfig,
			ConfigManager configManager,
			ModelRepository modelRepository) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepository = modelRepository;

		loadVoiceConfig(); // throws on err
	}

	public static List<String> splitSentence(String sentence) {

		List<String> fragments = Arrays.stream(sentenceSplitter.split(sentence))
				.filter(s -> !s.isBlank()) // remove blanks
				.map(String::trim) // trim spaces
				.collect(Collectors.toList());

		// add period to the last segment
		if (fragments.size() > 1) {
			fragments.set(fragments.size() - 1, fragments.get(fragments.size() - 1) + ".");
		}

		return fragments;
	}

	private void loadVoiceConfig() throws JsonSyntaxException {
		String voiceSettingsJSON = configManager.getConfiguration(CONFIG_GROUP, "VoiceConfig");
		if (voiceSettingsJSON == null) {
			voiceSettingsJSON = "{}"; //
		}
		voiceConfig = new VoiceConfig(voiceSettingsJSON);
	}

	private void saveVoiceConfig() {
		configManager.setConfiguration(CONFIG_GROUP, "VoiceConfig", voiceConfig.exportJSON());
	}

	public boolean isPiperForModelRunning(ModelRepository.ModelLocal modelLocal) {
		return pipers.get(modelLocal).countRunningInstances() > 0;
	}

	public boolean isAnyPiperRunning() {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			if (model.countRunningInstances() > 0) return true;
		}
		return false;
	}

	public void startPiperForModelLocal(ModelRepository.ModelLocal modelLocal) {

		Piper piper = new Piper(modelLocal, runtimeConfig.getPiperPath(), 2);

		pipers.put(modelLocal, piper);
	}

	public void speak(VoiceID voiceID, String text, int distance, String audioQueueName) throws ModelLocalUnavailableException {

		try {
			if (!modelRepository.hasModelLocal(voiceID.modelName)) {
				throw new ModelLocalUnavailableException(voiceID);
			}

			ModelRepository.ModelLocal modelLocal = modelRepository.getModelLocal(voiceID);

			if (!isPiperForModelRunning(modelLocal)) {
				throw new ModelLocalUnavailableException(voiceID);
			}

			List<String> fragments = splitSentence(text);
			for (String sentence : fragments) {
				pipers.get(modelLocal).speak(sentence, voiceID, getVolumeWithDistance(distance), audioQueueName);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public float getVolumeWithDistance(int distance) {
		if (distance <= 1) {
			return 0;
		}
		return -6.0f * (float) (Math.log(distance) / Math.log(2)); // Log base 2
	}



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

	public void shutDownAllPipers() {
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			pipers.get(modelLocal).shutDown();
		}
	}

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
				if (audioQueueName.equals(PlayerCommon.getUsername())) continue;
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
}
