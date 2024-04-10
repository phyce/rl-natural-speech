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
import dev.phyce.naturalspeech.events.SpeechEngineStarted;
import dev.phyce.naturalspeech.events.TextToSpeechStarted;
import dev.phyce.naturalspeech.events.TextToSpeechStopped;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.PiperNotActiveException;
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

// Renamed from TTSManager
@Slf4j
@PluginSingleton
public class TextToSpeech implements SpeechEngine {

	public final static String ABBREVIATION_FILE = "abbreviations.json";
	public static final String AUDIO_QUEUE_DIALOGUE = "&dialogue";

	private final NaturalSpeechConfig config;
	private final AudioEngine audioEngine;
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
		this.audioEngine = audioEngine;
		this.pluginEventBus = pluginEventBus;
	}

	@Override
	public StartResult start() {
		if (started) {
			stop();
		}
		started = true;

		for (SpeechEngine engine : engines) {
			StartResult result = engine.start();
			if (result == StartResult.SUCCESS) {
				activeEngines.add(engine);
				pluginEventBus.post(new SpeechEngineStarted(engine));
			}
			else {
				log.error("Failed to start engine:{}", engine.getClass().getSimpleName());
			}
		}

		if (activeEngines.isEmpty()){
			log.error("No engines started successfully.");
			return StartResult.FAILED;
		} else {
			pluginEventBus.post(new TextToSpeechStarted());
			return StartResult.SUCCESS;
		}
	}

	@Override
	public void stop() {
		started = false;
		cancelAll();

		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.stop();
		}
		activeEngines.clear();
		pluginEventBus.post(new TextToSpeechStopped());
	}

	@Override
	public boolean canSpeak() {
		for (SpeechEngine activeEngine : activeEngines) {
			if (activeEngine.canSpeak())
			{
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
	public SpeakResult speak(VoiceID voiceID, String text, Supplier<Float> gainSupplier, String lineName)
		throws ModelLocalUnavailableException, PiperNotActiveException {

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
	public void cancel(Predicate<String> lineCondition) {
		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.cancel(lineCondition);
		}
	}

	@Override
	public void cancelAll() {
		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.cancelAll();
		}
	}

	public void silence(Predicate<String> lineCondition) {
		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.cancel(lineCondition);
		}
		audioEngine.closeLineConditional(lineCondition);
	}

	public void silenceAll() {
		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.cancelAll();
		}
		audioEngine.closeAll();
	}

	public void silenceOtherLines(String lineName) {

		Predicate<String> linePredicate = (otherLineName) -> {

			if (otherLineName.equals(AUDIO_QUEUE_DIALOGUE)) return false;
			if (otherLineName.equals(AudioLineNames.LOCAL_USER)) return false;
			if (otherLineName.equals(lineName)) return false;

			return true;
		};

		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.cancel(linePredicate);
		}

		audioEngine.closeLineConditional(linePredicate);
	}

	public void cancelLine(String lineName) {

		for (SpeechEngine activeEngine : activeEngines) {
			activeEngine.cancel((otherLineName) -> otherLineName.equals(lineName));
		}

		audioEngine.closeLineName(lineName);
	}

	// In method so we can load again when user changes config

	public String expandAbbreviations(String text) {
		return TextUtil.expandAbbreviations(text, abbreviations);
	}
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
