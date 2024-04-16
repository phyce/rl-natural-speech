package dev.phyce.naturalspeech.tts;

import com.google.common.io.Resources;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import dev.phyce.naturalspeech.tts.piper.PiperEngine;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@PluginSingleton
public class TextToSpeech implements SpeechEngine {
	private final Object lock = new Object[0];

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

	@Override
	@Synchronized("lock")
	public ListenableFuture<StartResult> start(ExecutorService executorService) {

		if (started) {
			log.warn("Starting TextToSpeech when already started. Restarting.");
			stop();
		}

		pluginEventBus.post(new TextToSpeechStarting());

		List<ListenableFuture<StartResult>> futures = new ArrayList<>();
		for (SpeechEngine engine : engines) {
			if (textToSpeechConfig.isEnabled(engine)) {
				futures.add(engine.start(pluginExecutorService));
			} else {
				pluginEventBus.post(new SpeechEngineStartSkippedEngine(engine));
			}
		}

		return Futures.whenAllComplete(futures).call(
			() -> {
				synchronized (lock) {
					for (SpeechEngine engine : engines) {
						if (engine.isStarted()) {
							activeEngines.add(engine);
							pluginEventBus.post(new SpeechEngineStarted(engine));
							log.trace("Started text-to-speech engine:{}", engine.getClass().getSimpleName());
						}
						else {
							log.trace("Failed to start engine:{}", engine.getClass().getSimpleName());
						}
					}

					if (activeEngines.isEmpty()) {
						// if all engines were disabled
						if (engines.stream().noneMatch(
							engine -> {
								// PiperEngine handles its own enable status, per model.
								// PiperEngine itself is never disabled.
								if (engine instanceof PiperEngine) {
									return false;
								}
								return textToSpeechConfig.isEnabled(engine);
							}
						)) {
							log.error("No engines started successfully because all them were disabled.");
							pluginEventBus.post(new TextToSpeechFailedStart(TextToSpeechFailedStart.Reason.ALL_DISABLED));
							return StartResult.FAILED;
						}
						// if there are no engines available/installed
						log.error("No engines started successfully.");
						pluginEventBus.post(new TextToSpeechFailedStart(TextToSpeechFailedStart.Reason.ALL_FAILED));
						return StartResult.FAILED;
					}
					else {
						started = true;
						pluginEventBus.post(new TextToSpeechStarted());
						return StartResult.SUCCESS;
					}
				}
			},
			executorService
		);
	}

	@Override
	@Synchronized("lock")
	public void stop() {
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

	@Synchronized
	public ListenableFuture<StartResult> startEngine(SpeechEngine engine) {

		SettableFuture<StartResult> resultFuture = SettableFuture.create();

		pluginExecutorService.submit(() -> {
			synchronized (lock) {
				// if it's already active stop and remove active status
				if (activeEngines.remove(engine)) {
					engine.stop();
				}

				// if it's a new engine, add the engine
				if (!engines.contains(engine)) {
					engines.add(engine);
				}

				try {
					engine.start(pluginExecutorService).get();
				} catch (InterruptedException | ExecutionException ignore) {}

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
			}
		});

		return resultFuture;
	}

	@Synchronized("lock")
	public void stopEngine(SpeechEngine engine) {
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
		}

		// If all engines are stopped, stop TextToSpeech
		if (activeEngines.isEmpty()) {
			stop();
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
