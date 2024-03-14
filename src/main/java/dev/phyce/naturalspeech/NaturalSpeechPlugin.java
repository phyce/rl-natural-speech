package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.helpers.CustomMenuEntry;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.intruments.VoiceLogger;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.ui.game.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.LineUnavailableException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
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

	//</editor-fold>

	//<editor-fold desc="> Internal Dependencies">
	@Inject
	private NaturalSpeechRuntimeConfig runtimeConfig;
	@Getter
	private TopLevelPanel topLevelPanel;
	@Getter
	private VoiceManager voiceManager;
	@Getter
	private TextToSpeech textToSpeech;
	@Getter
	private ModelRepository modelRepository;

	@Inject
	private Provider<VoiceManager> voiceManagerProvider;
	@Inject
	private Provider<TextToSpeech> textToSpeechProvider;
	@Inject
	private Provider<TopLevelPanel> topLevelPanelProvider;
	@Inject
	private Provider<ModelRepository> modelRepositoryProvider;
	@Inject
	private Provider<SpeechEventHandler> speechEventHandlerProvider;
	@Inject
	private Provider<MenuEventHandler> menuEventHandlerProvider;
	//</editor-fold>

	//<editor-fold desc="> Runtime Variables">


	private NavigationButton navButton;
	//</editor-fold>

	static {
		final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());
		logger.setLevel(Level.INFO);
	}

	public void startTextToSpeech() throws RuntimeException, IOException, LineUnavailableException {
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

		speechEventHandlerProvider.get();
		menuEventHandlerProvider.get();



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
		textToSpeech.loadShortenedPhrases();

		if (config.autoStart()) {
			try {
				this.startTextToSpeech();
			} catch (IOException | LineUnavailableException e) {
				throw new RuntimeException(e);
			}
		}
		log.info("NaturalSpeech plugin has started");
	}

	@Override
	protected void shutDown() {
		if (textToSpeech != null) {
			textToSpeech.stop();
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
					textToSpeech.loadShortenedPhrases();
					break;
			}
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted) {
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
		}
	}
	//</editor-fold>


	//<editor-fold desc="> ChatMessage">

	// FIXME Implement voice getter
	//</editor-fold>

	//<editor-fold desc="> Other">


	//</editor-fold>

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
