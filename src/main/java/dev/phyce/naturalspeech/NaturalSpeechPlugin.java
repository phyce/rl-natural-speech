package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig.ConfigKeys;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.guice.PluginSingletonScope;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import dev.phyce.naturalspeech.tts.MagicUsernames;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
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
	private MuteManager muteManager;
	private TextToSpeech textToSpeech;
	private SpamFilterPluglet spamFilterPluglet;
	private ChatFilterPluglet chatFilterPluglet;
	private SpamDetection spamDetection;
	private SpeechEventHandler speechEventHandler;
	private MenuEventHandler menuEventHandler;
	private CommandExecutedEventHandler commandExecutedEventHandler;

	//</editor-fold>

	//<editor-fold desc="> Runtime Variables">
	private PluginSingletonScope pluginSingletonScope;
	private NavigationButton navButton;
	//</editor-fold>

	static {

		final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());

		String result = System.getProperty("nslogger");
		if (result != null) {
			log.info("nslogger VM property found, setting logger level to {}", result);
			logger.setLevel(Level.valueOf(result));
		}
		else {
			logger.setLevel(Level.INFO);
		}
	}

	private TopLevelPanel topLevelPanel;


	//<editor-fold desc="> Override Methods">
	@Override
	public void configure(Binder binder) {
		pluginSingletonScope = new PluginSingletonScope();
		binder.bindScope(PluginSingleton.class, pluginSingletonScope);
		// Instantiate PluginHelper early, Plugin relies on static PluginHelper::Instance
		// No cycling-dependencies back at NaturalSpeechPlugin allowed
		// quality-of-life abstraction for coding
		binder.bind(PluginHelper.class).asEagerSingleton();
		// Downloader has all dependencies from RuneLite, eager load
		binder.bind(Downloader.class).asEagerSingleton();
	}

	@Override
	public void startUp() {
		pluginSingletonScope.enter();

		runtimeConfig = injector.getInstance(NaturalSpeechRuntimeConfig.class);
		textToSpeech = injector.getInstance(TextToSpeech.class);
		voiceManager = injector.getInstance(VoiceManager.class);
		muteManager = injector.getInstance(MuteManager.class);
		spamFilterPluglet = injector.getInstance(SpamFilterPluglet.class);
		chatFilterPluglet = injector.getInstance(ChatFilterPluglet.class);
		spamDetection = injector.getInstance(SpamDetection.class);

		// Abstracting the massive client event handlers into their own files
		speechEventHandler = injector.getInstance(SpeechEventHandler.class);
		menuEventHandler = injector.getInstance(MenuEventHandler.class);
		commandExecutedEventHandler = injector.getInstance(CommandExecutedEventHandler.class);

		// registers to eventbus, make sure to unregister on shutdown()
		eventBus.register(speechEventHandler);
		eventBus.register(menuEventHandler);
		eventBus.register(commandExecutedEventHandler);
		eventBus.register(spamFilterPluglet);
		eventBus.register(chatFilterPluglet);

		// Build panel and navButton
		{
			topLevelPanel = injector.getInstance(TopLevelPanel.class);
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
			navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(topLevelPanel)
				.build();
			clientToolbar.addNavigation(navButton);
		}

		// Load Abbreviations is a method that can be called later when configs are changed
		textToSpeech.loadAbbreviations();


		if (config.autoStart()) {
			textToSpeech.start();
		}

		updateConfigVoice(ConfigKeys.PERSONAL_VOICE, config.personalVoiceID());
		updateConfigVoice(ConfigKeys.GLOBAL_NPC_VOICE, config.globalNpcVoice());
		updateConfigVoice(ConfigKeys.SYSTEM_VOICE, config.systemVoice());

		log.info("NaturalSpeech plugin has started");
	}

	@Override
	public void shutDown() {
		// unregister eventBus so handlers do not run after shutdown.
		eventBus.unregister(speechEventHandler);
		eventBus.unregister(menuEventHandler);
		eventBus.unregister(commandExecutedEventHandler);
		eventBus.unregister(spamFilterPluglet);
		eventBus.unregister(chatFilterPluglet);

		topLevelPanel.shutdown();

		if (textToSpeech != null) {
			textToSpeech.stop();
		}
		clientToolbar.removeNavigation(navButton);

		saveConfigs();

		pluginSingletonScope.exit();

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
		muteManager.saveConfig();
	}

	@Override
	public void resetConfiguration() {
		runtimeConfig.reset();
	}
	//</editor-fold>

	//<editor-fold desc="> Hooks">

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		if (textToSpeech.activePiperProcessCount() < 1) {
			switch (event.getKey()) {
				case ConfigKeys.MUTE_SELF:
					log.trace("Detected mute-self toggle, clearing audio queue.");
					textToSpeech.clearPlayerAudioQueue(MagicUsernames.LOCAL_USER);
					break;

				case ConfigKeys.MUTE_OTHERS:
					log.trace("Detected mute-others toggle, clearing audio queue.");
					textToSpeech.clearOtherPlayersAudioQueue(MagicUsernames.LOCAL_USER);
					break;

			}
		}

		switch (event.getKey()) {
			case ConfigKeys.COMMON_ABBREVIATIONS:
			case ConfigKeys.CUSTOM_ABBREVIATIONS:
				log.trace("Detected abbreviation changes, reloading into TextToSpeech");
				textToSpeech.loadAbbreviations();
				break;

			case ConfigKeys.PERSONAL_VOICE:
			case ConfigKeys.GLOBAL_NPC_VOICE:
			case ConfigKeys.SYSTEM_VOICE:
				log.trace("Detected voice changes from config, loading in new voices");
				updateConfigVoice(event.getKey(), event.getNewValue());
				break;
		}
	}

	private void updateConfigVoice(String configKey, String voiceString) {
		VoiceID voiceID;
		voiceID = VoiceID.fromIDString(voiceString);

		switch (configKey) {
			case ConfigKeys.PERSONAL_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", MagicUsernames.LOCAL_USER, voiceID);
					voiceManager.setDefaultVoiceIDForUsername(MagicUsernames.LOCAL_USER, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", MagicUsernames.LOCAL_USER);
					voiceManager.resetForUsername(MagicUsernames.LOCAL_USER);
				}
				break;
			case ConfigKeys.GLOBAL_NPC_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", MagicUsernames.GLOBAL_NPC, voiceID);
					voiceManager.setDefaultVoiceIDForUsername(MagicUsernames.GLOBAL_NPC, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", MagicUsernames.GLOBAL_NPC);
					voiceManager.resetForUsername(MagicUsernames.GLOBAL_NPC);
				}
				break;
			case ConfigKeys.SYSTEM_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", MagicUsernames.SYSTEM, voiceID);
					voiceManager.setDefaultVoiceIDForUsername(MagicUsernames.SYSTEM, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", MagicUsernames.SYSTEM);
					voiceManager.resetForUsername(MagicUsernames.SYSTEM);
				}
				break;
		}
	}
	//</editor-fold>

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
