package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.enums.Locations;
import dev.phyce.naturalspeech.tts.DownloadManager;
import dev.phyce.naturalspeech.tts.TTSEngine;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Player;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.ui.ClientToolbar;

import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import java.awt.image.BufferedImage;
import java.io.IOException;

@Slf4j
@PluginDescriptor(
	name = "Natural Speech"
)
public class NaturalSpeechPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Client client;
	@Inject
	private NaturalSpeechConfig config;
	private TTSEngine tts;
	private boolean started = false;
	private String ttsEngineLocation = "";
	private NaturalSpeechPanel panel;
	private NavigationButton navButton;
	private DownloadManager downloads;

	@Override
	protected void startUp() {
		String directoryPath = "./";
		downloads = DownloadManager.getInstance(directoryPath);

		System.out.println("Starting up");
		System.out.println(getClass());
		System.out.println(getClass());

		panel = new NaturalSpeechPanel(configManager);
		System.out.println("Current working directory: " + System.getProperty("user.dir"));
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		startTTS();
		started = true;
		log.info("TTS engine initialised");
		new Thread(this::statusUpdates).start();
	}

	public void statusUpdates() {
		float lastProgress = 0;
		while (started) {
			try {
				Thread.sleep(500);
				float progress = downloads.getFileProgress();
//				log.info("file progress");
//				log.info(String.valueOf(progress));
				if(lastProgress != progress) {
					if (panel != null ) panel.updateModelSegment();
					lastProgress = progress;
				}

			} catch (InterruptedException e) {return;}
        }
	}
	public void startTTS() {
		try {
			tts = new TTSEngine(config.ttsEngine(),"C:\\piper\\voices\\piper-voices\\en\\en_US\\libritts\\high\\en_US-libritts-high.onnx", config.shortenedPhrases());
		} catch (IOException | LineUnavailableException e) {
			log.info(e.getMessage());
		}
	}	@Override
	protected void shutDown() {
		started = false;
		tts.shutDown();
		clientToolbar.removeNavigation(navButton);
	}
	@Subscribe
	protected void onChatMessage(ChatMessage message) {
		if( config.muteGrandExchange() && positionInArea(Locations.GRAND_EXCHANGE)) {
			tts.clearQueues();
			return;
		}

		switch(message.getType()) {
			case PUBLICCHAT:
				if (!config.publicChat())return;
				break;

			case PRIVATECHAT:
				if (!config.privateChat())return;
				break;

			case PRIVATECHATOUT:
				if (!config.privateOutChat())return;
				break;

			case FRIENDSCHAT:
				if (!config.friendsChat())return;
				break;

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				if (!config.examineChat())return;

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

		int voiceId = getVoiceId(message);
		int distance = getSoundDistance(message);

		try {
			//System.out.println(message);
			tts.speak(message, voiceId, distance);
		} catch(IOException e) {
			log.info(e.getMessage());
		}
	}

	protected int getVoiceId(ChatMessage message) {
		//log.info(String.valueOf(config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName())));
		switch(message.getType()) {
			//case ITEM_EXAMINE:
			//case NPC_EXAMINE:
			//case OBJECT_EXAMINE:
			//	if (config.usePersonalVoice())return config.personalVoice();
			//	break;

			case PRIVATECHATOUT:
				if (config.usePersonalVoice())return config.personalVoice();
				break;
		}

		if(config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName())) return config.personalVoice();

		return -1;
	}
	protected int getSoundDistance(ChatMessage message) {
		if (message.getType() == ChatMessageType.PUBLICCHAT && config.distanceFade()) return getPlayerDistance(message.getName());
		return 0;
	}
	protected boolean positionInArea(Locations location) {
		WorldPoint position = client.getLocalPlayer().getWorldLocation();
		WorldPoint from = location.getStart();
		WorldPoint to = location.getEnd();

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
			if (player != null && player.getName() != null && Text.sanitize(player.getName()).equals(sanitized)) return player;
		}
		return null;
	}
	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
