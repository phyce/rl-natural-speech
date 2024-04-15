package dev.phyce.naturalspeech.tts;

import com.google.common.io.Resources;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.json.abbreviations.AbbreviationEntryDatum;
import dev.phyce.naturalspeech.events.TextToSpeechNoEngineError;
import dev.phyce.naturalspeech.events.SpeechEngineStarted;
import dev.phyce.naturalspeech.events.SpeechEngineStopped;
import dev.phyce.naturalspeech.events.TextToSpeechStarted;
import dev.phyce.naturalspeech.events.TextToSpeechStopped;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@PluginSingleton
public class TextToSpeech implements SpeechEngine {

	public final static String ABBREVIATION_FILE = "abbreviations.json";

	private final NaturalSpeechConfig config;
	private final PluginEventBus pluginEventBus;

	private final Vector<SpeechEngine> engines = new Vector<>();
	private final Vector<SpeechEngine> activeEngines = new Vector<>();

	private Map<String, String> abbreviations;

	@Getter
	private boolean started = false;

	@Inject
	private TextToSpeech(
		ClientThread clientThread,
		NaturalSpeechConfig config,
		AudioEngine audioEngine,
		PluginEventBus pluginEventBus
	) {
		this.config = config;
		this.pluginEventBus = pluginEventBus;
	}

	@Override
	public StartResult start() {

		if (started) {
			log.warn("Starting TextToSpeech when already started. Restarting.");
			stop();
		}

		for (SpeechEngine engine : engines) {
			StartResult result = engine.start();
			if (result == StartResult.SUCCESS) {
				activeEngines.add(engine);
				pluginEventBus.post(new SpeechEngineStarted(engine));
				log.trace("Started text-to-speech engine:{}", engine.getClass().getSimpleName());
			}
			else {
				log.trace("Failed to start engine:{}", engine.getClass().getSimpleName());
			}
		}

		if (activeEngines.isEmpty()){
			log.error("No engines started successfully.");
			pluginEventBus.post(new TextToSpeechNoEngineError());
			return StartResult.FAILED;
		} else {
			started = true;
			pluginEventBus.post(new TextToSpeechStarted());
			return StartResult.SUCCESS;
		}
	}

	@Override
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
	public boolean canSpeakAny() {
		for (SpeechEngine activeEngine : activeEngines) {
			if (activeEngine.canSpeakAny())
			{
				return true;
			}
		}
		return false;
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
	public SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName) {

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
