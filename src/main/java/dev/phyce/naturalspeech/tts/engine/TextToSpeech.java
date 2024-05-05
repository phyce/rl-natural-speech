package dev.phyce.naturalspeech.tts.engine;

import com.google.common.io.Resources;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Monitor;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.PluginExecutorService;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.TextToSpeechConfig;
import dev.phyce.naturalspeech.configs.json.abbreviations.AbbreviationEntryDatum;
import dev.phyce.naturalspeech.events.SpeechEngineStartSkippedEngine;
import dev.phyce.naturalspeech.events.SpeechEngineStarted;
import dev.phyce.naturalspeech.events.SpeechEngineStopped;
import dev.phyce.naturalspeech.events.TextToSpeechFailedStart;
import dev.phyce.naturalspeech.events.TextToSpeechStarted;
import dev.phyce.naturalspeech.events.TextToSpeechStarting;
import dev.phyce.naturalspeech.events.TextToSpeechStopped;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@PluginSingleton
public class TextToSpeech implements SpeechEngine {

	// We need monitor&lock because start needs to sync with its own future thread
	private final Monitor monitor = new Monitor();
	private final AtomicBoolean startLock = new AtomicBoolean(false);
	private final Monitor.Guard whenStartUnlocked = new Monitor.Guard(monitor) {
		@Override
		public boolean isSatisfied() {return !startLock.getAcquire();}
	};

	public final static String ABBREVIATION_FILE = "abbreviations.json";

	private final NaturalSpeechConfig config;
	private final PluginEventBus pluginEventBus;
	private final PluginExecutorService pluginExecutorService;
	private final TextToSpeechConfig textToSpeechConfig;

	private final Vector<SpeechEngine> engines = new Vector<>();
	private final Vector<SpeechEngine> activeEngines = new Vector<>();

	private Map<String, String> abbreviations;

	@Getter
	private boolean started = false;

	@Inject
	private TextToSpeech(
		NaturalSpeechConfig config,
		PluginEventBus pluginEventBus,
		PluginExecutorService pluginExecutorService,
		TextToSpeechConfig textToSpeechConfig
	) {
		this.config = config;
		this.pluginEventBus = pluginEventBus;
		this.pluginExecutorService = pluginExecutorService;
		this.textToSpeechConfig = textToSpeechConfig;
	}

	@SneakyThrows(InterruptedException.class)
	@Override
	@Synchronized
	public ListenableFuture<StartResult> start(ExecutorService executorService) {
		monitor.enterWhen(whenStartUnlocked);

		try {
			// we must stop before start
			if (started) {
				log.warn("Starting TextToSpeech when already started.");
				stop();
			}

			// start lock, any other engine operations will wait until unlock
			startLock.set(true);

			pluginEventBus.post(new TextToSpeechStarting());

			List<SpeechEngine> startingEngines = new ArrayList<>();
			List<ListenableFuture<StartResult>> futures = new ArrayList<>();
			for (SpeechEngine engine : engines) {
				if (textToSpeechConfig.isEnabled(engine)) {
					ListenableFuture<StartResult> onDone = engine.start(pluginExecutorService);
					startingEngines.add(engine);
					futures.add(onDone);
				}
				else {
					pluginEventBus.post(new SpeechEngineStartSkippedEngine(engine));
				}
			}

			ListenableFuture<StartResult> onAllDone;

			if (startingEngines.isEmpty()) {
				// if no engines are enabled (through TextToSpeechConfig)
				onAllDone = Futures.immediateFuture(StartResult.DISABLED);
				log.info("No engines are enabled.");
				pluginEventBus.post(new TextToSpeechFailedStart(TextToSpeechFailedStart.Reason.ALL_DISABLED));
			}
			else {
				onAllDone = Futures.transform(Futures.allAsList(futures), (List<StartResult> results) -> {
					for (SpeechEngine engine : startingEngines) {
						if (engine.isStarted()) {
							activeEngines.add(engine);
							pluginEventBus.post(new SpeechEngineStarted(engine));
							log.trace("Started text-to-speech engine:{}", engine.getClass().getSimpleName());
						}
						else {
							log.trace("Failed to start engine:{}", engine.getClass().getSimpleName());
						}
					}

					if (!activeEngines.isEmpty()) {
						started = true;
						pluginEventBus.post(new TextToSpeechStarted());
						return StartResult.SUCCESS;
					}
					else { // Figure out why no engines started
						boolean allNotInstalled = results.stream().allMatch(
							result -> result == StartResult.NOT_INSTALLED);
						boolean allDisabled = results.stream().allMatch(
							result -> result == StartResult.DISABLED || result == StartResult.NOT_INSTALLED);

						StartResult result;
						if (allNotInstalled) {
							log.info("No engines are installed/available.");
							pluginEventBus.post(
								new TextToSpeechFailedStart(TextToSpeechFailedStart.Reason.NOT_INSTALLED));
							result = StartResult.NOT_INSTALLED;
						}
						else if (allDisabled) {
							// if settings disabled all engines (through internal config)
							// PiperModelConfig is an example of this, a Multi-Engine representing a fleet of Models
							log.info("No engines are enabled (disabled in internal config, ex PiperModelConfig).");
							pluginEventBus.post(
								new TextToSpeechFailedStart(TextToSpeechFailedStart.Reason.ALL_DISABLED));
							result = StartResult.DISABLED;
						}
						else {
							// if there are no engines available/installed
							log.error("No engines started successfully.");
							pluginEventBus.post(new TextToSpeechFailedStart(TextToSpeechFailedStart.Reason.ALL_FAILED));
							result = StartResult.FAILED;
						}

						return result;
					}
				}, executorService);
			}

			onAllDone.addListener(() -> {
				monitor.enter();
				startLock.set(false);
				monitor.leave();
			}, pluginExecutorService);

			return onAllDone;
		} finally {
			monitor.leave();
		}
	}

