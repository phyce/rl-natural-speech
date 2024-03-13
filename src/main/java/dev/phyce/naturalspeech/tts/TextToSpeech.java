package dev.phyce.naturalspeech.tts;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.ModelConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.configs.VoiceConfig;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.PiperNotAvailableException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {

	//<editor-fold desc="> Properties">
	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";
	private final ConfigManager configManager;
	private final ModelRepository modelRepository;
	//Model ShortName -> PiperRunner
	private final Map<ModelRepository.ModelLocal, Piper> pipers = new HashMap<>();
	private final NaturalSpeechRuntimeConfig runtimeConfig;

	@Getter
	private ModelConfig modelConfig;
	private final List<TextToSpeechListener> textToSpeechListeners = new ArrayList<>();
	@Getter
	private boolean started = false;
	//</editor-fold>

	@Inject
	private TextToSpeech(
		NaturalSpeechRuntimeConfig runtimeConfig,
		ConfigManager configManager,
		NaturalSpeechPlugin plugin, EventBus eventbus) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepository = plugin.getModelRepository();

		loadModelConfig();
	}

	public void speak(VoiceID voiceID, String text, int distance, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotAvailableException {

		try {
			if (!modelRepository.hasModelLocal(voiceID.modelName)) {
				throw new ModelLocalUnavailableException(text, voiceID);
			}

			ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(voiceID);

			// Check if the piper for the model is running, if not, throw
			if (!isPiperForModelActive(modelLocal)) {
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

	//</editor-fold>

	//<editor-fold desc="> Voice">

	//</editor-fold>

	//<editor-fold desc="> Audio">
	public float getVolumeWithDistance(int distance) {
		if (distance <= 1) {
			return 0;
		}
		return -6.0f * (float) (Math.log(distance) / Math.log(2)); // Log base 2
	}

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
				if (audioQueueName.equals(PluginHelper.getLocalPlayerUsername())) continue;
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
	//</editor-fold>

	//<editor-fold desc="> Piper">

	/**
	 * Starts Piper for specific ModelLocal
	 */
	public void startPiperForModel(ModelRepository.ModelLocal modelLocal) throws IOException {
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

	public void stopPiperForModel(ModelRepository.ModelLocal modelLocal)
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

	public int activePiperProcessCount() {
		int result = 0;
		for (ModelRepository.ModelLocal modelLocal : pipers.keySet()) {
			Piper model = pipers.get(modelLocal);
			result += model.countAlive();
		}
		return result;
	}

	public boolean isPiperForModelActive(ModelRepository.ModelLocal modelLocal) {
		Piper piper = pipers.get(modelLocal);
		return piper != null && piper.countAlive() > 0;
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

	public Set<ModelRepository.ModelLocal> getActiveModels() {
		return pipers.keySet();
	}
	//</editor-fold>

	public void start() {
		started = true;
		for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
			try {
				if (modelRepository.hasModelLocal(modelURL.getModelName()) &&
					modelConfig.isModelEnabled(modelURL.getModelName())) {
					ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(modelURL.getModelName());
					startPiperForModel(modelLocal);
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
