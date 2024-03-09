package dev.phyce.naturalspeech;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import dev.phyce.naturalspeech.downloader.Downloader;
import static dev.phyce.naturalspeech.enums.Locations.*;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.helpers.CustomMenuEntry;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.uservoiceconfigs.VoiceID;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import dev.phyce.naturalspeech.utils.TextUtil;
import lombok.Getter;
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

import javax.sound.sampled.LineUnavailableException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;


@Slf4j
@PluginDescriptor(name = CONFIG_GROUP)
public class NaturalSpeechPlugin extends Plugin {
	//<editor-fold desc="> Misc">
	public final static String CONFIG_GROUP = "NaturalSpeech";
	public final static String MODEL_REPO_FILENAME = "model_repository.json";
	public final static String MODEL_FOLDER_NAME = "models";
	//</editor-fold>

	//<editor-fold desc="> RuneLite Dependencies">
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Client client;
	@Inject
	private NaturalSpeechConfig config;
	//</editor-fold>

	//<editor-fold desc="> Internal Dependencies">
	@Inject
	private NaturalSpeechRuntimeConfig runtimeConfig;
	@Inject
	private ModelRepository modelRepository;
	@Getter
	@Inject
	private TextToSpeech textToSpeech;
	@Inject
	private Provider<TopLevelPanel> topLevelPanelProvider;
	//</editor-fold>

	//<editor-fold desc="> Runtime Variables">
	@Getter
	private TopLevelPanel topLevelPanel;
	private Map<String, String> shortenedPhrases;
	private NavigationButton navButton;
	//</editor-fold>
	public void startTextToSpeech() throws RuntimeException, IOException, LineUnavailableException {
		// FIXME(Louis) Need to modify to load in all ModelLocal configured
		ModelRepository.ModelLocal librittsLocal = modelRepository.getModelLocal("libritts");

		Path piperPath = runtimeConfig.getPiperPath();

		if (!piperPath.toFile().exists() || !piperPath.toFile().canExecute()) {
			log.error("Invalid Piper exectuable path: {}", piperPath);
			throw new RuntimeException("Invalid Piper executable path " + piperPath);
		}

		// FIXME(Louis) Lazy load with new MainSettingsPanel, load with multiple models based on user config
		textToSpeech.startPiperForModelLocal(librittsLocal);
	}

	public void stopTextToSpeech() {
		textToSpeech.shutDownAllPipers();
	}
	//<editor-fold desc="> Override Methods">
	@Override
	public void configure(Binder binder) {
		// Instantiate PluginHelper early, Plugin relies on static PluginHelper::Instance
		// No cycling-dependencies back at NaturalSpeechPlugin allowed
		binder.bind(PluginHelper.class).asEagerSingleton();
		// Downloader has all dependencies from RuneLite, eager load
		binder.bind(Downloader.class).asEagerSingleton();
	}

	@Override
	protected void startUp() {
		// Have to lazy-load config panel after RuneLite UI is initialized, cannot field @Inject
		topLevelPanel = topLevelPanelProvider.get();

		// FIXME(Louis) Change to user controlled lazy loading
//		try {
////			modelRepository.getModelLocal("en_GB-vctk-medium");
////			modelRepository.getModelLocal("en_US-libritts-high");
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}


		// Build navButton
		{
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
			navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(topLevelPanel)
				.build();
			clientToolbar.addNavigation(navButton);
		}

		// Load ShortenedPhrases is a method that can be called later when configs are changed
		loadShortenedPhrases();

		log.info("NaturalSpeech TTS engine started");
	}

	@Override
	protected void shutDown() {
		if (textToSpeech != null) textToSpeech.shutDownAllPipers();
		clientToolbar.removeNavigation(navButton);

		textToSpeech.saveVoiceConfig();
	}
	//</editor-fold>

	//<editor-fold desc="> Hooks">
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
			else if (interfaces.contains(groupId) && entry.getOption().equals("Report")) drawOptions(entry, index);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals(CONFIG_GROUP)) {
			switch (event.getKey()) {
				case "muteSelf":
					textToSpeech.clearPlayerAudioQueue(getClientUsername());
					break;

				case "muteOthers":
					textToSpeech.clearOtherPlayersAudioQueue(getClientUsername());
					break;
				case "shortenedPhrases":
					loadShortenedPhrases();
					break;
			}
		}
	}
	//</editor-fold>

	@Subscribe
	protected void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (textToSpeech.activePiperInstanceCount() == 0) return;
		if (message.getType() == ChatMessageType.AUTOTYPER) return;

		patchChatMessageMissingName(message);

		if (isMessageTypeDisabledInConfig(message)
				|| checkMuteAllowAndBlockList(message)
				|| isAreaDisabled()
				|| isSelfMuted(message)
				|| isMutingOthers(message)
				|| checkMuteLevelThreshold(message)) {
			return;
		}

		String text = isPlayerChatMessage(message) ? expandShortenedPhrases(message.getMessage()) : message.getMessage();
		VoiceID[] voiceIDs = textToSpeech.getModelAndVoiceFromChatMessage(message);
		int distance = getSpeakerDistance(message);
		//TODO: I feel like this could be simplified
