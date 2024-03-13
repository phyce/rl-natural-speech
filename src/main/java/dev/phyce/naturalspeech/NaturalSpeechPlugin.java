package dev.phyce.naturalspeech;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import static dev.phyce.naturalspeech.enums.Locations.inGrandExchange;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.helpers.CustomMenuEntry;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.intruments.VoiceLogger;
import dev.phyce.naturalspeech.tts.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.ui.game.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.LineUnavailableException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;


@Slf4j
@PluginDescriptor(name=CONFIG_GROUP)
public class NaturalSpeechPlugin extends Plugin {
	//<editor-fold desc="> Misc">
	public final static String CONFIG_GROUP = "NaturalSpeech";
	public final static String MODEL_REPO_FILENAME = "model_repository.json";
	public final static String MODEL_FOLDER_NAME = "models";
	public final static String VOICE_CONFIG_FILE = "speaker_config.json";
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
	private Provider<VoiceManager> voiceManagerProvider;
	@Inject
	private Provider<TextToSpeech> textToSpeechProvider;
	@Inject
	private Provider<TopLevelPanel> topLevelPanelProvider;
	@Inject
	private Provider<ModelRepository> modelRepositoryProvider;
	@Inject
	private Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;
	//</editor-fold>

	//<editor-fold desc="> Runtime Variables">
	@Getter
	private TopLevelPanel topLevelPanel;
	@Getter
	private VoiceManager voiceManager;
	@Getter
	private TextToSpeech textToSpeech;
	@Getter
	private ModelRepository modelRepository;
	private Map<String, String> shortenedPhrases;
	private NavigationButton navButton;
	private TextToSpeech.TextToSpeechListener textToSpeechListener;
	private String lastNpcDialogText = "";
	private String lastPlayerDialogText = "";
	private Actor actorInteractedWith = null;

	//</editor-fold>
	public void startTextToSpeech() throws RuntimeException, IOException, LineUnavailableException {
		//		// FIXME(Louis) Need to modify to load in all ModelLocal configured
		//		ModelRepository.ModelLocal librittsLocal = modelRepository.loadModelLocal("libritts");
		//ttsConfig
		//		Path piperPath = runtimeConfig.getPiperPath();
		//
		//		if (!piperPath.toFile().exists() || !piperPath.toFile().canExecute()) {
		//			log.error("Invalid Piper executable path: {}", piperPath);
		//			throw new RuntimeException("Invalid Piper executable path " + piperPath);
		//		}

		// FIXME(Louis) Lazy load with new MainSettingsPanel, load with multiple models based on user config
		textToSpeech.start();
	}

	public void stopTextToSpeech() {
		textToSpeech.stop();
	}

	//<editor-fold desc="> Override Methods">
	@Override
	public void configure(Binder binder) {
		// Instantiate PluginHelper early, Plugin relies on static PluginHelper::Instance
		// No cycling-dependencies back at NaturalSpeechPlugin allowed
		binder.bind(PluginHelper.class).asEagerSingleton();
		// Downloader has all dependencies from RuneLite, eager load
		binder.bind(Downloader.class).asEagerSingleton();
		binder.bind(VoiceLogger.class).asEagerSingleton();
	}

