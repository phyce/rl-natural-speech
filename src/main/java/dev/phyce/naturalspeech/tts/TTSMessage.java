package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts;
import net.runelite.api.ChatMessageType;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.NaturalSpeechConfig;

import net.runelite.api.events.ChatMessage;

import javax.inject.Inject;
import java.awt.*;


public class TTSMessage extends ChatMessage {
    @Inject
    private static NaturalSpeechConfig config;
    private final int voiceId;
    private int distance;

    public TTSMessage(ChatMessage chatMessage, int distance, int voiceId) {
        super(chatMessage.getMessageNode(), chatMessage.getType(), chatMessage.getName(), chatMessage.getMessage(), chatMessage.getSender(), chatMessage.getTimestamp());
        if(voiceId == -1) this.voiceId = getVoiceIndex(chatMessage.getName());
        else this.voiceId = voiceId;
        this.distance = distance;
    }
    public TTSMessage(ChatMessage chatMessage, int distance) {
        super(chatMessage.getMessageNode(), chatMessage.getType(), chatMessage.getName(), chatMessage.getMessage(), chatMessage.getSender(), chatMessage.getTimestamp());
        this.voiceId = getVoiceIndex(chatMessage.getName());
        this.distance = distance;
    }
    public int getVoiceId() {return voiceId;}
    public static int getVoiceIndex(String name) {
        int hashCode = name.hashCode();
        return Math.abs(hashCode) % config.MAX_VOICES;
    }
    public int getDistance() {return distance;}
}