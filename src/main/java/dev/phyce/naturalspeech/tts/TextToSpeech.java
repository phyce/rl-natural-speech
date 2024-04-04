package dev.phyce.naturalspeech.tts;

import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.configs.ModelConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.configs.json.abbreviations.AbbreviationEntryDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.PiperNotActiveException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.macos.MacUnquarantine;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.piper.PiperProcess;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4Repository;
import dev.phyce.naturalspeech.tts.wsapi4.SpeechAPI4;
import dev.phyce.naturalspeech.utils.OSValidator;
import dev.phyce.naturalspeech.utils.TextUtil;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.RuneLiteAPI;

// Renamed from TTSManager
@Slf4j
@PluginSingleton
public class TextToSpeech {

	//<editor-fold desc="> Properties">
	public final static String ABBREVIATION_FILE = "abbreviations.json";
	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";

	private final ConfigManager configManager;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final ClientThread clientThread;
	private final PiperRepository piperRepository;
	private final SAPI4Repository sapi4Repository;
	private final NaturalSpeechConfig config;
	private final AudioEngine audioEngine;

	private Map<String, String> abbreviations;
	@Getter
	private ModelConfig modelConfig;
	private final Map<String, Piper> pipers = new HashMap<>();

	// TODO(Louis) mid-refactor.
	// "microsoft" does not denote any specific models and has no lifetime
	// The VoiceID::ids are the actual models and can be available or not.
	// We want "microsoft:sam", not "sam:0"
	// A more generalized approach can be done at a later time.
	public static final String SAPI4ModelName = "microsoft";
	private final Map<String, SpeechAPI4> sapi4s = new HashMap<>();

	private final List<TextToSpeechListener> textToSpeechListeners = new ArrayList<>();
	@Getter
	private boolean started = false;
	private boolean isPiperUnquarantined = false;
	//</editor-fold>

	@Inject
	private TextToSpeech(
		ConfigManager configManager,
		ClientThread clientThread,
		PiperRepository piperRepository,
		NaturalSpeechRuntimeConfig runtimeConfig,
		SAPI4Repository sapi4Repository,
		NaturalSpeechConfig config,
		AudioEngine audioEngine
	) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.clientThread = clientThread;
		this.piperRepository = piperRepository;
		this.sapi4Repository = sapi4Repository;
		this.config = config;
		this.audioEngine = audioEngine;

		loadModelConfig();

