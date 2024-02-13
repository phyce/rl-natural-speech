package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.NaturalSpeechConfig;

import net.runelite.api.events.ChatMessage;

import javax.inject.Inject;


public class TTSMessage extends ChatMessage {
    @Inject
    private NaturalSpeechConfig config;
    private int voiceId;

    public TTSMessage(ChatMessage chatMessage, int voiceId) {
        super(chatMessage.getMessageNode(), chatMessage.getType(), chatMessage.getName(), chatMessage.getMessage(), chatMessage.getSender(), chatMessage.getTimestamp());
        this.voiceId = voiceId;
    }
    public TTSMessage(ChatMessage chatMessage) {
        super(chatMessage.getMessageNode(), chatMessage.getType(), chatMessage.getName(), chatMessage.getMessage(), chatMessage.getSender(), chatMessage.getTimestamp());
        this.voiceId = getVoiceIndex();
    }
    public int getVoiceIndex() {
        int hashCode = getName().hashCode();
        return Math.abs(hashCode) % config.MAX_VOICES;
    }
}