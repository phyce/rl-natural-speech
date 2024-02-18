package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.enums.Locations;
import dev.phyce.naturalspeech.tts.DownloadManager;
import dev.phyce.naturalspeech.tts.TTSEngine;

import com.google.inject.Provides;
import lombok.Getter;
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
import java.nio.file.Path;

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
	@Getter
	private TTSEngine tts;
	private boolean started = false;
	private String ttsEngineLocation = "";
	private NaturalSpeechPanel panel;
	private NavigationButton navButton;
	private DownloadManager downloads;

	@Override
	protected void startUp() {
		String directoryPath = "tts/";
		downloads = DownloadManager.getInstance(directoryPath);

		panel = new NaturalSpeechPanel(configManager, this);
		//System.out.println("Current working directory: " + System.getProperty("user.dir"));
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		log.info("TTS plugin initialised");
	}
	public void startTTS() {
		try {
			started = true;
			new Thread(this::statusUpdates).start();
			Path voiceModelPath = downloads.getDownloadPath().toAbsolutePath();
			System.out.println(voiceModelPath);
			tts = new TTSEngine(config.ttsEngine(), voiceModelPath.toString() + "\\en_US-libritts-high.onnx", config.shortenedPhrases());
		} catch (IOException | LineUnavailableException e) {
			log.info(e.getMessage());
		}
	}

	public void stopTTS() {
		started = false;
		tts.shutDown();
	}
	public void statusUpdates() {
		float lastProgress = 0;
		boolean ttsRunning = false;
		while (started) {
			try {
				Thread.sleep(500);

				float progress = downloads.getFileProgress();
				System.out.println("Checking for status updates");
				System.out.println(lastProgress);
				System.out.println(progress);

				if(lastProgress != progress) {
					System.out.println("progress changing!!!");
					if (panel != null ) panel.updateModelSegment();
					lastProgress = progress;
				}

				if(tts != null) {
					if(ttsRunning != tts.isProcessing()) {
						ttsRunning = tts.isProcessing();

						if(ttsRunning)panel.updateStatus(2);
						else panel.updateStatus(1);
					}
				}

			} catch (InterruptedException e) {return;}
        }
	}
	@Override
	protected void shutDown() {
		started = false;
		tts.shutDown();
		clientToolbar.removeNavigation(navButton);
	}
	@Subscribe
	protected void onChatMessage(ChatMessage message) {
		if(!started) return;
		if(tts == null || !tts.isProcessing()) return;

		if( config.muteGrandExchange() && positionInArea(Locations.GRAND_EXCHANGE)) {
			tts.clearQueues();
			return;
		}

		switch(message.getType()) {
			case PUBLICCHAT: if (!config.publicChat())return; break;
			case PRIVATECHAT:if (!config.privateChat())return; break;
			case PRIVATECHATOUT:if (!config.privateOutChat())return; break;
			case FRIENDSCHAT:if (!config.friendsChat())return; break;
			case CLAN_CHAT: if (!config.clanChat())return; break;
			case CLAN_GUEST_CHAT: if (!config.clanGuestChat())return; break;

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				if (!config.examineChat())return;

				message.setName(client.getLocalPlayer().getName());
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
			System.out.println(message);
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
