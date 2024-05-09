package dev.phyce.naturalspeech.texttospeech.engine.piper;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.audio.AudioEngine;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.configs.PiperConfig;
import dev.phyce.naturalspeech.events.PiperProcessCrashed;
import dev.phyce.naturalspeech.events.PiperModelStarted;
import dev.phyce.naturalspeech.events.PiperModelStopped;
import dev.phyce.naturalspeech.events.PiperProcessExited;
import dev.phyce.naturalspeech.events.PiperProcessStarted;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.utils.MacUnquarantine;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.Platforms;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@PluginSingleton
public class PiperEngine implements SpeechEngine {
	// need lombok to expose secret lock because future thread needs to synchronize on the lock
	private final Object lock = new Object[0];

	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";

	private final PiperRepository piperRepository;
	private final RuntimePathConfig runtimeConfig;
	private final AudioEngine audioEngine;
	private final ConfigManager configManager;
	private final VoiceManager voiceManager;
	private final PluginEventBus pluginEventBus;
	private final PluginExecutorService pluginExecutorService;

	private final ConcurrentHashMap<String, PiperModel> models = new ConcurrentHashMap<>();
	@Getter
	private PiperConfig piperConfig;
	private boolean isPiperUnquarantined = false;

	@Getter
	private boolean started = false;

	@Inject
	private PiperEngine(
		PiperRepository piperRepository,
		RuntimePathConfig runtimeConfig,
		AudioEngine audioEngine,
		ConfigManager configManager,
		VoiceManager voiceManager,
		PluginEventBus pluginEventBus,
		PluginExecutorService pluginExecutorService
	) {
		this.piperRepository = piperRepository;
		this.runtimeConfig = runtimeConfig;
		this.audioEngine = audioEngine;
		this.configManager = configManager;
		this.voiceManager = voiceManager;
		this.pluginEventBus = pluginEventBus;
		this.pluginExecutorService = pluginExecutorService;

		loadPiperConfig();
	}

	@Override
	public @NonNull SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {

		if (!isModelActive(voiceID.getModelName())) {
			return SpeakResult.REJECT;
		}

		if (!piperRepository.hasModelLocal(voiceID.modelName)) {
			return SpeakResult.REJECT;
		}

		PiperModel piper = models.get(voiceID.modelName);

		if (piper.speak(voiceID, text, gainSupplier, lineName)) {
			return SpeakResult.ACCEPT;
		}
		else {
			return SpeakResult.REJECT;
		}
	}

	@Deprecated(since = "Do not start engines directly, use TextToSpeech::startEngine.")
	@Synchronized("lock")
	@Override
	public ListenableFuture<StartResult> start(ExecutorService executorService) {

		if (!isPiperPathValid()) {
			log.trace("No valid piper found at {}", runtimeConfig.getPiperPath());
			return Futures.immediateFuture(StartResult.NOT_INSTALLED);
		}
		else {
			log.trace("Found Valid Piper at {}", runtimeConfig.getPiperPath());
		}

		return Futures.submit(() -> {
			synchronized (lock) {

				if (started) {
					stop();
				}

				isPiperUnquarantined = false; // set to false for each launch, in case piper path/files were modified

				boolean isDisabled = false;
				for (PiperRepository.ModelURL modelURL : piperRepository.getModelURLS()) {
					try {
						if (piperRepository.hasModelLocal(modelURL.getModelName())) {
							if (piperConfig.isModelEnabled(modelURL.getModelName())) {
								PiperRepository.ModelLocal modelLocal =
									piperRepository.loadModelLocal(modelURL.getModelName());
								startModel(modelLocal);
								started = true;
							} else {
								isDisabled = true;
							}
						}
					} catch (IOException e) {
						log.error("Failed to start model {}", modelURL.getModelName(), e);
					}
				}

				if (started) {
					return StartResult.SUCCESS;
				} else if (isDisabled) {
					return StartResult.DISABLED;
				} else {
					return StartResult.FAILED;
				}
			}
		}, executorService);
	}

	@Deprecated(since = "Do not stop engines directly, use TextToSpeech::stopEngine")
	@Override
	@Synchronized("lock")
	public void stop() {
		if (!started) {
			return;
		}

		models.values().stream().map(PiperModel::getModelLocal).forEach(this::stopModel);
		models.clear();
		started = false;
	}

	@Override
	public boolean canSpeak(VoiceID voiceID) {
		return isModelActive(voiceID.modelName);
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		for (PiperModel piper : models.values()) {
			piper.cancelConditional(lineCondition);
		}
		audioEngine.closeLineConditional(lineCondition);
	}

