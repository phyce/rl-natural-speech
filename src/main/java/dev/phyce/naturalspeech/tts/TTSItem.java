package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.enums.SpeakerTypes;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.events.ChatMessage;

public class TTSItem extends ChatMessage {
	@Inject private static NaturalSpeechConfig config;

	public int voiceID;
	public String model;
	@Getter private int distance;
	@Getter private SpeakerTypes speakerType;
	private boolean configPersonalVoice;
	public byte[] audioClip;

//	private String[] sentences;
//	public TTSItem

	public TTSItem(ChatMessage message, int distance, int voiceID) {
		super(message.getMessageNode(), message.getType(), message.getName(), message.getMessage(), message.getSender(), message.getTimestamp());

		if(voiceID == -1) this.voiceID = calculateVoiceIndex();
		else this.voiceID = voiceID;

		this.distance = Math.max(distance, 0);
	}

	public TTSItem(ChatMessage message, int distance, boolean configPersonalVoice) {
		super(message.getMessageNode(), message.getType(), message.getName(), message.getMessage(), message.getSender(), message.getTimestamp());
//		this(message, distance, -1);
		this.configPersonalVoice = configPersonalVoice;
		this.distance = Math.max(distance, 0);
	}

	public TTSItem(TTSItem original, String sentence) {
		this.setMessage(sentence);
		this.setName(original.getName());
		this.setMessageNode(original.getMessageNode());
		this.setSender(original.getSender());
		this.setType(original.getType());
		this.setTimestamp(original.getTimestamp());
		this.voiceID = original.voiceID;
		this.distance = original.distance;
		this.speakerType = original.speakerType;
	}
	private int calculateVoiceIndex() {
		return calculateVoiceIndex(this.getName());
	}

	public static int calculateVoiceIndex(String name) {
		int hashCode = name.hashCode();
		return Math.abs(hashCode) % config.MAX_VOICES;
	}

	public TTSItem[] explode() {
		String[] sentences = this.getMessage().split("(?<=[.!?,])\\s+|(?<=[.!?,])$");
		TTSItem[] items = new TTSItem[sentences.length];

		for (int i = 0; i < sentences.length; i++) {
			String sentence = sentences[i] + (i < sentences.length - 1 ? "." : "");
			items[i] = new TTSItem(this, sentence);
		}

		return items;
	}
}