	@SneakyThrows(InterruptedException.class)
	@Override
	@Synchronized
	public void stop() {
		monitor.enterWhen(whenStartUnlocked);

		if (!started) {
			log.warn("Stopping TextToSpeech when not started. Ignoring.");
			return;
		}

		started = false;
		silenceAll();

		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.stop();
			pluginEventBus.post(new SpeechEngineStopped(activeEngine));
		}
		activeEngines.clear();
		pluginEventBus.post(new TextToSpeechStopped());

		monitor.leave();
	}

	@Override
	public boolean canSpeak(VoiceID voiceID) {
		for (SpeechEngine activeEngine : activeEngines) {
			if (activeEngine.canSpeak(voiceID)) {
				return true;
			}
		}
		return false;
	}

	public void register(SpeechEngine engine) {
		if (engines.contains(engine)) {
			log.error("Registering {} when it has been registered already.", engine);
			return;
		}
		log.trace("Registered {}", engine);
		engines.add(engine);
	}

	@Override
	public @NonNull SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {

		text = expandAbbreviations(text);

		for (SpeechEngine activeEngine : activeEngines) {
			SpeakResult result = activeEngine.speak(voiceID, text, gainSupplier, lineName);
			if (result == SpeakResult.ACCEPT) {
				return SpeakResult.ACCEPT;
			}
		}

		log.error("No engines were able to speak voiceID:{} text:{} lineName:{}", voiceID, text, lineName);
		return SpeakResult.REJECT;
	}

	@Override
	public void silence(Predicate<String> lineCondition) {
		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.silence(lineCondition);
		}
	}

	@Override
	public void silenceAll() {
		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.silenceAll();
		}
	}

	@Override
	public @NonNull EngineType getEngineType() {
		return EngineType.BUILTIN_PLUGIN;
	}

	@Override
	public @NonNull String getEngineName() {
		return "TextToSpeech"; // multi-engine
	}


	@SneakyThrows(InterruptedException.class)
	@Synchronized
	public ListenableFuture<StartResult> startEngine(SpeechEngine engine) {
		monitor.enterWhen(whenStartUnlocked);
		try {
			SettableFuture<StartResult> resultFuture = SettableFuture.create();

			// if it's already active, stop and remove active status
			if (activeEngines.remove(engine)) {
				engine.stop();
			}

			// lock start and begin async start
			startLock.set(true);

			// if it's a new engine, add the engine
			if (!engines.contains(engine)) {
				log.warn("startEngine call on engine that was not registered. Registering now.");
				engines.add(engine);
				Thread.dumpStack();
			}

			engine.start(pluginExecutorService).addListener(() -> {
				monitor.enter();
				try {
					if (engine.isStarted()) {
						activeEngines.add(engine);
						pluginEventBus.post(new SpeechEngineStarted(engine));
						log.trace("Started text-to-speech engine:{}", engine.getClass().getSimpleName());
						resultFuture.set(StartResult.SUCCESS);
					}
					else {
						log.trace("Failed to start engine:{}", engine.getClass().getSimpleName());
						resultFuture.set(StartResult.FAILED);
					}
				} finally {
					startLock.set(false);
					monitor.leave();
				}
			}, pluginExecutorService);

			return resultFuture;
		} finally {
			monitor.leave();
		}
	}

	@SneakyThrows(InterruptedException.class)
	@Synchronized
	public void stopEngine(SpeechEngine engine) {
		monitor.enterWhen(whenStartUnlocked);
		try {
			if (!engine.isStarted()) {
				log.warn("Attempting to stop engine that has not started:{}", engine.getClass().getSimpleName());
			}
			else if (!engines.contains(engine)) {
				log.warn("Attempting to stop engine not registered:{}", engine.getClass().getSimpleName());
			}
			else if (!activeEngines.contains(engine)) {
				log.error("SEVERE: " +
						"found engine that has started without using TextToSpeech::startEngine or TextToSpeech::start. " +
						"TextToSpeech was unaware the engine has already started. Stopping engine regardless...:{}",
					engine.getClass().getSimpleName());
				engine.stop();
			}
			else {
				activeEngines.remove(engine);
				engine.stop();
				pluginEventBus.post(new SpeechEngineStopped(engine));
				log.trace("Stopped text-to-speech engine:{}", engine.getClass().getSimpleName());
			}

			// If all engines are stopped, stop TextToSpeech
			if (activeEngines.isEmpty()) {
				stop();
			}
		} finally {
			monitor.leave();
		}
	}

	public String expandAbbreviations(String text) {
		return TextUtil.expandAbbreviations(text, abbreviations);
	}

	/**
	 * In method so we can load again when user changes config
	 */
	public void loadAbbreviations() {
		URL resourceUrl = Objects.requireNonNull(NaturalSpeechPlugin.class.getResource(ABBREVIATION_FILE));
		String jsonString;
		try {
			jsonString = Resources.toString(resourceUrl, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		abbreviations = new HashMap<>();

		if (config.useCommonAbbreviations()) {
			try {
				Type listOfAbbreviationEntryDatumType = new TypeToken<AbbreviationEntryDatum[]>() {}.getType();
				AbbreviationEntryDatum[] data =
					RuneLiteAPI.GSON.fromJson(jsonString, listOfAbbreviationEntryDatumType);

				for (AbbreviationEntryDatum entry : data) {abbreviations.put(entry.acronym, entry.sentence);}
			} catch (JsonSyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		String phrases = config.customAbbreviations();
		String[] lines = phrases.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) abbreviations.put(parts[0].trim(), parts[1].trim());
		}
	}

}
