package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

public class TTSManager
{
	private Map<String, TTSModel> models;
	private Path enginePath;
	private Map<String, String> shortenedPhrases;
	public TTSManager(Path engine, String phrases) {
		prepareShortenedPhrases(phrases);
		this.enginePath = engine;
		this.models = new HashMap<>();
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
	public void speak(ChatMessage message, int voiceID, int distance) throws IOException {
		speak(message, voiceID, distance, "libritts");
	}
	public void speak(ChatMessage message, int voiceID, int distance, String modelName) throws IOException {
		TTSItem ttsMessage;
		if (voiceID == -1) ttsMessage = new TTSItem(message, distance);
		else ttsMessage = new TTSItem(message, distance, voiceID);

		prepareMessage(ttsMessage);
	}
	private void prepareMessage(TTSItem message) throws IOException {
		//TODO this will need to be changed, TTSItem should carry which model to send to
		if(!isActive("libritts"))return;

		if(message.getType() != ChatMessageType.DIALOG) {
			message.setMessage(Strings.parseMessage(message.getMessage(), shortenedPhrases));
		}

		TTSItem[] sentences = message.explode();

		for (TTSItem sentence : sentences) {
			models.get("libritts").speak(sentence);
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
}
