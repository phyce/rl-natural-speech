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

    public TTSMessage(String message, int voiceId) {
        this.setType(ChatMessageType.DIALOG);
        this.voiceId = voiceId;

    }
    public TTSMessage(ChatMessage chatMessage, int voiceId) {
        super(chatMessage.getMessageNode(), chatMessage.getType(), chatMessage.getName(), chatMessage.getMessage(), chatMessage.getSender(), chatMessage.getTimestamp());
        if(voiceId == -1) this.voiceId = getVoiceIndex(chatMessage.getName());
        else this.voiceId = voiceId;
    }
    public TTSMessage(ChatMessage chatMessage) {
        super(chatMessage.getMessageNode(), chatMessage.getType(), chatMessage.getName(), chatMessage.getMessage(), chatMessage.getSender(), chatMessage.getTimestamp());
        this.voiceId = getVoiceIndex(chatMessage.getName());
    }

    public int getVoiceId() {return voiceId;}
//    public int getVoiceIndex() {
//        int hashCode = getName().hashCode();
//        return Math.abs(hashCode) % config.MAX_VOICES;
//    }
    public static int getVoiceIndex(String name){
        int hashCode = name.hashCode();
        return Math.abs(hashCode) % config.MAX_VOICES;
    }

}