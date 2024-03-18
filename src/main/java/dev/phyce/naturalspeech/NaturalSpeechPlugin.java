package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.getLocalPlayerUsername;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.LoggerFactory;


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
	@Inject
	private EventBus eventBus;

	//</editor-fold>

	//<editor-fold desc="> Internal Dependencies">
	private NaturalSpeechRuntimeConfig runtimeConfig;
	private VoiceManager voiceManager;
	private TextToSpeech textToSpeech;
	private SpeechEventHandler speechEventHandler;
	private MenuEventHandler menuEventHandler;

	//</editor-fold>

	//<editor-fold desc="> Runtime Variables">
	private NavigationButton navButton;
	//</editor-fold>


	static {
		final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());
		logger.setLevel(Level.INFO);
	}

	//<editor-fold desc="> Override Methods">
	@Override
	public void configure(Binder binder) {
		// Instantiate PluginHelper early, Plugin relies on static PluginHelper::Instance
		// No cycling-dependencies back at NaturalSpeechPlugin allowed
		// quality-of-life abstraction for coding
		binder.bind(PluginHelper.class).asEagerSingleton();
		// Downloader has all dependencies from RuneLite, eager load
		binder.bind(Downloader.class).asEagerSingleton();
	}

	@Override
	public void startUp() {

		runtimeConfig = injector.getInstance(NaturalSpeechRuntimeConfig.class);
		textToSpeech = injector.getInstance(TextToSpeech.class);
		voiceManager = injector.getInstance(VoiceManager.class);

		// Abstracting the massive client event handlers into their own files
		speechEventHandler = injector.getInstance(SpeechEventHandler.class);
		menuEventHandler = injector.getInstance(MenuEventHandler.class);

		// registers to eventbus, make sure to unregister on shutdown()
		eventBus.register(speechEventHandler);
		eventBus.register(menuEventHandler);

		// Build panel and navButton
		{
			TopLevelPanel topLevelPanel = injector.getInstance(TopLevelPanel.class);
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
		textToSpeech.loadShortenedPhrases();


		if (config.autoStart()) {
			textToSpeech.start();
		}

		updateConfigVoice("personalVoice", config.personalVoiceID());
		updateConfigVoice("dialogVoice", config.dialogVoice());
		updateConfigVoice("systemVoice", config.systemVoice());

		log.info("NaturalSpeech plugin has started");
	}

	@Override
	public void shutDown() {
		// unregister eventBus so handlers do not run after shutdown.
		eventBus.unregister(speechEventHandler);
		eventBus.unregister(menuEventHandler);

		if (textToSpeech != null) {
			textToSpeech.stop();
		}
		clientToolbar.removeNavigation(navButton);

		saveConfigs();

		log.info("NaturalSpeech plugin has shutDown");
	}

	@Subscribe
	private void onClientShutdown(ClientShutdown e) {
		// shutDown is not called on X button client exit, so we need to listen to clientShutdown
		saveConfigs();
	}

	private void saveConfigs() {
		voiceManager.saveVoiceConfig();
		textToSpeech.saveModelConfig();
		runtimeConfig.savePiperPath(runtimeConfig.getPiperPath());
	}

	@Override
	public void resetConfiguration() {
		runtimeConfig.reset();
	}
	//</editor-fold>

	//<editor-fold desc="> Hooks">

	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {

		}
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
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
					textToSpeech.loadShortenedPhrases();
					break;

				case "personalVoice":
				case "dialogVoice":
				case "systemVoice":
					updateConfigVoice(event.getKey(), event.getNewValue());
					break;
			}
		}
	}

	private void updateConfigVoice(String configKey, String voiceString) {
		VoiceID voiceID;
		voiceID = VoiceID.fromIDString(voiceString);
		if (voiceID == null)  {
			log.error("User attempted to provide an invalid Voice ID Value for: " + configKey);
		}

		boolean isModelActive = (voiceID != null && textToSpeech.isModelActive(voiceID.modelName));
		switch(configKey) {
			case "personalVoice":
				String localPlayer = getLocalPlayerUsername();

				// FIXME(Louis)
				if (localPlayer == null) {
					log.error("Player isn't logged in, no username information available.");
					break;
				}

				if (isModelActive)  {
					voiceManager.setDefaultVoiceIDForUsername(localPlayer, voiceID);
					break;
				}
				voiceManager.resetForUsername(localPlayer);
				break;

			case "dialogVoice":
				if (isModelActive)  {
					voiceManager.setDefaultVoiceIDForNPCs(voiceID);
					break;
				}
				voiceManager.resetVoiceIDForNPCs();
				break;

			case "systemVoice":
				if (isModelActive)  {
					voiceManager.setDefaultVoiceIDForSystem(voiceID);
					break;
				}
				voiceManager.resetVoiceIDForSystem();
				break;
		}
	}

