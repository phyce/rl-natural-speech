package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.enums.Locations;
import net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.tts.TTSEngine;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

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
			tts = new TTSEngine("C:\\piper\\voices\\piper-voices\\en\\en_US\\libritts\\high\\en_US-libritts-high.onnx", config.shortenedPhrases());
		} catch (IOException | LineUnavailableException e) {
			log.info(e.getMessage());
		}
		log.info("TTS engine initialised");
	}

	@Override
	protected void shutDown() {
		try {tts.shutDown();}
		catch (IOException e) {}
	}
	@Subscribe
	protected void onChatMessage(ChatMessage message) {
		if( config.muteGrandExchange() && playerInArea(Locations.GRAND_EXCHANGE)){
			//Options to allow friend/clan messages in grand exchange (and those types are chosen to be enabled
			//Only if mute grand exchange is on
			tts.clearQueues();
			return;
		}
		//log.info(message.toString());

		int voiceId = -1;
		int distance = 0;

		switch(message.getType()) {
			case PUBLICCHAT:
				if (!config.publicChat())return;
				distance = getPlayerDistance(message.getName());
				break;

			case PRIVATECHAT:
				if (!config.privateChat())return;
				break;

			case PRIVATECHATOUT:
				if (!config.privateOutChat())return;
				break;

			case FRIENDSCHAT:
				if (!config.friendsChat())return;
//				fetchDistance = true;
				break;

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				if (!config.examineChat())return;

				if (config.usePersonalVoice())voiceId = config.personalVoice();
				message.setName(client.getLocalPlayer().getName());
				break;

			case CLAN_CHAT:
				if (!config.clanChat())return;
				break;

			case CLAN_GUEST_CHAT:
				if (!config.clanGuestChat())return;
				break;

			case DIALOG:
				if(!config.dialog())return;
				String[] parts = message.getMessage().split("\\|", 2);
				if (parts.length == 2) {
					message.setMessage(parts[1]);
					message.setName(parts[0]);
				}
				break;

			default: return;
		}
		log.info("player that sent message is x tiles away: ");
		log.info(String.valueOf(distance));

		log.info(String.valueOf(config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName())));

		if(config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName())) {
			voiceId = config.personalVoice();
		}

		try {
			if(config.distanceFade())tts.speak(message, voiceId, getPlayerDistance(message.getName()));
			else tts.speak(message, voiceId, 0);

			log.info(message.toString());
		} catch(IOException e) {
			log.info(e.getMessage());
		}
	}
	protected boolean playerInArea(Locations location) {
		return playerInArea(location.getStart(), location.getEnd());
	}
	protected boolean playerInArea(WorldPoint from, WorldPoint to) {
		WorldPoint position = client.getLocalPlayer().getWorldLocation();

		int minX = Math.min(from.getX(), to.getX());
		int maxX = Math.max(from.getX(), to.getX());
		int minY = Math.min(from.getY(), to.getY());
		int maxY = Math.max(from.getY(), to.getY());

		return position.getX() >= minX && position.getX() <= maxX
				&& position.getY() >= minY && position.getY() <= maxY;
	}

	public int getPlayerDistance(String username) {
		Player localPlayer = client.getLocalPlayer();
		Player targetPlayer = getPlayerFromUsername(username);

		if (localPlayer == null || targetPlayer == null) return 0;

		return localPlayer.getWorldLocation().distanceTo(targetPlayer.getWorldLocation());
	}

	private Player getPlayerFromUsername(String username) {
		String sanitized = Text.sanitize(username);
		for (Player player : client.getCachedPlayers()) {
			if (player != null && player.getName() != null && Text.sanitize(player.getName()).equals(sanitized)) {
				return player;
			}
		}
		return null;
	}

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager){
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
