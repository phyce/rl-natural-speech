package dev.phyce.naturalspeech.texttospeech.engine.piper;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository.PiperModel;
import dev.phyce.naturalspeech.utils.MacUnquarantine;
import dev.phyce.naturalspeech.utils.Platforms;
import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@PluginSingleton
public class PiperEngine implements SpeechEngine {

	private final RuntimePathConfig runtimeConfig;
	private final AudioEngine audioEngine;
	private final PluginExecutorService pluginExecutorService;
	private final PiperModelEngine.Factory modelEngineFactory;

	@Getter
	private boolean started = false;

	private final Map<PiperRepository.PiperModel, PiperModelEngine> pipers = new ConcurrentHashMap<>();

	@Inject
	private PiperEngine(
		RuntimePathConfig runtimeConfig,
		AudioEngine audioEngine,
		ConfigManager configManager,
		PiperRepository piperRepository,
		PluginExecutorService pluginExecutorService,
		PiperModelEngine.Factory modelEngineFactory
	) {
		this.runtimeConfig = runtimeConfig;
		this.audioEngine = audioEngine;
		this.pluginExecutorService = pluginExecutorService;
		this.modelEngineFactory = modelEngineFactory;

		piperRepository.models().forEach(this::load);
	}

	@Override
	public @NonNull SpeechEngine.SpeakStatus speak(
		VoiceID voiceID,
		String text,
		Supplier<Float> gainSupplier,
		String lineName
	) {
		if (!started) {
			return SpeakStatus.REJECT;
		}

		for (PiperModelEngine piper : pipers.values()) {
			if (!piper.isStarted()) continue;
			if (piper.speak(voiceID, text, gainSupplier, lineName) == SpeakStatus.ACCEPT) {
				return SpeakStatus.ACCEPT;
			}
		}

		return SpeakStatus.REJECT;
	}

	@Deprecated(since="Do not start engines directly, use SpeechManager::startEngine.")
	@Override
	public ListenableFuture<StartResult> start() {
		return Futures.submit(this::_start, pluginExecutorService);
	}

	@Deprecated(since="Do not stop engines directly, use SpeechManager::stopEngine")
	@Override
	public void stop() {
		if (!started) {
			return;
		}

		for (PiperModelEngine piper : pipers.values()) {
			piper.stop();
		}
	}

	@Override
	public boolean contains(VoiceID voiceID) {
		return pipers.values().stream().anyMatch(piper -> piper.contains(voiceID));
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		pipers.values().forEach(piper -> piper.silence(lineCondition));
		audioEngine.closeConditional(lineCondition);
	}

	@Override
	public void silenceAll() {
		pipers.values().forEach(PiperModelEngine::silenceAll);
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


	public PiperModelEngine load(PiperRepository.PiperModel piperModel) {
		if (pipers.containsKey(piperModel)) {
			return pipers.get(piperModel);
		}

		PiperModelEngine engine = modelEngineFactory.create(piperModel, runtimeConfig.getPiperPath());
		pipers.put(piperModel, engine);

		return engine;
	}

	public void unload(PiperModel piperModel) {
		if (!pipers.containsKey(piperModel)) {
			log.error("{} was not loaded, cannot unload", piperModel);
			return;
		}

		PiperModelEngine engine = pipers.remove(piperModel);
		if (engine.isStarted()) {
			engine.stop();
		}
	}

	private StartResult _start() {
		if (!isPiperPathValid()) {
			log.trace("No valid piper found at {}", runtimeConfig.getPiperPath());
			return StartResult.NOT_INSTALLED;
		}
		else {
			log.trace("Found Valid Piper at {}", runtimeConfig.getPiperPath());
		}

		if (started) {
			stop();
		}

		if (pipers.isEmpty()) {
			return StartResult.FAILED;
		}

		if (Platforms.IS_MAC) {
			MacUnquarantine.Unquarantine(runtimeConfig.getPiperPath());
		}


		StartResult[] results = pipers.values().parallelStream()
			.map(PiperModelEngine::start)
			.map(future -> {
				try {
					return future.get();
				} catch (InterruptedException e) {
					log.error("Interrupted while starting Piper", e);
					return null;
				} catch (ExecutionException e) {
					log.error("ErrorResult while starting Piper", e);
					return null;
				}
			})
			.filter(Objects::nonNull)
			.toArray(StartResult[]::new);

		boolean failed = false;
		for (StartResult result : results) {
			switch (result) {
				case SUCCESS:
					started = true;
					break;
				case FAILED:
					failed = true;
					break;
			}
		}

		if (started) {
			return StartResult.SUCCESS;
		}

		if (failed) {
			return StartResult.FAILED;
		}
		else {
			return StartResult.DISABLED;
		}
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


}
