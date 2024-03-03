package dev.phyce.naturalspeech;

import dev.phyce.naturalspeech.common.CustomMenuEntry;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.enums.Locations;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;

import com.google.inject.Provides;
import dev.phyce.naturalspeech.tts.TTSManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import dev.phyce.naturalspeech.common.PlayerCommon;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.ClientToolbar;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@PluginDescriptor(
		name = "Natural Speech"
)
public class NaturalSpeechPlugin extends Plugin {
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Client client;
	@Inject
	private NaturalSpeechConfig config;

	@Getter
	private TTSManager tts = null;

	@Getter
	private final Set<String> allowList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	@Getter
	private final Set<String> blockList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	@Getter
	@Inject
	private Downloader downloader;

	@Inject
	private VoiceRepository voiceRepository;

	@Inject
	private Provider<TopLevelPanel> topLevelPanelProvider;


	@Getter
	private TopLevelPanel topLevelPanel;

	private boolean started = false;
	private NavigationButton navButton;

	@Override
	protected void startUp() {
		try {
			voiceRepository.downloadPiperVoice("en_GB-vctk-medium");
			voiceRepository.downloadPiperVoice("en_US-libritts-high");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		PlayerCommon playerCommon = injector.getInstance(PlayerCommon.class);
		topLevelPanel = topLevelPanelProvider.get();
		// create downloader

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(topLevelPanel)
				.build();

		clientToolbar.addNavigation(navButton);

//		if (config.autoStart())startTTS();

		log.info("NaturalSpeech TTS engine started");
	}

	public void startTTS() throws RuntimeException {
		started = true;

		Path ttsPath = Path.of(config.ttsEngine());
		Path voicePath = ttsPath.resolveSibling(Settings.voiceFolderName).resolve(Settings.voiceFilename);

		// check if tts_path points to existing file and is a valid executable
		if (!ttsPath.toFile().exists() || !ttsPath.toFile().canExecute()) {
			log.error("Invalid TTS engine path.");
			throw new RuntimeException("Invalid TTS engine path");
		}
		tts = new TTSManager(ttsPath, config.shortenedPhrases());
		tts.startVoiceModel(voicePath);
		//tts = new TTSEngine(tts_path, voice_path, config.shortenedPhrases());
	}

	public void stopTTS() {
		started = false;
		tts.shutDown();
	}

	public void statusUpdates() {
//		boolean ttsRunning = false;
//		while (started) {
//			try {
//				Thread.sleep(500);
//
//				if(tts != null) {
//					if(ttsRunning != tts.isProcessing()) {
//						ttsRunning = tts.isProcessing();
//
//						if(ttsRunning) {
//                            panel.updateStatus(2);
//                        } else {
//                            panel.updateStatus(1);
//                        }
//					}
//				}
//
//			} catch (InterruptedException e) {return;}
//        }
	}

	@Override
	protected void shutDown() {
		started = false;
		if (tts != null) {
			tts.shutDown();
		}
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	protected void onChatMessage(ChatMessage message) {
		if (!started) {
			return;
		}
		if (tts == null || !tts.isActive()) {
			return;
		}
		if (config.muteGrandExchange() && positionInArea(Locations.GRAND_EXCHANGE)) {
			return;
		}

		ChatMessageType messageType = message.getType();

		switch (messageType) {
			case PUBLICCHAT:
				if (!config.publicChat()) return;
				break;
			case PRIVATECHAT:
				if (!config.privateChat()) return;
				break;
			case PRIVATECHATOUT:
				if (!config.privateOutChat()) return;
				break;
			case FRIENDSCHAT:
				if (!config.friendsChat()) return;
				break;
			case CLAN_CHAT:
				if (!config.clanChat()) return;
				break;
			case CLAN_GUEST_CHAT:
				if (!config.clanGuestChat()) return;
				break;

			default:
				break;
		}

		switch (messageType) {
			case PUBLICCHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
				if(!PlayerCommon.getAllowList().isEmpty() && !PlayerCommon.getAllowList().contains(message.getName())) return;
				if(!PlayerCommon.getBlockList().isEmpty() && PlayerCommon.getBlockList().contains(message.getName())) return;
				break;

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				if (!config.examineChat()) return;

				message.setName(client.getLocalPlayer().getName());
				break;

			case DIALOG:
				if (!config.dialog()) return;
				String[] parts = message.getMessage().split("\\|", 2);
				if (parts.length == 2) {
					message.setMessage(parts[1]);
					message.setName(parts[0]);
				}
				break;

			default:
				return;
		}

		//Feels like this could be inside the conditional below too. Not sure where it should go yet.
		if (config.muteSelf() && message.getName().equals(client.getLocalPlayer().getName())) {
			return;
		}
		if (!messageType.equals(ChatMessageType.DIALOG)) {
			if (config.muteOthers() && !message.getName().equals(client.getLocalPlayer().getName())) {
				return;
			}
		}

		if (config.muteLevelThreshold() > PlayerCommon.getLevel(message.getName())) {
			if (!client.getLocalPlayer().getName().equals(message.getName()) &&
					!Arrays.asList(
							ChatMessageType.DIALOG,
							ChatMessageType.PRIVATECHATOUT,
							ChatMessageType.ITEM_EXAMINE,
							ChatMessageType.NPC_EXAMINE,
							ChatMessageType.OBJECT_EXAMINE
					).contains(messageType)) {
				return;
			}
		}

		int voiceId = getVoiceId(message);
		int distance = getSoundDistance(message);

		try {
			System.out.println(message);
			tts.speak(message, voiceId, distance);
		} catch (IOException e) {
			log.info(e.getMessage());
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		final MenuEntry[] entries = event.getMenuEntries();

		Set<Integer> interfaces = new HashSet<>();
		interfaces.add(InterfaceID.FRIEND_LIST);
		interfaces.add(InterfaceID.FRIENDS_CHAT);
		interfaces.add(InterfaceID.CHATBOX);
		interfaces.add(InterfaceID.PRIVATE_CHAT);
		interfaces.add(InterfaceID.GROUP_IRON);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (entry.getType() == MenuAction.PLAYER_EIGHTH_OPTION) drawOptions(entry, index);
			else if (interfaces.contains(groupId)/* && entry.getOption() == "Report"*/) {
				if (entry.getOption().equals("Report")) drawOptions(entry, index);
			}
		}
	}
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if(event.getGroup().equals("naturalSpeech")) {
			switch(event.getKey()) {
				case "muteSelf":
					tts.clearPlayerAudio(PlayerCommon.getUsername());
					break;

				case "muteOthers":
					tts.focusOnPlayer(PlayerCommon.getUsername());
					break;
			}
		}
	}
	public synchronized void drawOptions(MenuEntry entry, int index) {
		String regex = "<col=[0-9a-f]+>([^<]+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(entry.getTarget());

		matcher.find();
		String username = matcher.group(1).trim();

		String status;
		if(PlayerCommon.isBeingListened(username)) {
			status = "<col=78B159>O";
		} else {
			status = "<col=DD2E44>0";
		}

		CustomMenuEntry muteOptions = new CustomMenuEntry(String.format("%s <col=ffffff>TTS <col=ffffff>(%s) <col=ffffff>>", status, username), index);

		if(PlayerCommon.isBeingListened(username)) {
			if(!PlayerCommon.getAllowList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Stop listening", -1, function -> {
					PlayerCommon.unlisten(username);
				}));
			} else {
				muteOptions.addChild(new CustomMenuEntry("Mute", -1, function -> {
					PlayerCommon.mute(username);
				}));
			}

