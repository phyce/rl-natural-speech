package dev.phyce.naturalspeech.tts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.phyce.naturalspeech.Strings;
import dev.phyce.naturalspeech.common.PlayerCommon;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

public class TTSManager
{
	private HashMap<String, SpeakerConfiguration> speakerConfigurations;
	private Map<String, TTSModel> models;
	private Path enginePath;
	private Map<String, String> shortenedPhrases;
	private final String speakerConfig = "./speaker_config.json";
	public TTSManager(Path engine, String phrases) {
		speakerConfigurations = new HashMap<>();
		this.enginePath = engine;
		this.models = new HashMap<>();
		loadConfigurations();
		System.out.println("speakerConfigurations");
//		System.out.println(speakerConfigurations.get("phyce").getModel());
//		System.out.println(speakerConfigurations.get("banker").getName());
		prepareShortenedPhrases(phrases);
	}
	private void loadConfigurations() {
		try {
			if (!Files.exists(Paths.get(speakerConfig))) {
				// If the file does not exist, create an empty configuration list and save it
				saveConfigurations(new HashMap<String, SpeakerConfiguration>());
			}
			try (FileReader reader = new FileReader(speakerConfig)) {
				Type listType = new TypeToken<List<SpeakerConfiguration>>() {}.getType();
				List<SpeakerConfiguration> configs = new Gson().fromJson(reader, listType);
				if (configs != null) {
					configs.forEach(config -> speakerConfigurations.put(config.getName(), config));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void saveConfigurations(HashMap<String, SpeakerConfiguration> configs) {
		try (FileWriter writer = new FileWriter(speakerConfig)) {
			new Gson().toJson(configs, writer);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void saveConfigurations() {
		saveConfigurations(speakerConfigurations);
	}
	public void startVoiceModel(Path voicePath) {
		String name = "libritts";
		TTSModel model = new TTSModel(name, voicePath, enginePath, 2);
		models.put(name, model);
	}
	private void prepareShortenedPhrases(String phrases) {
		shortenedPhrases = new HashMap<>();
		String[] lines = phrases.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) shortenedPhrases.put(parts[0].trim(), parts[1].trim());
		}
	}
	public boolean isActive(String modelName) {
		return (models.get(modelName).activeInstances() > 0);
	}
	public boolean isActive() {
		for (String key : models.keySet()) {
			TTSModel model = models.get(key);
			if (model.activeInstances() > 0) return true;
		}
		return false;
	}
//	public void speak(ChatMessage message, int voiceID, int distance) throws IOException {
//		speak(message, voiceID, distance, "libritts");
//	}
//	public void speak(ChatMessage message, int voiceID, int distance, String modelName) throws IOException {
//		TTSItem ttsMessage;
//		if (voiceID == -1) ttsMessage = new TTSItem(message, distance);
//		else ttsMessage = new TTSItem(message, distance, voiceID);
//
//		prepareMessage(ttsMessage);
//	}
	public void speak(TTSItem message)  throws IOException {
		message.model = getVoiceModel(message.getName());
		message.voiceID = getVoiceID(message.getName());
		prepareMessage(message);
	}

	public String getVoiceModel(String username) {
		SpeakerConfiguration config = speakerConfigurations.get(username.toLowerCase());
		if ( config != null ) {
			System.out.println("config MODEL not null for: " + username);
			System.out.println(config.getModel());

			return config.getModel();
		}

		for (String key : models.keySet()) {
			TTSModel model = models.get(key);
			return model.getName();
		}
		//TODO need to do something different here
		return "";
	}

	public int getVoiceID(String username) {
		SpeakerConfiguration config = speakerConfigurations.get(username.toLowerCase());
		if ( config != null ) {
			System.out.println("config  VOICE ID not null for: " + username);
			System.out.println(config.getVoiceID());
			return config.getVoiceID();
		}

		return TTSItem.calculateVoiceIndex(username);
	}

	private void prepareMessage(TTSItem message) throws IOException {
		//TODO this will need to be changed, TTSItem should carry which model to send to
		if(!isActive(message.model))return;
		if(message.getType() != ChatMessageType.DIALOG) {
			message.setMessage(Strings.parseMessage(message.getMessage(), shortenedPhrases));
		}

		TTSItem[] sentences = message.explode();
		for (TTSItem sentence : sentences) {
			models.get(message.model).speak(sentence);
		}
	}
	public void shutDown() {
		for (String key : models.keySet()) {
			models.get(key).shutDown();
		}
	}
	public void clearQueues() {
		for (String key : models.keySet()) {
			models.get(key).clearQueue();
		}
	}

	public void focusOnPlayer(String username) {
		for (String key : models.keySet()) {
			for (String queueName : models.get(key).getAudioQueues().keySet()) {
				if (queueName == "&dialog") continue;
				if (queueName == PlayerCommon.getUsername()) continue;
				if (queueName == username) continue;
				models.get(key).getAudioQueues().get(queueName).queue.clear();
			}
		}
	}

	public void clearPlayerAudio(String username) {
		for (String key : models.keySet()) {
			for (String queueName : models.get(key).getAudioQueues().keySet()) {
				if (queueName == "&dialog") continue;
				if (queueName == username) models.get(key).getAudioQueues().get(queueName).queue.clear();
			}
		}
	}
}