//	private void updatePersonalVoiceID() {
//		VoiceID voiceID;
//		voiceID = VoiceID.fromIDString(event.getNewValue());
//		if (voiceID == null) {
//			voiceManager.resetForUsername(standardized_username);
//			log.error("User attempting provided invalid Voice ID for personal voice through RuneLite config panel.");
//		}
//		else voiceManager.setVoiceIDForUsername(standardized_username, voiceID);
//	}

//	private void updateDialogVoice() {
//		VoiceID voiceID;
//		voiceID = VoiceID.fromIDString(event.getNewValue());
//		if (voiceID == null) {
//			voiceManager.resetVoiceIDForNPCs();
//			log.error("User attempting provided invalid Voice ID for dialog voice through RuneLite config panel.");
//		}
//		else voiceManager.setVoiceIDForNPCs(voiceID);
//	}

//	private void updateSystemVoice() {
//		VoiceID voiceID;
//		voiceID = VoiceID.fromIDString(event.getNewValue());
//		if (voiceID == null) {
//			voiceManager.resetVoiceIDForSystem();
//			log.error("User attempting provided invalid Voice ID for system voice through RuneLite config panel.");
//		}
//		else voiceManager.setVoiceIDForSystem(voiceID);
//	}

	@Subscribe
	private void onCommandExecuted(CommandExecuted commandExecuted) {
		String[] args = commandExecuted.getArguments();

		//noinspection SwitchStatementWithTooFewBranches
		switch (commandExecuted.getCommand()) {
			case "nslogger": {
				final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());
				String message;
				Level currentLoggerLevel = logger.getLevel();

				if (args.length < 1) {
					message = "Logger level is currently set to " + currentLoggerLevel;
				}
				else {
					Level newLoggerLevel = Level.toLevel(args[0], currentLoggerLevel);
					logger.setLevel(newLoggerLevel);
					message = "Logger level has been set to " + newLoggerLevel;
				}

				client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
				break;
			}
			case "setvoice": {
				if (args.length < 2) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"use ::setvoice model:id username, for example ::setvoice libritts:2 Zezima", null);
				}
				else {
					VoiceID voiceId = VoiceID.fromIDString(args[0]);
					String username = Arrays.stream(args).skip(1).reduce((a, b) -> a + " " + b).orElse(args[1]);
					if (voiceId == null) {
						client.addChatMessage(ChatMessageType.CONSOLE, "", "voice id " + args[1] + " is invalid.",
							null);
					}
					else {
						voiceManager.setDefaultVoiceIDForUsername(username, voiceId);
						client.addChatMessage(ChatMessageType.CONSOLE, "", username + " voice is set to " + args[0],
							null);
					}
				}
				break;
			}
			case "unsetvoice": {
				if (args.length < 1) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"use ::unsetvoice username, for example ::unsetvoice Zezima", null);
				}
				else {
					String username = Arrays.stream(args).reduce((a, b) -> a + " " + b).orElse(args[0]);
					voiceManager.resetForUsername(username);
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"All voices are removed for " + username, null);
				}
				break;
			}
			case "checkvoice": {
				String username;
				if (args.length < 1) {
//					client.addChatMessage(ChatMessageType.CONSOLE, "",
//						"use ::checkvoice username, for example ::checkvoice Zezima", null);
					username = PluginHelper.getLocalPlayerUsername();
					Objects.requireNonNull(username);
				}
				else {
					username = Arrays.stream(args).reduce((a, b) -> a + " " + b).orElse(args[0]);
				}

				List<VoiceID> voiceIds = voiceManager.checkVoiceIDWithUsername(username);
				if (voiceIds == null) {
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"There are no voices set for " + username + ".", null);
				}
				else {
					String idStr = voiceIds.stream().map(VoiceID::toString).reduce((a, b) -> a + ", " + b)
						.orElse("No voice set");
					client.addChatMessage(ChatMessageType.CONSOLE, "", username + " voice is set to " + idStr, null);
				}
				break;
			}
		}
	}
	//</editor-fold>

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}


}
