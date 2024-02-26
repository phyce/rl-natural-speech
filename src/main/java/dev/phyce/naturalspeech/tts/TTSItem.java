package dev.phyce.naturalspeech.tts;

import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.enums.SpeakerTypes;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.events.ChatMessage;

public class TTSItem extends ChatMessage {
	@Inject private static NaturalSpeechConfig config;

	@Getter private final int voiceID;
	@Getter private int distance;
	@Getter private SpeakerTypes speakerType;
	public byte[] audioClip;

	public String message;

	public TTSItem(ChatMessage message, int distance, int voiceID) {
		super(message.getMessageNode(), message.getType(), message.getName(), message.getMessage(), message.getSender(), message.getTimestamp());

		if(voiceID == -1) this.voiceID = calculateVoiceIndex();
		else this.voiceID = voiceID;

		this.distance = Math.max(distance, 0);
	}

	public TTSItem(ChatMessage message, int distance) {	this(message, distance, -1); }

	private int calculateVoiceIndex() {
		return calculateVoiceIndex(this.getName());
	}

	public static int calculateVoiceIndex(String name) {
		int hashCode = name.hashCode();
		return Math.abs(hashCode) % config.MAX_VOICES;
	}
}
