package dev.phyce.naturalspeech.tts.piper;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.ModelConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.ModelConfigDatum;
import dev.phyce.naturalspeech.configs.json.ttsconfigs.PiperConfigDatum;
import dev.phyce.naturalspeech.events.piper.PiperModelStarted;
import dev.phyce.naturalspeech.events.piper.PiperModelStopped;
import dev.phyce.naturalspeech.events.piper.PiperProcessExited;
import dev.phyce.naturalspeech.events.piper.PiperProcessStarted;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.macos.MacUnquarantine;
import dev.phyce.naturalspeech.tts.SpeechEngine;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@PluginSingleton
public class PiperEngine implements SpeechEngine {

	private static final String CONFIG_KEY_MODEL_CONFIG = "ttsConfig";

	private final PiperRepository piperRepository;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final AudioEngine audioEngine;
	private final ConfigManager configManager;
	private final VoiceManager voiceManager;
	private final PluginEventBus pluginEventBus;

	private final ConcurrentHashMap<String, PiperModel> models = new ConcurrentHashMap<>();
	@Getter
	private ModelConfig modelConfig;
	private boolean isPiperUnquarantined = false;

	@Inject
	private PiperEngine(
		PiperRepository piperRepository,
		NaturalSpeechRuntimeConfig runtimeConfig,
		AudioEngine audioEngine,
		ConfigManager configManager,
		VoiceManager voiceManager,
		PluginEventBus pluginEventBus,
		TextToSpeech textToSpeech
	) {
		this.piperRepository = piperRepository;
		this.runtimeConfig = runtimeConfig;
		this.audioEngine = audioEngine;
		this.configManager = configManager;
		this.voiceManager = voiceManager;
		this.pluginEventBus = pluginEventBus;

		loadModelConfig();

		textToSpeech.register(this);
	}

	@Override
	public StartResult start() {
		if (!isPiperPathValid()) {
			return StartResult.FAILED;
		}

		isPiperUnquarantined = false; // set to false for each launch, in case piper path/files were modified

		for (PiperRepository.ModelURL modelURL : piperRepository.getModelURLS()) {
			try {
				if (piperRepository.hasModelLocal(modelURL.getModelName())
					&& modelConfig.isModelEnabled(modelURL.getModelName())) {
					PiperRepository.ModelLocal modelLocal = piperRepository.loadModelLocal(modelURL.getModelName());
					startModel(modelLocal);
				}
			} catch (IOException e) {
				log.error("Failed to start {}", modelURL.getModelName(), e);
				return StartResult.FAILED;
			}
		}

		return StartResult.SUCCESS;
	}

	@Override
	public void stop() {
		models.values().stream().map(PiperModel::getModelLocal).forEach(this::stopModel);
		models.clear();
	}

	@Override
	public boolean canSpeak() {
		int result = 0;
		for (String modelName : models.keySet()) {
			PiperModel model = models.get(modelName);
			result += model.countAlive();
		}

		return result > 0;
	}

	@Override
	public SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {

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

	@Override
	public void cancel(Predicate<String> lineCondition) {
		for (PiperModel piper : models.values()) {
			piper.cancelConditional(lineCondition);
		}
	}

	@Override
	public void cancelAll() {
		for (String modelName : models.keySet()) {
			models.get(modelName).cancelAll();
		}
	}

	public boolean isPiperPathValid() {
		File piper_file = runtimeConfig.getPiperPath().toFile();

		if (OSValidator.IS_WINDOWS) {
			String filename = piper_file.getName();
			// naive canExecute check for windows, 99.99% of humans use .exe extension for executables on Windows
			// File::canExecute returns true for all files on Windows.
			return filename.endsWith(".exe") && piper_file.exists() && !piper_file.isDirectory();
		}
		else {
			return piper_file.canExecute() && piper_file.exists() && !piper_file.isDirectory();
		}
	}

	public void startModel(PiperRepository.ModelLocal modelLocal) throws IOException {
		if (models.get(modelLocal.getModelName()) != null) {
			log.warn("Starting model for {} when there are already pipers running for the model.",
				modelLocal.getModelName());
			PiperModel duplicate = models.remove(modelLocal.getModelName());
			stopModel(duplicate.getModelLocal());
		}

		if (!isPiperUnquarantined && OSValidator.IS_MAC) {
			isPiperUnquarantined = MacUnquarantine.Unquarantine(runtimeConfig.getPiperPath());
		}

		PiperModel model = new PiperModel(audioEngine, modelLocal, runtimeConfig.getPiperPath());

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
					// if this is the last model running this model, unregister from voiceMap
					if (model.countAlive() == 0) {
//						stopModel(model.getModelLocal());
					}
				}
			}
		);

		model.start(modelConfig.getModelProcessCount(modelLocal.getModelName()));

		// if this is going to be the first model running this model register
		models.put(modelLocal.getModelName(), model);
		voiceManager.registerPiperModel(modelLocal);
		pluginEventBus.post(new PiperModelStarted(model));
	}

	public void stopModel(PiperRepository.ModelLocal modelLocal) {
		PiperModel piper = models.remove(modelLocal.getModelName());
		if (piper != null) {
			piper.stop();
			voiceManager.unregisterPiperModel(piper.getModelLocal());
			pluginEventBus.post(new PiperModelStopped(piper));
		}
		else {
			log.error("Attempting to stop in-active model:{}", modelLocal.getModelName());
		}
	}

	public boolean isModelActive(String modelName) {
		PiperModel piper = models.get(modelName);
		return piper != null && piper.countAlive() > 0;
	}

	public void saveModelConfig() {
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MODEL_CONFIG, modelConfig.toJson());
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
}
