package dev.phyce.naturalspeech.texttospeech.engine;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.SpeechManagerConfig;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.events.SpeechEngineEvent;
import dev.phyce.naturalspeech.events.SpeechManagerEvent;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.MagicNames;
import dev.phyce.naturalspeech.texttospeech.Voice;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import dev.phyce.naturalspeech.utils.FuncFutures;
import dev.phyce.naturalspeech.utils.Result;
import static dev.phyce.naturalspeech.utils.Result.Error;
import static dev.phyce.naturalspeech.utils.Result.ResultFutures.immediateError;
import dev.phyce.naturalspeech.utils.StreamableFuture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sound.sampled.AudioInputStream;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import org.slf4j.MarkerFactory;

@Slf4j
@PluginSingleton
public class SpeechManager implements SpeechEngine, PluginModule {
	private final AudioEngine audioEngine;
	private final VoiceManager voiceManager;
	private final PluginEventBus pluginEventBus;
	private final PluginExecutorService pluginExecutorService;
	private final SpeechManagerConfig speechManagerConfig;
	private ImmutableList<ManagedSpeechEngine> engines = ImmutableList.of();

	@Inject
	private SpeechManager(
		AudioEngine audioEngine,
		PluginEventBus pluginEventBus,
		PluginExecutorService pluginExecutorService,
		SpeechManagerConfig speechManagerConfig,
		PiperRepository piperRepository,
		VoiceManager voiceManager,
		MacSpeechEngine macEngine,
		SAPI4Engine sapi4Engine,
		SAPI5Engine sapi5Engine,
		PiperEngine.Factory modelEngineFactory
	) {
		this.audioEngine = audioEngine;
		this.pluginEventBus = pluginEventBus;
		this.pluginExecutorService = pluginExecutorService;
		this.speechManagerConfig = speechManagerConfig;
		this.voiceManager = voiceManager;

		piperRepository.getModels()
			.map(modelEngineFactory::create)
			.forEach(this::loadEngine);

		loadEngine(macEngine);
		loadEngine(sapi4Engine);
		loadEngine(sapi5Engine);
	}

	@Override
	public void startUp() {
		pluginEventBus.post(SpeechManagerEvent.STARTING(this));

		ListenableFuture<List<Result<Void, @NonNull EngineError>>> allFutures =
			Futures.allAsList(engines.stream()
				.map(this::startupEngine)
				.collect(Collectors.toList())
			);

		FuncFutures.onComplete(allFutures,
			() -> pluginEventBus.post(SpeechManagerEvent.STARTED(this)));
	}

	@Override
	public void shutDown() {
		engines.forEach(this::shutdownEngine);
		pluginEventBus.post(SpeechManagerEvent.STOPPED(this));
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		if (isAlive()) {
			switch (event.getKey()) {
				case ConfigKeys.MUTE_SELF:
					log.trace("Detected mute-self toggle, clearing audio queue.");
					silence((otherLineName) -> otherLineName.equals(MagicNames.LOCAL_PLAYER));
					break;

				case ConfigKeys.MUTE_OTHER_PLAYERS:
					log.trace("Detected mute-others toggle, clearing audio queue.");
					silence((otherLineName) -> !otherLineName.equals(MagicNames.LOCAL_PLAYER));
					break;
			}
		}
	}

	@Override
	public @NonNull Result<StreamableFuture<Audio>, Rejection> generate(
		@NonNull VoiceID voiceID,
		@NonNull String text
	) {

		List<Rejection> rejections = new ArrayList<>(engines.size());
		for (SpeechEngine engine : engines) {
			var result = engine.generate(voiceID, text);
			if (result.isOk()) {
				return result;
			}
			else {
				rejections.add(result.unwrapError());
			}
		}

		log.error("No engines were able to speak voiceID:{} text:{}", voiceID, text);
		return Error(Rejection.MULTIPLE(this, rejections));
	}