//		System.out.println(message);
		textToSpeech.speak(
			textToSpeech.getModelAndVoiceFromChatMessage(message)[0],
			text.toLowerCase(),
			distance,
			message.getName()
		);
	}

	//<editor-fold desc="> ChatMessage">
	public boolean isMessageTypeDisabledInConfig(ChatMessage message) {
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
	public int getSpeakerDistance(ChatMessage message) {
		if (message.getType() == ChatMessageType.PUBLICCHAT && config.distanceFadeEnabled()) {
			return getDistance(message.getName());
		}
		return 0;
	}
	private boolean isSelfMuted(ChatMessage message) {
		if (config.muteSelf() && message.getName().equals(client.getLocalPlayer().getName())) return true;
		return false;
	}
	private boolean isMutingOthers(ChatMessage message) {
		if (isNPCChatMessage(message)) return false;

		return config.muteOthers() && !message.getName().equals(client.getLocalPlayer().getName());
	}
	private boolean checkMuteLevelThreshold(ChatMessage message) {
		if (isNPCChatMessage(message)) return false;
		if (Objects.equals(client.getLocalPlayer().getName(), message.getName())) return false;
		if (message.getType() == ChatMessageType.PRIVATECHAT) return false;
		if (getLevel(message.getName()) < config.muteLevelThreshold()) return true;


		return false;
	}
	// FIXME Implement voice getter

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
	//</editor-fold>

	//<editor-fold desc="> Other">
	public String expandShortenedPhrases(String text) {
		return TextUtil.expandShortenedPhrases(text, shortenedPhrases);
	}

	private boolean isAreaDisabled() {
		if (client.getLocalPlayer() == null) return false;
		//noinspection RedundantIfStatement
		if (config.muteGrandExchange() && inGrandExchange(client.getLocalPlayer().getWorldLocation())) return true;

		return false;
	}

	public synchronized void drawOptions(MenuEntry entry, int index) {
		String regex = "<col=[0-9a-f]+>([^<]+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(entry.getTarget());

		matcher.find();
		String username = matcher.group(1).trim();

		String status;
		if (isBeingListened(username)) status = "<col=78B159>O";
		else status = "<col=DD2E44>0";

		CustomMenuEntry muteOptions = new CustomMenuEntry(String.format("%s <col=ffffff>TTS <col=ffffff>(%s) <col=ffffff>>", status, username), index);

		if (isBeingListened(username)) {
			if (!getAllowList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Stop listening", -1, function -> {
					unlisten(username);
				}));
			} else {
				muteOptions.addChild(new CustomMenuEntry("Mute", -1, function -> {
					mute(username);
				}));
			}
			if (getAllowList().isEmpty() && PluginHelper.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Mute others", -1, function -> {
					listen(username);
					textToSpeech.clearOtherPlayersAudioQueue(username);
				}));
			}
		} else {
			if (!PluginHelper.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Unmute", -1, function -> {
					unmute(username);
				}));
			} else {
				muteOptions.addChild(new CustomMenuEntry("Listen", -1, function -> {
					listen(username);
				}));
			}
		}

		if (!getBlockList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear block list", -1, function -> {
				getBlockList().clear();
			}));
		} else if (!getAllowList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear allow list", -1, function -> {
				getAllowList().clear();
			}));
		}
		muteOptions.addTo(client);
	}

	// In method so we can load again when user changes config
	public void loadShortenedPhrases() {
		String phrases = config.shortenedPhrases();
		shortenedPhrases = new HashMap<>();
		String[] lines = phrases.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=", 2);
			if (parts.length == 2) shortenedPhrases.put(parts[0].trim(), parts[1].trim());
		}
	}



	//</editor-fold>

	// FIXME(Louis) Implement new status update in new MainSettingsPanel
	//	public void statusUpdates() {
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
	//	}


	@Override
	public void resetConfiguration() {
		runtimeConfig.reset();
	}

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}

}