	@Override
	public void silenceAll() {
		for (String modelName : models.keySet()) {
			models.get(modelName).cancelAll();
		}
		audioEngine.closeAll();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.EXTERNAL_DEPENDENCY;
	}

	@Override
	public @NonNull String getEngineName() {
		return "PiperEngine";
	}

	public boolean isAlive() {
		int result = 0;
		for (String modelName : models.keySet()) {
			PiperModel model = models.get(modelName);
			result += model.countAlive();
		}

		return result > 0;
	}

	public boolean isPiperPathValid() {

		if (NaturalSpeechPlugin._SIMULATE_NO_TTS || NaturalSpeechPlugin._SIMULATE_MINIMUM_MODE) {
			return false;
		}

		File piper_file = runtimeConfig.getPiperPath().toFile();

		if (Platforms.IS_WINDOWS) {
			String filename = piper_file.getName();
			// naive canExecute check for windows, 99.99% of humans use .exe extension for executables on Windows
			// File::canExecute returns true for all files on Windows.
			return filename.endsWith(".exe") && piper_file.exists() && !piper_file.isDirectory();
		}
		else {
			return piper_file.canExecute() && piper_file.exists() && !piper_file.isDirectory();
		}
	}

	@Synchronized("lock")
	public void startModel(PiperRepository.ModelLocal modelLocal) throws IOException {
		if (models.get(modelLocal.getModelName()) != null) {
			log.warn("Starting model for {} when there are already pipers running for the model.",
				modelLocal.getModelName());
			PiperModel duplicate = models.remove(modelLocal.getModelName());
			stopModel(duplicate.getModelLocal());
		}

		if (!isPiperUnquarantined && Platforms.IS_MAC) {
			isPiperUnquarantined = MacUnquarantine.Unquarantine(runtimeConfig.getPiperPath());
		}

		PiperModel model = new PiperModel(pluginExecutorService, audioEngine, modelLocal, runtimeConfig.getPiperPath());

		// Careful, PiperProcess listeners are not called on the client thread
		model.addPiperListener(
			new PiperModel.PiperProcessLifetimeListener() {
				@Override
				public void onPiperProcessStart(PiperProcess process) {
					pluginEventBus.post(new PiperProcessStarted(model, process));
				}

				@Override
				public void onPiperProcessExit(PiperProcess process) {
					pluginEventBus.post(new PiperProcessExited(model, process));
				}

				@Override
				public void onPiperProcessCrash(PiperProcess process) {
					pluginEventBus.post(new PiperProcessCrashed(model, process));
					if (model.countAlive() == 0) {
						stopModel(modelLocal);
					}

					if (models.isEmpty()) {
						stop();
					}
				}
			}
		);

		model.start(piperConfig.getModelProcessCount(modelLocal.getModelName()));

		// if this is going to be the first model running this model register
		models.put(modelLocal.getModelName(), model);
		for (PiperRepository.PiperVoiceMetadata voiceMetadata : modelLocal.getPiperVoiceMetadata()) {
			voiceManager.register(voiceMetadata.toVoiceID(), voiceMetadata.getGender());
		}
		pluginEventBus.post(new PiperModelStarted(model));
	}

	@Synchronized("lock")
	public void stopModel(PiperRepository.ModelLocal modelLocal) {
		PiperModel piper = models.remove(modelLocal.getModelName());
		if (piper != null) {
			piper.stop();
			for (PiperRepository.PiperVoiceMetadata voiceMetadata : piper.getModelLocal().getPiperVoiceMetadata()) {
				voiceManager.unregister(voiceMetadata.toVoiceID());
			}
			pluginEventBus.post(new PiperModelStopped(piper));
		}
		else {
			log.error("Attempting to stop in-active model:{}", modelLocal.getModelName());
			Thread.dumpStack();
		}

	}

	public boolean isModelActive(String modelName) {
		PiperModel piper = models.get(modelName);
		return piper != null && piper.countAlive() > 0;
	}

	public Integer getTaskQueueSize() {
		return models.reduceEntries(Long.MAX_VALUE, (entry) -> entry.getValue().getTaskQueueSize(), Integer::sum);
	}

	public void savePiperConfig() {
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG, piperConfig.toJSON());
	}

	public void loadPiperConfig() {
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG);

		// no existing configs
		if (json == null) {
			// default piper configuration with libritts turned on
			this.piperConfig = new PiperConfig();
			this.piperConfig.getModelConfigs().add(new PiperConfig.ModelConfig("libritts", true, 1));
		}
		else { // has existing config, just load the json
			this.piperConfig = PiperConfig.fromJSON(json);
		}
	}
}
