package dev.phyce.naturalspeech.tts;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.configs.ModelConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.PiperNotActiveException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.macos.MacUnquarantine;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsCache;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsEngine;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.piper.PiperProcess;
import dev.phyce.naturalspeech.utils.OSValidator;
import dev.phyce.naturalspeech.utils.TextUtil;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {

	//<editor-fold desc="> Properties">
	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";

	private final ConfigManager configManager;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final ClientThread clientThread;
	private final ModelRepository modelRepository;
	private final NaturalSpeechConfig config;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final OkHttpClient httpClient;
	private final Gson gson;

	private Map<String, String> shortenedPhrases;

	private static final String COMMON_ABBREVIATIONS_RESOURCE = "common_abbreviations.txt";

	private final java.util.concurrent.atomic.AtomicInteger dialogGen = new java.util.concurrent.atomic.AtomicInteger(0);
	private final PlaybackGate playbackGate;
	@Getter
	private ModelConfig modelConfig;
	private final Map<String, Piper> pipers = new HashMap<>();
	private volatile ElevenLabsEngine elevenLabsEngine;
	private Filepath elevenLabsCacheDirectory;
	private final List<TextToSpeechListener> textToSpeechListeners = new ArrayList<>();
	@Getter
	private boolean piperStarted = false;
	private boolean isPiperUnquarantined = false;
	//</editor-fold>

	@Inject
	private TextToSpeech(
		ConfigManager configManager,
		ClientThread clientThread,
		ModelRepository modelRepository,
		NaturalSpeechRuntimeConfig runtimeConfig,
		NaturalSpeechConfig config,
		ElevenLabsVoiceRepository elevenLabsVoiceRepository,
		OkHttpClient httpClient,
		Gson gson) {
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.clientThread = clientThread;
		this.modelRepository = modelRepository;
		this.config = config;
		this.elevenLabsVoiceRepository = elevenLabsVoiceRepository;
		this.httpClient = httpClient;
		this.gson = gson;
		this.playbackGate = new PlaybackGate(config::sequentialPlaybackEnabled, config::sequentialPlaybackQueueSize);

		loadModelConfig();
	}

	// <editor-fold desc="> API">
	public void startPiper() {
		if (!isPiperPathValid()) {
			triggerOnPiperInvalid();
			return;
		}

		isPiperUnquarantined = false; // set to false for each launch, in case piper path/files were modified
		piperStarted = false;
		try {
			for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
				try {
					if (modelRepository.hasModelLocal(modelURL.getModelName()) &&
						modelConfig.isModelEnabled(modelURL.getModelName())) {
						ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(modelURL.getModelName());
						startPiperForModel(modelLocal);
						piperStarted = true;
					}
				} catch (IOException e) {
					log.error("Failed to start {}", modelURL.getModelName(), e);
				}
			}
		} catch (RuntimeException e) {
			log.error("Unexpected exception starting text to speech", e);
			return;
		}

		if (piperStarted) {
			triggerOnStart();
		}
	}

	public void stopPiper() {
		piperStarted = false;
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

	public void startAll() {
		if (activePiperProcessCount() == 0) startPiper();
		startElevenLabs();
	}

	public void stopAll() {
		stopPiper();
		stopElevenLabs();
	}

	public void speak(VoiceID voiceID, String text, int distance, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotActiveException {
		speak(voiceID, text, distance, 0, audioQueueName);
	}

	public void speak(VoiceID voiceID, String text, int distance, int volumeBoostPercent, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotActiveException {
		assert distance >= 0;
		try {
			boolean isElevenLabs = voiceID.getEngine() == SpeechEngine.ELEVENLABS;
			if (!isElevenLabs && !modelRepository.hasModelLocal(voiceID.modelName)) {
				throw new ModelLocalUnavailableException(text, voiceID);
			}

			if (!isModelActive(voiceID.getModelName())) {
				throw new PiperNotActiveException(text, voiceID);
			}

			boolean queued = playbackGate.isQueued(audioQueueName);
			if (queued && playbackGate.isBacklogged(pendingAudioCount())) {
				log.debug("Dropping message, playback queue is full. Text:{}", text);
				return;
			}

			int generation = MagicUsernames.DIALOG.equals(audioQueueName) ? dialogGen.get() : -1;
			float volume = getVolumeWithDistance(distance, volumeBoostPercent);

			if (isElevenLabs) {
				ElevenLabsEngine engine = elevenLabsEngine;
				if (engine != null) engine.speak(text, voiceID, volume, audioQueueName, generation);
				return;
			}

			Piper piper = pipers.get(voiceID.modelName);
			if (piper == null) return;

			List<String> fragments = queued ? List.of(text) : splitSentence(text);
			for (String sentence : fragments) {
				piper.speak(sentence, voiceID, volume, audioQueueName, generation);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error loading " + voiceID, e);
		}
	}

	public int pendingAudioCount() {
		int total = 0;
		for (Piper piper : pipers.values()) {
			total += piper.pendingAudioCount();
		}
		ElevenLabsEngine engine = elevenLabsEngine;
		if (engine != null) {
			total += engine.pendingAudioCount();
		}
		return total;
	}

	public String expandShortenedPhrases(String text) {
		return TextUtil.expandShortenedPhrases(text, shortenedPhrases);
	}

	//</editor-fold>

	//<editor-fold desc="> Audio">
//	public float getVolumeWithDistance(int distance) {
//		if (distance <= 1) {
//			return 0;
//		}
//		return -6.0f * (float) (Math.log(distance) / Math.log(2)); // Log base 2
//	}
	public float getVolumeWithDistance(int distance) {
		return getVolumeWithDistance(distance, 0);
	}

	public float getVolumeWithDistance(int distance, int volumeBoostPercent) {
		float volumeWithDistance;
		if (distance <= 1) {
			volumeWithDistance = 0;
		} else {
			volumeWithDistance = -6.0f * (float) (Math.log(distance) / Math.log(2));
		}

		int masterVolumePercentage = PluginHelper.getConfig().masterVolume();
		if (masterVolumePercentage == 0) return -80;

		int effectivePercent = masterVolumePercentage + Math.max(0, volumeBoostPercent);
		float scaleFactor = effectivePercent / 100.0f;

		float minVolume = -35;
		float maxVolume = (float) Math.max(0.0, 20.0 * Math.log10(scaleFactor));

		float scaledVolume = minVolume + (volumeWithDistance - minVolume) * scaleFactor;
		scaledVolume = Math.max(minVolume, Math.min(maxVolume, scaledVolume));

		return scaledVolume;
	}

	public void silenceQueue(String queueName) {
		if (MagicUsernames.DIALOG.equals(queueName)) {
			dialogGen.incrementAndGet();
		}
		for (Piper piper : pipers.values()) {
			piper.silenceQueue(queueName);
		}
		ElevenLabsEngine engine = elevenLabsEngine;
		if (engine != null) {
			engine.silenceQueue(queueName);
		}
	}

	public void clearAllAudioQueues() {
		for (String modelName : pipers.keySet()) {
			pipers.get(modelName).clearQueue();
		}
		ElevenLabsEngine engine = elevenLabsEngine;
		if (engine != null) {
			engine.clearQueue();
		}
	}

	public void clearOtherPlayersAudioQueue(String username) {
		for (Map<String, AudioQueue> namedAudioQueueMap : namedAudioQueueMaps()) {
			namedAudioQueueMap.forEach((audioQueueName, audioQueue) -> {
				if (audioQueueName.equals(MagicUsernames.DIALOG)) return;
				if (audioQueueName.equals(PluginHelper.getLocalPlayerUsername())) return;
				if (audioQueueName.equals(username)) return;
				audioQueue.queue.clear();
			});
		}
	}

	public void clearPlayerAudioQueue(String username) {
		if (username.equals(MagicUsernames.DIALOG)) return;

		for (Map<String, AudioQueue> namedAudioQueueMap : namedAudioQueueMaps()) {
			AudioQueue audioQueue = namedAudioQueueMap.get(username);
			if (audioQueue != null) {
				audioQueue.queue.clear();
			}
		}
	}

	private List<Map<String, AudioQueue>> namedAudioQueueMaps() {
		List<Map<String, AudioQueue>> namedAudioQueueMaps = new ArrayList<>();
		for (Piper piper : pipers.values()) {
			namedAudioQueueMaps.add(piper.getNamedAudioQueueMap());
		}
		ElevenLabsEngine engine = elevenLabsEngine;
		if (engine != null) {
			namedAudioQueueMaps.add(engine.getNamedAudioQueueMap());
		}
		return namedAudioQueueMaps;
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
		} else {
			return piper_file.exists() && piper_file.canExecute() && !piper_file.isDirectory();
		}
	}

	public void startPiperForModel(ModelRepository.ModelLocal modelLocal) throws IOException {
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
			modelLocal,
			runtimeConfig.getPiperPath(),
			modelConfig.getModelProcessCount(modelLocal.getModelName()),
			dialogGen::get,
			playbackGate
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

	public void stopPiperForModel(ModelRepository.ModelLocal modelLocal)
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

	public boolean isModelActive(ModelRepository.ModelLocal modelLocal) {
		return isModelActive(modelLocal.getModelName());
	}

	public boolean isModelActive(String modelName) {
		ElevenLabsModel elevenLabsModel = ElevenLabsModel.fromId(modelName);
		if (elevenLabsModel != null) {
			return isElevenLabsRunning() && elevenLabsVoiceRepository.isModelEnabled(elevenLabsModel);
		}

		Piper piper = pipers.get(modelName);
		return piper != null && piper.countAlive() > 0;
	}

	public boolean isEngineRunning(SpeechEngine engine) {
		if (engine == SpeechEngine.ELEVENLABS) {
			return isElevenLabsRunning() && !elevenLabsVoiceRepository.getEnabledModels().isEmpty();
		}
		return activePiperProcessCount() > 0;
	}

	public boolean isSourceRunning(VoiceSource source) {
		String modelName = source.getModelName();
		return modelName == null ? isEngineRunning(source.getEngine()) : isModelActive(modelName);
	}

	public boolean isAnyEngineRunning() {
		return isEngineRunning(SpeechEngine.PIPER) || isEngineRunning(SpeechEngine.ELEVENLABS);
	}

	@CheckForNull
	public String getVoiceName(VoiceID voiceID) {
		if (voiceID.getEngine() == SpeechEngine.ELEVENLABS) {
			for (ElevenLabsVoice voice : elevenLabsVoiceRepository.getVoices()) {
				if (voice.getId().equals(voiceID.getId())) return voice.getName();
			}
			return null;
		}

		Piper piper = pipers.get(voiceID.getModelName());
		if (piper == null) return null;
		for (ModelRepository.VoiceMetadata voice : piper.getModelLocal().getVoiceMetadata()) {
			if (voice.getPiperVoiceID() == voiceID.getPiperVoiceID()) return voice.getName();
		}
		return null;
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

	//<editor-fold desc="> ElevenLabs">
	public void setElevenLabsCacheDirectory(Filepath cacheDirectory) {
		elevenLabsCacheDirectory = cacheDirectory;
	}

	public void startElevenLabs() {
		if (!elevenLabsVoiceRepository.hasApiKey() || elevenLabsCacheDirectory == null) return;

		if (elevenLabsEngine == null) {
			ElevenLabsCache cache = new ElevenLabsCache(elevenLabsCacheDirectory, config);
			elevenLabsEngine = new ElevenLabsEngine(httpClient, gson, config, cache, playbackGate, dialogGen::get);
			triggerOnElevenLabsStart();
		}
		elevenLabsVoiceRepository.refresh();
	}

	public void stopElevenLabs() {
		ElevenLabsEngine engine = elevenLabsEngine;
		elevenLabsEngine = null;
		if (engine != null) {
			engine.stop();
			triggerOnElevenLabsStop();
		}
	}

	public void reloadElevenLabs() {
		if (elevenLabsVoiceRepository.hasApiKey()) {
			startElevenLabs();
		}
		else {
			stopElevenLabs();
			elevenLabsVoiceRepository.refresh();
		}
	}

	public void refreshElevenLabsVoices() {
		elevenLabsVoiceRepository.refresh();
	}

	public void clearElevenLabsCache() {
		ElevenLabsEngine engine = elevenLabsEngine;
		if (engine != null) {
			engine.clearCache();
		}
	}

	public boolean isElevenLabsStarted() {
		return elevenLabsEngine != null;
	}

	public boolean isElevenLabsRunning() {
		return elevenLabsEngine != null && elevenLabsVoiceRepository.hasVoices();
	}

	private void triggerOnElevenLabsStart() {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onElevenLabsStart();
		}
	}

	private void triggerOnElevenLabsStop() {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onElevenLabsStop();
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
	public void loadShortenedPhrases() {
		shortenedPhrases = new HashMap<>();
		if (config.useCommonAbbreviations()) {
			parsePhrasesInto(readCommonAbbreviationsResource(), shortenedPhrases);
		}
		parsePhrasesInto(config.shortenedPhrases(), shortenedPhrases);
	}

	private static void parsePhrasesInto(String phrases, Map<String, String> out) {
		if (phrases == null || phrases.isEmpty()) return;
		for (String line : phrases.split("\n")) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) out.put(parts[0].trim(), parts[1].trim());
		}
	}

	private static String readCommonAbbreviationsResource() {
		try (java.io.InputStream is = NaturalSpeechPlugin.class.getResourceAsStream(COMMON_ABBREVIATIONS_RESOURCE)) {
			if (is == null) {
				log.warn("Common abbreviations resource not found on classpath: {}", COMMON_ABBREVIATIONS_RESOURCE);
				return "";
			}
			return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			log.error("Failed to read common abbreviations resource", e);
			return "";
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

		default void onElevenLabsStart() {}

		default void onElevenLabsStop() {}
	}
}
