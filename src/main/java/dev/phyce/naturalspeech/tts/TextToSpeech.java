package dev.phyce.naturalspeech.tts;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.configs.ModelConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.PiperNotActiveException;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.macos.MacUnquarantine;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.piper.PiperProcess;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabs;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsEngine;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeechEngine;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeech;
import dev.phyce.naturalspeech.utils.OSValidator;
import dev.phyce.naturalspeech.utils.TextUtil;
import static dev.phyce.naturalspeech.utils.TextUtil.splitSentence;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

// Renamed from TTSManager
@Slf4j
@Singleton
public class TextToSpeech {

	//<editor-fold desc="> Properties">
	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";

	private final ConfigManager configManager;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final ClientThread clientThread;
	private final ModelRepository modelRepository;
	private final NaturalSpeechConfig config;

	private Map<String, String> shortenedPhrases;

	private static final String COMMON_ABBREVIATIONS_RESOURCE = "common_abbreviations.txt";

	private final java.util.concurrent.atomic.AtomicInteger dialogGen = new java.util.concurrent.atomic.AtomicInteger(0);
	private final PlaybackGate playbackGate;
	@Getter
	private ModelConfig modelConfig;
	private final Map<String, Piper> pipers = new HashMap<>();
	@Getter
	private NativeSpeechEngine nativeSpeechEngine;
	@Getter
	private ElevenLabsEngine elevenLabsEngine;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final okhttp3.OkHttpClient httpClient;
	private final com.google.gson.Gson gson;
	private final List<TextToSpeechListener> textToSpeechListeners = new ArrayList<>();
	@Getter
	private boolean started = false;
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
		okhttp3.OkHttpClient httpClient,
		com.google.gson.Gson gson) {
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
	public void start() {
		if (config.nativeSpeechEnabled() && !isNativeSpeechRunning()) startNativeSpeech();

		startElevenLabs();

		if (!isPiperPathValid()) {
			if (isNativeSpeechRunning() || isElevenLabsRunning()) {
				started = true;
				triggerOnStart();
			}
			else {
				triggerOnPiperInvalid();
			}
			return;
		}

		isPiperUnquarantined = false; // set to false for each launch, in case piper path/files were modified
		started = isNativeSpeechRunning() || isElevenLabsRunning();
		try {
			for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
				try {
					if (modelRepository.hasModelLocal(modelURL.getModelName()) &&
						modelConfig.isModelEnabled(modelURL.getModelName())) {
						ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(modelURL.getModelName());
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

	public void startNativeSpeech() {
		stopNativeSpeech();

		if (!NativeSpeech.isSupported()) {
			log.debug("This platform has no system voices wired up yet, skipping");
			return;
		}

		try {
			nativeSpeechEngine = NativeSpeechEngine.start(playbackGate, dialogGen::get);
			triggerOnNativeSpeechStart(nativeSpeechEngine);
		}
		catch (IOException | RuntimeException e) {
			log.error("Failed to start the system voices", e);
			nativeSpeechEngine = null;
		}
	}

	public void stopNativeSpeech() {
		if (nativeSpeechEngine == null) return;

		NativeSpeechEngine stopping = nativeSpeechEngine;
		nativeSpeechEngine = null;
		try {
			stopping.stop();
		}
		catch (RuntimeException e) {
			log.error("Error stopping the system voices", e);
		}
		triggerOnNativeSpeechExit(stopping);
	}

	public boolean isNativeSpeechRunning() {
		return nativeSpeechEngine != null && nativeSpeechEngine.isAlive();
	}

	/**
	 * ElevenLabs is a cloud API with no process to spawn, so "starting" it only means loading the
	 * voice library for the configured key. Call again whenever the API key changes.
	 */
	public void startElevenLabs() {
		// A cloud backend failing must never stop piper or the system voices from coming up, so this
		// swallows rather than propagates: start() calls it before the local engines are running.
		try {
			elevenLabsVoiceRepository.resetWarning();
			elevenLabsVoiceRepository.refresh();
		}
		catch (RuntimeException e) {
			log.error("Failed to load the ElevenLabs voice library, continuing without it", e);
		}
	}

	/**
	 * The engine is built on first use rather than by {@link #start()}. It holds nothing but worker
	 * threads and an HTTP client, and tying it to the start button meant a saved API key that fired no
	 * config-change event left it null — which silently reported ElevenLabs as not running.
	 */
	private ElevenLabsEngine elevenLabsEngine() {
		ElevenLabsEngine engine = elevenLabsEngine;

		if (engine == null) {
			engine = new ElevenLabsEngine(httpClient, gson, config, playbackGate, dialogGen::get);
			elevenLabsEngine = engine;
		}

		return engine;
	}

	/**
	 * Cancels anything queued or in flight and releases the worker threads; a later call rebuilds
	 * them on demand.
	 * <p>
	 * Deliberately leaves the voice library loaded. It reflects the API key, not the engine, and
	 * dropping it here meant an unrelated full stop — swapping the piper binary, say — silently
	 * reported ElevenLabs as unavailable until the user pressed Start.
	 */
	public void stopElevenLabs() {
		try {
			if (elevenLabsEngine != null) {
				ElevenLabsEngine stopping = elevenLabsEngine;
				elevenLabsEngine = null;
				stopping.stop();
			}
		}
		catch (RuntimeException e) {
			log.error("Error stopping ElevenLabs", e);
			elevenLabsEngine = null;
		}
	}

	/**
	 * Usable as soon as there is a key and a loaded voice library — deliberately not tied to whether
	 * the engine object exists, since it is created on demand and there is no process to be up.
	 */
	public boolean isElevenLabsRunning() {
		return elevenLabsVoiceRepository.isConfigured() && elevenLabsVoiceRepository.hasVoices();
	}

	public void stop() {
		started = false;
		stopNativeSpeech();
		stopElevenLabs();
		stopPipers();
		triggerOnStop();
	}


	public void stopPiper() {
		stopPipers();
		started = isNativeSpeechRunning() || isElevenLabsRunning();
		triggerOnStop();
	}

	private void stopPipers() {
		for (Piper piper : pipers.values()) {
			try {
				piper.stop();
			} catch (RuntimeException e) {
				log.error("Error stopping piper: {}", piper, e);
			}
			triggerOnPiperExit(piper);
		}
		pipers.clear();
	}

	public void speak(VoiceID voiceID, String text, int distance, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotActiveException {
		speak(voiceID, text, distance, 0, audioQueueName);
	}

	public void speak(VoiceID voiceID, String text, int distance, int volumeBoostPercent, String audioQueueName)
		throws ModelLocalUnavailableException, PiperNotActiveException {
		assert distance >= 0;
		try {
			boolean isNativeVoice = NativeSpeech.isNativeModel(voiceID.getModelName());
			boolean isElevenLabsVoice = ElevenLabs.isElevenLabsModel(voiceID.getModelName());

			if (isNativeVoice) {
				if (nativeSpeechEngine == null || !nativeSpeechEngine.isAlive()) {
					throw new PiperNotActiveException(text, voiceID);
				}
			}
			else if (isElevenLabsVoice) {
				if (!isElevenLabsRunning()) {
					throw new PiperNotActiveException(text, voiceID);
				}
			}
			else {
				if (!modelRepository.hasModelLocal(voiceID.modelName)) {
					throw new ModelLocalUnavailableException(text, voiceID);
				}

				if (!isModelActive(voiceID.getModelName())) {
					throw new PiperNotActiveException(text, voiceID);
				}
			}

			// Piper should be guaranteed to be present due to checks above
			Piper piper = (isNativeVoice || isElevenLabsVoice) ? null : pipers.get(voiceID.modelName);

			boolean isDialog = MagicUsernames.DIALOG.equals(audioQueueName);
			if (!isDialog && playbackGate.isBacklogged(pendingAudioCount())) {
				log.debug("Dropping message, playback queue is full ({} pending). Text:{}",
					pendingAudioCount(), text);
				return;
			}

			int generation = isDialog ? dialogGen.get() : -1;
			float volume = getVolumeWithDistance(distance, volumeBoostPercent);

			if (isElevenLabsVoice) {
				// One request per line. Splitting would bill each fragment separately and drop an
				// audible gap into the middle of a sentence while the next request round-trips.
				elevenLabsEngine().speak(text, voiceID, volume, audioQueueName, generation);
				return;
			}

			List<String> fragments = splitSentence(text);
			for (String sentence : fragments) {
				if (isNativeVoice) {
					nativeSpeechEngine.speak(sentence, voiceID, volume, audioQueueName, generation);
				}
				else {
					piper.speak(sentence, voiceID, volume, audioQueueName, generation);
				}
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
		if (nativeSpeechEngine != null) {
			total += nativeSpeechEngine.pendingAudioCount();
		}
		if (elevenLabsEngine != null) {
			total += elevenLabsEngine.pendingAudioCount();
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
		if (nativeSpeechEngine != null) {
			nativeSpeechEngine.silenceQueue(queueName);
		}
		if (elevenLabsEngine != null) {
			elevenLabsEngine.silenceQueue(queueName);
		}
	}

	public void clearAllAudioQueues() {
		for (String modelName : pipers.keySet()) {
			pipers.get(modelName).clearQueue();
		}
		if (nativeSpeechEngine != null) {
			nativeSpeechEngine.clearQueue();
		}
		if (elevenLabsEngine != null) {
			elevenLabsEngine.clearQueue();
		}
	}

	public void clearOtherPlayersAudioQueue(String username) {
		for (String modelName : pipers.keySet()) {
			Piper piper = pipers.get(modelName);
			for (String audioQueueName : piper.getNamedAudioQueueMap().keySet()) {
				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;
				if (audioQueueName.equals(PluginHelper.getLocalPlayerUsername())) continue;
				if (audioQueueName.equals(username)) continue;
				piper.getNamedAudioQueueMap().get(audioQueueName).queue.clear();
			}
		}
	}

	public void clearPlayerAudioQueue(String username) {
		for (String modelName : pipers.keySet()) {
			Piper piper = pipers.get(modelName);
			for (String audioQueueName : piper.getNamedAudioQueueMap().keySet()) {
				// Don't clear dialogue
				if (audioQueueName.equals(AUDIO_QUEUE_DIALOGUE)) continue;

				if (audioQueueName.equals(username)) {
					piper.getNamedAudioQueueMap().get(audioQueueName).queue.clear();
				}
			}
		}
	}
	//</editor-fold>

	//<editor-fold desc="> Piper">

	/**
	 * Starts Piper for specific ModelLocal
	 */

	public boolean isPiperSetUp() {
		if (!isPiperPathValid()) return false;

		try {
			for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
				if (modelRepository.hasModelLocal(modelURL.getModelName())) return true;
			}
		}
		catch (IOException e) {
			log.debug("Could not check for downloaded voice packs", e);
		}

		return false;
	}

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

	public boolean isAnyEngineRunning() {
		return activePiperProcessCount() > 0 || isNativeSpeechRunning() || isElevenLabsRunning();
	}

	public static SpeechEngine engineOfModel(String modelName) {
		if (NativeSpeech.isNativeModel(modelName)) return SpeechEngine.SYSTEM;
		if (ElevenLabs.isElevenLabsModel(modelName)) return SpeechEngine.ELEVENLABS;
		return SpeechEngine.PIPER;
	}

	public boolean isModelActive(ModelRepository.ModelLocal modelLocal) {
		return isModelActive(modelLocal.getModelName());
	}

	public boolean isModelActive(String modelName) {
		if (NativeSpeech.isNativeModel(modelName)) {
			return nativeSpeechEngine != null && nativeSpeechEngine.isAlive();
		}

		if (ElevenLabs.isElevenLabsModel(modelName)) {
			return isElevenLabsRunning();
		}

		Piper piper = pipers.get(modelName);
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

	private void triggerOnNativeSpeechStart(NativeSpeechEngine engine) {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onNativeSpeechStart(engine);
		}
	}

	private void triggerOnNativeSpeechExit(NativeSpeechEngine engine) {
		for (TextToSpeechListener listener : textToSpeechListeners) {
			listener.onNativeSpeechExit(engine);
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

		default void onNativeSpeechStart(NativeSpeechEngine engine) {}

		default void onNativeSpeechExit(NativeSpeechEngine engine) {}

		default void onPiperInvalid() {}

		default void onStart() {}

		default void onStop() {}

	}
}