			if(PlayerCommon.getAllowList().isEmpty() && PlayerCommon.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Mute others", -1, function -> {
					PlayerCommon.listen(username);
					tts.focusOnPlayer(username);
				}));
			}
		}
		else {
			if(!PlayerCommon.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Unmute", -1, function -> {
					PlayerCommon.unmute(username);
				}));
			} else {
				muteOptions.addChild(new CustomMenuEntry("Listen", -1, function -> {
					PlayerCommon.listen(username);
				}));
			}
		}

		if(! PlayerCommon.getBlockList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear block list", -1, function -> {
				PlayerCommon.getBlockList().clear();
			}));
		} else if (!PlayerCommon.getAllowList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear allow list", -1, function -> {
				PlayerCommon.getAllowList().clear();
			}));
		}
		muteOptions.addTo(client);
	}


	protected int getVoiceId(ChatMessage message) {
		//log.info(String.valueOf(config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName())));
		switch (message.getType()) {
			//case ITEM_EXAMINE:
			//case NPC_EXAMINE:
			//case OBJECT_EXAMINE:
			//	if (config.usePersonalVoice())return config.personalVoice();
			//	break;

			case PRIVATECHATOUT:
				if (config.usePersonalVoice()) return config.personalVoice();
				break;
		}

		if (config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName()))
			return config.personalVoice();

		return -1;
	}

	protected int getSoundDistance(ChatMessage message) {
		if (message.getType() == ChatMessageType.PUBLICCHAT && config.distanceFade())
			return PlayerCommon.getDistance(message.getName());
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



	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