	public void speak(
		@NonNull VoiceID voiceID,
		@NonNull String text,
		@NonNull Supplier<Float> gainSupplier,
		@NonNull String line
	) {
		Result<StreamableFuture<Audio>, Rejection> result = generate(voiceID, text);
		result.ifOk(future -> {
			future.addStreamListener(audio -> {
				Preconditions.checkNotNull(audio);

				try (AudioInputStream stream = audio.toInputStream()) {
					audioEngine.play(line, stream, gainSupplier);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}, pluginExecutorService);

			FuncFutures.onException(future,
				(e) -> log.error("Exception while to generate audio for {} with {}", voiceID, e.toString()));
		});
		result.ifError(this::logRejection);
	}

	@Synchronized
	public void loadEngine(@NonNull SpeechEngine engine) {
		Preconditions.checkArgument(engine instanceof ManagedSpeechEngine);
		ManagedSpeechEngine managedEngine = (ManagedSpeechEngine) engine;
		Preconditions.checkArgument(!engines.contains(managedEngine));

		engines = ImmutableList.<ManagedSpeechEngine>builder()
			.addAll(engines)
			.add(managedEngine)
			.build();
	}

	@Synchronized
	public void unloadEngine(@NonNull SpeechEngine engine) {
		Preconditions.checkArgument(engine instanceof ManagedSpeechEngine);
		ManagedSpeechEngine managedEngine = (ManagedSpeechEngine) engine;
		Preconditions.checkArgument(engines.contains(managedEngine));

		if (managedEngine.isAlive()) managedEngine.shutdown();

		var builder = ImmutableList.<ManagedSpeechEngine>builder();
		builder.addAll(engines.stream()
			.filter(e -> !Objects.equals(e, managedEngine))
			.iterator());
		engines = builder.build();
	}

	@NonNull
	public ListenableFuture<Result<Void, @NonNull EngineError>> startupEngine(
		@NonNull SpeechEngine engine
	) {
		Preconditions.checkArgument(engine instanceof ManagedSpeechEngine);
		ManagedSpeechEngine managedEngine = (ManagedSpeechEngine) engine;
		Preconditions.checkArgument(engines.contains(managedEngine),
			"Starting engine that has not been loaded with SpeechManager::loadEngine");

		pluginEventBus.post(SpeechEngineEvent.STARTING(managedEngine));

		if (!speechManagerConfig.isEnabled(engine)) {
			return immediateError(EngineError.DISABLED(engine));
		}

		ListenableFuture<Result<Void, EngineError>> future = managedEngine.startup();

		FuncFutures.onResult(future, (result) -> {
			result.ifOk(() -> {
				managedEngine.getVoices().forEach(voiceManager::register);
				pluginEventBus.post(SpeechEngineEvent.STARTED(managedEngine));
			});
			result.ifError(this::postError);
		});

		return future;
	}

	public void shutdownEngine(@NonNull SpeechEngine engine) {
		ManagedSpeechEngine managedEngine = (ManagedSpeechEngine) engine;

		managedEngine.getVoiceIDs().forEach(voiceManager::unregister);
		managedEngine.shutdown();

		pluginEventBus.post(SpeechEngineEvent.STOPPED(managedEngine));
	}

	private void postError(EngineError error) {
		error.log();
		switch (error.reason) {
			case NO_RUNTIME:
				pluginEventBus.post(SpeechEngineEvent.START_NO_RUNTIME(error.engine));
				break;
			case NO_MODEL:
				pluginEventBus.post(SpeechEngineEvent.START_NO_MODEL(error.engine));
				break;
			case DISABLED:
				pluginEventBus.post(SpeechEngineEvent.START_DISABLED(error.engine));
				break;
			case UNEXPECTED_FAIL:
				pluginEventBus.post(SpeechEngineEvent.START_CRASHED(error));
				break;
			case ALREADY_STARTED:
				// ignore
				break;
			case MULTIPLE_REASONS:
				error.childs.forEach(this::postError);
				break;
			default:
				log.error("Unhandled EngineError {}", error.reason);
				break;
		}
	}

	private void logRejection(Rejection rejection) {
		SpeechEngine engine = rejection.engine;
		String engineName = engine.getEngineName();
		switch (rejection.reason) {
			case REJECT:
			case DEAD:
				log.error(MarkerFactory.getMarker(engineName), "{} - {}", rejection.reason, engineName);
				break;
			case MULTIPLE:
				rejection.childs.forEach(this::logRejection);
				break;
			default:
				log.error("Unhandled Rejection", rejection);
				break;
		}
	}

	public void silenceAll() {
		// FIXME SILENCE
	}

	public void silence(@NonNull Predicate<String> linePredicate) {
		// FIXME SILENCE
	}

	@Override
	public boolean isAlive() {
		return engines.stream().anyMatch(SpeechEngine::isAlive);
	}

	public boolean canSpeak(@NonNull VoiceID voiceID) {
		return engines.stream().anyMatch(e -> e.getVoiceIDs().contains(voiceID));
	}

	@Override
	public ImmutableSet<Voice> getVoices() {
		ImmutableSet.Builder<Voice> builder = ImmutableSet.builder();
		engines.stream()
			.map(SpeechEngine::getVoices)
			.forEach(builder::addAll);
		return builder.build();
	}

	@Override
	public ImmutableSet<VoiceID> getVoiceIDs() {
		ImmutableSet.Builder<VoiceID> builder = ImmutableSet.builder();
		engines.stream()
			.map(SpeechEngine::getVoiceIDs)
			.forEach(builder::addAll);
		return builder.build();
	}

	@Override
	public @NonNull String getEngineName() {
		return SpeechManager.class.getSimpleName();
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.MANAGER;
	}

	public Stream<? extends SpeechEngine> getEngines() {
		return engines.stream();
	}

}
