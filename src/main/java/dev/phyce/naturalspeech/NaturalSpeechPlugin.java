package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts.TTSEngine;

import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;

@Slf4j
@PluginDescriptor(
	name = "Natural Speech"
)
public class NaturalSpeechPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private NaturalSpeechConfig config;

	private TTSEngine tts;

	@Override
	protected void startUp() {
		try {
			tts = new TTSEngine("C:\\piper\\voices\\piper-voices\\en\\en_US\\libritts\\high\\en_US-libritts-high.onnx");
		} catch (IOException | LineUnavailableException e) {
			log.info("NaturalSpeech failed to start engine//'/'/'/'/'/'/'/'/'/'/'/'/'/'/'/'/'/'/");
			log.info(e.getMessage());
		}

		log.info("TTS engine initialised");
	}

	@Override
	protected void shutDown() {
		try {
			tts.shutDown();
			log.info("NaturalSpeech stopped!");
		} catch (IOException e) {
			log.info("NaturalSpeech failed to stop");
		}
	}

	@Subscribe
	protected void onChatMessage(ChatMessage message) {
		if ( message.getType() == ChatMessageType.PUBLICCHAT ) {
			try {
				log.info(message.toString());
//				log.info("sending message to queue");
				tts.speak(message);
			} catch(IOException e) {
				log.info("Failed to speak message");
				log.info(e.getMessage());
			}
		}
	}


	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
