package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import com.google.inject.Provides;
import dev.phyce.naturalspeech.common.CustomMenuEntry;
import dev.phyce.naturalspeech.common.PlayerCommon;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.enums.Locations;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.phyce.naturalspeech.Settings.CONFIG_GROUP;
import static net.runelite.client.config.RuneLiteConfig.GROUP_NAME;

@Slf4j
@PluginDescriptor(
		name = CONFIG_GROUP
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
	@Inject
	private RuntimeConfig runtimeConfig;

	@Getter
	private TextToSpeech textToSpeech;

	private boolean started = false;
	private NavigationButton navButton;
	@Getter
	@Inject
	private Downloader downloader;

	@Inject
	private ModelRepository modelRepository;

	private Map<String, String> shortenedPhrases;

	@Getter
	private TopLevelPanel topLevelPanel;

	private static boolean isPlayerChatMessage(ChatMessage message) {
		return !isNPCChatMessage(message);
	}

	private static boolean isNPCChatMessage(ChatMessage message) {
		// From NPC
		switch (message.getType()) {
			case DIALOG:
			case PRIVATECHAT:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				return true;
		}
		return false;
	}

	private static boolean checkMuteAllowAndBlockList(ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
				if (!PlayerCommon.getAllowList().isEmpty() && !PlayerCommon.getAllowList().contains(message.getName()))
					return true;
				if (!PlayerCommon.getBlockList().isEmpty() && PlayerCommon.getBlockList().contains(message.getName()))
					return true;
		}
		return false;
	}

	@Override
	protected void startUp() {
		try {
			modelRepository.getModelLocal("en_GB-vctk-medium");
			modelRepository.getModelLocal("en_US-libritts-high");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		PlayerCommon playerCommon = injector.getInstance(PlayerCommon.class);
		topLevelPanel = injector.getInstance(TopLevelPanel.class);
		textToSpeech = injector.getInstance(TextToSpeech.class);


		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(topLevelPanel)
				.build();

		clientToolbar.addNavigation(navButton);

		loadShortenedPhrases();

		log.info("NaturalSpeech TTS engine started");
	}

	@SneakyThrows // FIXME(Louis) temporary sneaky throw for modelRepository.
	public void startTTS() throws RuntimeException {
		started = true;

		// FIXME(Louis) Need to modify to load in all ModelLocal configured
		ModelRepository.ModelLocal librittsLocal = modelRepository.getModelLocal("libritts");

		Path ttsPath = runtimeConfig.getPiperPath();
		Path voicePath = librittsLocal.onnx.toPath();


		// check if tts_path points to existing file and is a valid executable
		if (!ttsPath.toFile().exists() || !ttsPath.toFile().canExecute()) {
			log.error("Invalid TTS engine path.");
			throw new RuntimeException("Invalid TTS engine path");
		}
		textToSpeech.startPiperForModelLocal(librittsLocal);
		//tts = new TTSEngine(tts_path, voice_path, config.shortenedPhrases());
	}

	public void stopTTS() {
		started = false;
		textToSpeech.shutDownAllPipers();
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
		if (textToSpeech != null) {
			textToSpeech.shutDownAllPipers();
		}
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	protected void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (!isPiperReady()) {
			return;
		}

		// Patch message with correct name, message is passed through reference and modified
		patchChatMessageMissingName(message);

		if (isMessageTypeDisabledInConfig(message)) {
			return;
		}

		if (checkMuteAllowAndBlockList(message)) {
			return;
		}

		if (isAreaDisabled()) {
			return;
		}

		if (checkMuteSelf(message)) {
			return;
		}

		if (checkMuteOtherPlayers(message)) {
			return;
		}

		if (checkMuteLevelThreshold(message)) {
			return;
		}

		String text;
		// expand short phrases for player chat
		if (isPlayerChatMessage(message)) {
			text = expandShortenedPhrases(message.getMessage());
		} else {
			// use original for NPC dialog
			text = message.getMessage();
		}

		VoiceID voiceID = getModelAndVoiceFromChatMessage(message);
		int distance = getSoundDistance(message);
		textToSpeech.speak(voiceID, text, distance, message.getName());
	}

	public String expandShortenedPhrases(String text) {
		return TextToSpeechUtil.expandShortenedPhrases(text, shortenedPhrases);
	}

	/**
	 * EXAMINE has null for name field<br>
	 * DIALOG has name in `name|message` format with null for name field<br>
	 * <p>
	 * This method takes in message reference and patches the name field with correct value<br>
	 *
	 * @param message reference passed in and modified
	 */
	private void patchChatMessageMissingName(ChatMessage message) {
		switch (message.getType()) {
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				message.setName(client.getLocalPlayer().getName());
				break;
			case DIALOG:
				String[] parts = message.getMessage().split("\\|", 2);
				if (parts.length == 2) {
					message.setName(parts[0]);
					message.setMessage(parts[1]);
				} else {
					throw new RuntimeException("Unknown NPC dialog format: " + message.getMessage());
				}
				break;
		}
	}

	private boolean checkMuteSelf(ChatMessage message) {
		if (config.muteSelf() && message.getName().equals(client.getLocalPlayer().getName())) {
			return true;
		}
		return false;
	}

	private boolean checkMuteOtherPlayers(ChatMessage message) {
		if (isNPCChatMessage(message)) {
			return false;
		}

		return config.muteOthers() && !message.getName().equals(client.getLocalPlayer().getName());
	}

	private boolean checkMuteLevelThreshold(ChatMessage message) {
		if (isNPCChatMessage(message)) return false;

		// Local Player Don't mute
		if (Objects.equals(client.getLocalPlayer().getName(), message.getName())) {
			return false;
		}

		// Is player and level is lower then threshold
		if (PlayerCommon.getLevel(message.getName()) < config.muteLevelThreshold()) {
			return true;
		}

		return false;
	}

	private boolean isPiperReady() {
		return started && textToSpeech.isAnyPiperRunning();
	}

	private boolean isAreaDisabled() {
		//noinspection RedundantIfStatement
		if (config.muteGrandExchange() && Locations.isGrandExchange(client.getLocalPlayer().getWorldLocation())) {
			return true;
		}
		// ... other areas

		return false;
	}

	private boolean isMessageTypeDisabledInConfig(ChatMessage message) {
		switch (message.getType()) {
			case PUBLICCHAT:
				if (!config.publicChatEnabled()) return true;
				break;
			case PRIVATECHAT:
				if (!config.privateChatEnabled()) return true;
				break;
			case PRIVATECHATOUT:
				if (!config.privateOutChatEnabled()) return true;
				break;
			case FRIENDSCHAT:
				if (!config.friendsChatEnabled()) return true;
				break;
			case CLAN_CHAT:
				if (!config.clanChatEnabled()) return true;
				break;
			case CLAN_GUEST_CHAT:
				if (!config.clanGuestChatEnabled()) return true;
				break;
			case OBJECT_EXAMINE:
				if (!config.examineChatEnabled()) return true;
				break;
			case DIALOG:
				if (!config.dialogEnabled()) return true;
				break;
		}

		return false;
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
		if (event.getGroup().equals(GROUP_NAME)) {
			switch (event.getKey()) {
				case "muteSelf":
					textToSpeech.clearPlayerAudioQueue(PlayerCommon.getUsername());
					break;

				case "muteOthers":
					textToSpeech.clearOtherPlayersAudioQueue(PlayerCommon.getUsername());
					break;
				case "shortenedPhrases":
					// load in new shortened phrases
					loadShortenedPhrases();
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
		if (PlayerCommon.isBeingListened(username)) {
			status = "<col=78B159>O";
		} else {
			status = "<col=DD2E44>0";
		}

		CustomMenuEntry muteOptions = new CustomMenuEntry(String.format("%s <col=ffffff>TTS <col=ffffff>(%s) <col=ffffff>>", status, username), index);

		if (PlayerCommon.isBeingListened(username)) {
			if (!PlayerCommon.getAllowList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Stop listening", -1, function -> {
					PlayerCommon.unlisten(username);
				}));
			} else {
				muteOptions.addChild(new CustomMenuEntry("Mute", -1, function -> {
					PlayerCommon.mute(username);
				}));
			}

			if (PlayerCommon.getAllowList().isEmpty() && PlayerCommon.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Mute others", -1, function -> {
					PlayerCommon.listen(username);
					textToSpeech.clearOtherPlayersAudioQueue(username);
				}));
			}
		} else {
			if (!PlayerCommon.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Unmute", -1, function -> {
					PlayerCommon.unmute(username);
				}));
			} else {
				muteOptions.addChild(new CustomMenuEntry("Listen", -1, function -> {
					PlayerCommon.listen(username);
				}));
			}
		}

		if (!PlayerCommon.getBlockList().isEmpty()) {
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

	protected VoiceID getModelAndVoiceFromChatMessage(ChatMessage message) {
		//log.info(String.valueOf(config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName())));
//		switch (message.getType()) {
		//case ITEM_EXAMINE:
		//case NPC_EXAMINE:
		//case OBJECT_EXAMINE:
		//	if (config.usePersonalVoice())return config.personalVoice();
		//	break;

//			case PRIVATECHATOUT:
//				if (config.usePersonalVoice()) return VoiceID.fromIDString(config.personalVoice());
//				break;
//		}

//		if (config.usePersonalVoice() && client.getLocalPlayer().getName().equals(message.getName()))
//			return VoiceID.fromIDString(config.personalVoice());

		// FIXME Implement voice getter
		return VoiceID.fromIDString(config.personalVoiceID());
	}

	protected int getSoundDistance(ChatMessage message) {
		if (message.getType() == ChatMessageType.PUBLICCHAT && config.distanceFadeEnabled())
			return PlayerCommon.getDistance(message.getName());
		return 0;
	}

	// In method so we can load again when user changes config
	private void loadShortenedPhrases() {
		String phrases = config.shortenedPhrases();
		shortenedPhrases = new HashMap<>();
		String[] lines = phrases.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) shortenedPhrases.put(parts[0].trim(), parts[1].trim());
		}
	}



	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