	@Override
	protected void startUp() {
		modelRepository = modelRepositoryProvider.get();
		textToSpeech = textToSpeechProvider.get();
		// Have to lazy-load config panel after RuneLite UI is initialized, cannot field @Inject
		topLevelPanel = topLevelPanelProvider.get();
		voiceManager = voiceManagerProvider.get();


		textToSpeechListener = new TextToSpeech.TextToSpeechListener() {
			@Override
			public void onPiperStart(Piper piper) {
				log.info("Plugin hears that {} has started", piper);
			}

			@Override
			public void onPiperExit(Piper piper) {
				log.info("Plugin hears that {} has exited", piper);
			}
		};

		textToSpeech.addTextToSpeechListener(
			textToSpeechListener
		);

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
		log.info("NaturalSpeech plugin has started");

		if (config.autoStart()) {
			try {
				this.startTextToSpeech();
			} catch (IOException | LineUnavailableException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	protected void shutDown() {
		if (textToSpeech != null) {
			textToSpeech.stop();
			textToSpeech.removeTextToSpeechListener(textToSpeechListener);
		}
		clientToolbar.removeNavigation(navButton);

		voiceManager.saveVoiceConfig();
		textToSpeech.saveModelConfig();

		log.info("NaturalSpeech plugin has shutDown");
	}

	@Subscribe
	private void onClientShutdown(ClientShutdown e) {
		voiceManager.saveVoiceConfig();
		textToSpeech.saveModelConfig();
	}

	@Override
	public void resetConfiguration() {
		runtimeConfig.reset();
	}
	//</editor-fold>

	//<editor-fold desc="> Hooks">
	@Subscribe(priority=-1)
	public void onOverheadTextChanged(OverheadTextChanged event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (!config.dialogEnabled()) return;

		if (event.getActor() instanceof NPC) {
			NPC npc = (NPC) event.getActor();
			int distance = PluginHelper.getNPCDistance(npc);

			VoiceID voiceID = voiceManager.getVoiceIDFromNPCId(npc.getId(), npc.getName());
			textToSpeech.speak(voiceID, event.getOverheadText(), distance, npc.getName());
		}
	}

	@Subscribe(priority=-2)
	protected void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (textToSpeech.activePiperProcessCount() == 0) return;
		if (message.getType() == ChatMessageType.AUTOTYPER) return;

		patchChatMessage(message);

		if (!isMessageProcessable(message)) return;
		int distance = getSpeakerDistance(message);

		//This can't go into patchChatMessage unfortunately
		message.setName(message.getName()
			.replaceAll("<[^>]+>", "") // Remove all tags
			.replaceAll("[\\p{C}\\p{Z}]+", " ") // Replace control characters and any kind of separator with a space
			//			.replaceAll("[^\\p{L}\\p{Nd} ]+", "") // Remove non-letter, non-digit characters except spaces
			.toLowerCase());

		message.setMessage(message.getMessage()
			.toLowerCase()
		);

		VoiceID voiceID = voiceManager.getVoiceIDFromChatMessage(message);
		textToSpeech.speak(voiceID, message.getMessage(), distance, message.getName());
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
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

			if (entry.getType() == MenuAction.PLAYER_EIGHTH_OPTION) {drawOptions(entry, index);}
			else if (entry.getType() == MenuAction.EXAMINE_NPC) {drawOptions(entry, index);}
			else if (interfaces.contains(groupId) && entry.getOption().equals("Report")) drawOptions(entry, index);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (event.getTarget() == null || event.getSource() != client.getLocalPlayer()) {
			return;
		}
		// Reset dialog text on new interactions to indicate no active dialog
		lastNpcDialogText = "";
		lastPlayerDialogText = "";

		actorInteractedWith = event.getTarget();
	}

	private int getGroupId(int component) {
		return component >> 16;
	}

	private int getChildId(int component) {
		return component & '\uffff';
	}

	@Subscribe(priority=-1)
	public void onGameTick(GameTick event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (!config.dialogEnabled() || actorInteractedWith == null) return;

		int playerGroupId = getGroupId(ComponentID.DIALOG_PLAYER_TEXT);
		//		int playerGroupId = WidgetInfo.DIALOG_PLAYER_TEXT.getGroupId();
		Widget playerDialogTextWidget = client.getWidget(playerGroupId, getChildId(ComponentID.DIALOG_PLAYER_TEXT));

		if (playerDialogTextWidget != null) {
			String dialogText = playerDialogTextWidget.getText();
			if (!dialogText.equals(lastPlayerDialogText)) {
				lastPlayerDialogText = dialogText;

				dialogText = dialogText.replace("<br>", " ");
				VoiceID voiceID = voiceManager.getVoiceIdForLocalPlayer();
				textToSpeech.speak(voiceID, dialogText, 0, PluginHelper.getLocalPlayerUsername());
			}
		}
		else if (!lastPlayerDialogText.isEmpty()) lastPlayerDialogText = "";

		//		int npcGroupId = WidgetInfo.DIALOG_NPC_TEXT.getGroupId();
		int npcGroupId = getGroupId(ComponentID.DIALOG_NPC_TEXT);
		Widget npcDialogTextWidget = client.getWidget(npcGroupId, getChildId(ComponentID.DIALOG_NPC_TEXT));

		if (npcDialogTextWidget != null) {
			String dialogText = npcDialogTextWidget.getText();
			if (!dialogText.equals(lastNpcDialogText)) {
				lastNpcDialogText = dialogText;
				Widget nameTextWidget = client.getWidget(npcGroupId, getChildId(ComponentID.DIALOG_NPC_NAME));
				Widget modelWidget = client.getWidget(npcGroupId, getChildId(ComponentID.DIALOG_NPC_HEAD_MODEL));

				if (nameTextWidget != null && modelWidget != null) {
					String npcName = nameTextWidget.getText().toLowerCase();
					int modelId = modelWidget.getModelId();

					dialogText = dialogText.replace("<br>", " ");

					VoiceID voiceID = voiceManager.getVoiceIDFromNPCId(modelId, npcName);
					textToSpeech.speak(voiceID, dialogText, 1, dialogText);
				}
			}
		}
		else if (!lastNpcDialogText.isEmpty()) lastNpcDialogText = "";
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		if (event.getGroup().equals(CONFIG_GROUP)) {
			switch (event.getKey()) {
				case "muteSelf":
					textToSpeech.clearPlayerAudioQueue(getLocalPlayerUsername());
					break;

				case "muteOthers":
					textToSpeech.clearOtherPlayersAudioQueue(getLocalPlayerUsername());
					break;
				case "shortenedPhrases":
					loadShortenedPhrases();
					break;
			}
		}
	}
	//</editor-fold>



	//<editor-fold desc="> ChatMessage">
	public boolean isMessageProcessable(ChatMessage message) {
		if (isMessageTypeDisabledInConfig(message)) return false;
		if (checkMuteAllowAndBlockList(message)) return false;
		if (message.getType() == ChatMessageType.PUBLICCHAT && isAreaDisabled()) return false;
		if (isSelfMuted(message)) return false;
		if (isMutingOthers(message)) return false;
		if (checkMuteLevelThreshold(message)) return false;

		return true;
	}

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
				return true;
			//				if (!config.dialogEnabled()) return true;
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
				if (!config.systemMesagesEnabled()) return true;
				break;
			case TRADEREQ:
			case CHALREQ_CLANCHAT:
			case CHALREQ_FRIENDSCHAT:
			case CHALREQ_TRADE:
				if (!config.requestsEnabled()) return true;
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
		if (message.getType() == ChatMessageType.PRIVATECHATOUT) return false;
		if (message.getType() == ChatMessageType.CLAN_CHAT) return false;
		if (message.getType() == ChatMessageType.CLAN_GUEST_CHAT) return false;
		if (getLevel(message.getName()) < config.muteLevelThreshold()) return true;


		return false;
	}
	// FIXME Implement voice getter

	/**
	 * EXAMINE has null for name field<br>
	 * DIALOG has name in `name|message` format with null for name field<br>
	 * GAMEMESSAGE & CONSOLE can sometimes have tags which need to be removed<br>
	 * <p>
	 * This method takes in message reference and patches the name field with correct value<br>
	 *
	 * @param message reference passed in and modified
	 */
	private void patchChatMessage(ChatMessage message) {
		switch (message.getType()) {
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				message.setName(client.getLocalPlayer().getName());
				break;
			//			case DIALOG:
			//				String[] parts = message.getMessage().split("\\|", 2);
			//				if (parts.length == 2) {
			//					message.setName(parts[0]);
			//					message.setMessage(parts[1]);
			//				} else {
			//					throw new RuntimeException("Unknown NPC dialog format: " + message.getMessage());
			//				}
			//				break;
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
				message.setMessage(TextUtil.removeTags(message.getMessage()));
				message.setName("_system_");
				break;

		}
		message.setMessage(expandShortenedPhrases(message.getMessage()));
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
		if (isBeingListened(username)) {status = "<col=78B159>O";}
		else {status = "<col=DD2E44>0";}

		CustomMenuEntry muteOptions =
			new CustomMenuEntry(String.format("%s <col=ffffff>TTS <col=ffffff>(%s) <col=ffffff>>", status, username),
				index);

		if (isBeingListened(username)) {
			if (!getAllowList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Stop listening", -1, function -> {
					unlisten(username);
				}));
			}
			else {
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
		}
		else {
			if (!PluginHelper.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Unmute", -1, function -> {
					unmute(username);
				}));
			}
			else {
				muteOptions.addChild(new CustomMenuEntry("Listen", -1, function -> {
					listen(username);
				}));
			}
		}

		if (!getBlockList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear block list", -1, function -> {
				getBlockList().clear();
			}));
		}
		else if (!getAllowList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear allow list", -1, function -> {
				getAllowList().clear();
			}));
		}

		muteOptions.addChild(new CustomMenuEntry("Configure Voice", -1, function -> {
			voiceConfigChatboxTextInputProvider.get()
				.insertActor(entry.getActor())
				.build();
		}));

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

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