		// SAPI4 models don't have lifecycles and does not need to be cleared on stop
		{
			List<String> modelNames = sapi4Repository.getModels();
			if (modelNames != null) {
				for (String modelName : modelNames) {
					sapi4s.put(modelName, SpeechAPI4.start(audioEngine, modelName, runtimeConfig.getSAPI4Path()));
				}
			}
		}
	}

	// <editor-fold desc="> API">
	public void start() {
		if (!isPiperPathValid()) {
			triggerOnPiperInvalid();
			return;
		}

		isPiperUnquarantined = false; // set to false for each launch, in case piper path/files were modified
		started = false;
		try {
			for (PiperRepository.ModelURL modelURL : piperRepository.getModelURLS()) {
				try {
					if (piperRepository.hasModelLocal(modelURL.getModelName()) &&
						modelConfig.isModelEnabled(modelURL.getModelName())) {
						PiperRepository.ModelLocal modelLocal = piperRepository.loadModelLocal(modelURL.getModelName());
						startPiperForModel(modelLocal);
						started = true; // if even a single piper started successful, then it's running.
					}
				} catch (IOException e) {
					log.error("Failed to start {}", modelURL.getModelName(), e);
				}
			}
		} catch (RuntimeException e) {
			log.error("Unexpected exception starting text to speech", e);
			return;
		}

		if (started) {
			triggerOnStart();
		}
	}

	public void stop() {
		started = false;
		for (Piper piper : pipers.values()) {
			try {
				piper.stop();
			} catch (RuntimeException e) {
				log.error("Error stopping piper: {}", piper, e);
			}
			triggerOnPiperExit(piper);
		}
		pipers.clear();

		triggerOnStop();
	}

	public void speak(VoiceID voiceID, String text, float volumeDb, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotActiveException {

		// Piper should be guaranteed to be present due to checks above
		Object ttsEngine = resolveEngine(voiceID);

		if (ttsEngine instanceof Piper) {
			try {
				if (!piperRepository.hasModelLocal(voiceID.modelName)) {
					// FIXME(Louis): Implement SAPI Models into ModelRepository
					throw new ModelLocalUnavailableException(text, voiceID);
				}

				if (!isPiperModelActive(voiceID.getModelName())) {
					throw new PiperNotActiveException(text, voiceID);
				}
				Piper piper = (Piper) ttsEngine;
				piper.speak(text, voiceID, volumeDb, audioQueueName);
			} catch (IOException e) {
				throw new RuntimeException("Error loading " + voiceID, e);
			}
		}
		else if (ttsEngine instanceof SpeechAPI4) {
			SpeechAPI4 sapi = (SpeechAPI4) ttsEngine;
			sapi.speak(text, voiceID, volumeDb, audioQueueName);
		}
		else {
			log.info("Model for VoiceID is unavailable {}", voiceID);
		}
	}

	// FIXME(Louis): Returns Object right now, eventually we can return a TTSEngine interface
	public Object resolveEngine(VoiceID voiceID) {

		if (voiceID.modelName.equals(SAPI4ModelName)) {
			return sapi4s.get(voiceID.getId());
		}
		else {
			return pipers.get(voiceID.modelName);
		}
	}

	public String expandAbbreviations(String text) {
		return TextUtil.expandAbbreviations(text, abbreviations);
	}
	//</editor-fold>

	//<editor-fold desc="> Audio">
	public void clearAllAudioQueues() {
		for (String modelName : pipers.keySet()) {
			pipers.get(modelName).clearQueue();
		}
	}

	public void clearOtherPlayersAudioQueue(String username) {
//		for (String modelName : pipers.keySet()) {
//			Piper piper = pipers.get(modelName);
//			for (String audioQueueName : piper.getNamedAudioQueueMap().keySet()) {
//				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
//				if (audioQueueName.equals(PluginHelper.getLocalPlayerUsername())) continue;
//				if (audioQueueName.equals(username)) continue;
//				piper.getNamedAudioQueueMap().get(audioQueueName).queue.clear();
//			}
//		}
	}

	public void clearPlayerAudioQueue(String username) {
//		for (String modelName : pipers.keySet()) {
//			Piper piper = pipers.get(modelName);
//			for (String audioQueueName : piper.getNamedAudioQueueMap().keySet()) {
//				// Don't clear dialogue
//				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
//
//				if (audioQueueName.equals(username)) {
//					piper.getNamedAudioQueueMap().get(audioQueueName).queue.clear();
//				}
//			}
//		}
	}
	//</editor-fold>

	//<editor-fold desc="> Piper">

	/**
	 * Starts Piper for specific ModelLocal
	 */
	public boolean isPiperPathValid() {
		File piper_file = runtimeConfig.getPiperPath().toFile();

		if (OSValidator.IS_WINDOWS) {
			String filename = piper_file.getName();
			// naive canExecute check for windows, 99.99% of humans use .exe extension for executables on Windows
			return filename.endsWith(".exe") && piper_file.exists() && !piper_file.isDirectory();
		}
		else {
			return piper_file.exists() && piper_file.canExecute() && !piper_file.isDirectory();
		}
	}

	public void startPiperForModel(PiperRepository.ModelLocal modelLocal) throws IOException {
		if (pipers.get(modelLocal.getModelName()) != null) {
			log.warn("Starting piper for {} when there are already pipers running for the model.",
				modelLocal.getModelName());
			Piper duplicate = pipers.remove(modelLocal.getModelName());
			duplicate.stop();
			triggerOnPiperExit(duplicate);
		}

		if (!isPiperUnquarantined && OSValidator.IS_MAC) {
			isPiperUnquarantined = MacUnquarantine.Unquarantine(runtimeConfig.getPiperPath());
		}

		Piper piper = Piper.start(
			audioEngine,
			modelLocal,
			runtimeConfig.getPiperPath(),
			modelConfig.getModelProcessCount(modelLocal.getModelName())
		);

		// Careful, PiperProcess listeners are not called on the client thread
		piper.addPiperListener(
			new Piper.PiperProcessLifetimeListener() {
				@Override
				public void onPiperProcessExit(PiperProcess process) {
					clientThread.invokeLater(() -> triggerOnPiperExit(piper));
				}
			}
		);

		pipers.put(modelLocal.getModelName(), piper);

		triggerOnPiperStart(piper);
	}

	public void stopPiperForModel(PiperRepository.ModelLocal modelLocal)
		throws PiperNotActiveException {
		Piper piper;
		if ((piper = pipers.remove(modelLocal.getModelName())) != null) {
			piper.stop();
			//			triggerOnPiperExit(piper);
		}
		else {
			throw new RuntimeException("Removing piper for {}, but there are no pipers running that model");
		}
	}

	public int activePiperProcessCount() {
		int result = 0;
		for (String modelName : pipers.keySet()) {
			Piper model = pipers.get(modelName);
			result += model.countAlive();
		}
		return result;
	}

	public boolean isPiperModelActive(String modelName) {
		Piper piper = pipers.get(modelName);
		return piper != null && piper.countAlive() > 0;
	}

	public boolean isModelActive(VoiceID voiceID) {

		// FIXME(Louis): Double check later
		if (voiceID.modelName.equals(SAPI4ModelName)) {
			return sapi4s.containsKey(voiceID.getId());
		}

		return isPiperModelActive(voiceID.getModelName());
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

	private void triggerOnPiperInvalid() {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onPiperInvalid();
		}
	}
	//</editor-fold>

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

	// In method so we can load again when user changes config
	public void loadAbbreviations() {
		URL resourceUrl = Objects.requireNonNull(NaturalSpeechPlugin.class.getResource(ABBREVIATION_FILE));
		String jsonString;
		try {jsonString = Resources.toString(resourceUrl, StandardCharsets.UTF_8);} catch (IOException e) {
			throw new RuntimeException(e);
		}

		abbreviations = new HashMap<>();

		if (config.useCommonAbbreviations()) {
			try {
				if (jsonString != null) {
					Type listOfAbbreviationEntryDatumType = new TypeToken<AbbreviationEntryDatum[]>() {}.getType();
					AbbreviationEntryDatum[] data =
						RuneLiteAPI.GSON.fromJson(jsonString, listOfAbbreviationEntryDatumType);

					for (AbbreviationEntryDatum entry : data) {abbreviations.put(entry.acronym, entry.sentence);}
				}
			} catch (JsonSyntaxException e) {throw new RuntimeException(e);}
		}

		String phrases = config.customAbbreviations();
		String[] lines = phrases.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) abbreviations.put(parts[0].trim(), parts[1].trim());
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

		default void onPiperInvalid() {}

		default void onStart() {}

		default void onStop() {}
	}
}
