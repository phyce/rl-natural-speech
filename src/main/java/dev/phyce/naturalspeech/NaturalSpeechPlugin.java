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
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import dev.phyce.naturalspeech.tts.MagicUsernames;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeech;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
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
@PluginDescriptor(name="Natural Speech")
public class NaturalSpeechPlugin extends Plugin {

	private static final String LEGACY_CONFIG_GROUP = "NaturalSpeech";

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
	@Inject
	private ClientThread clientThread;

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
		// Instantiate PluginHelper early, Plugin relies on static PluginHelper::Instance
		// No cycling-dependencies back at NaturalSpeechPlugin allowed
		// quality-of-life abstraction for coding
		binder.bind(PluginHelper.class).asEagerSingleton();
		// Downloader has all dependencies from RuneLite, eager load
		binder.bind(Downloader.class).asEagerSingleton();
	}

	@Override
	public void startUp() {

		migrateLegacyConfigGroup();
		migrateSpeechToggles();

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

		// Load ShortenedPhrases is a method that can be called later when configs are changed
		textToSpeech.loadShortenedPhrases();


		seedFirstRunDefaults();

		if (config.autoStart()) {
			textToSpeech.start();
		}

		realignConfiguredVoices();

		updateConfigVoice(ConfigKeys.PERSONAL_VOICE, config.personalVoiceID());
		updateConfigVoice(ConfigKeys.GLOBAL_NPC_VOICE, config.globalNpcVoice());
		updateConfigVoice(ConfigKeys.SYSTEM_VOICE, config.systemVoice());
		updateConfigVoice(ConfigKeys.TWITCH_VOICE, config.twitchVoice());

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

	private void migrateLegacyConfigGroup() {
		final String legacyPrefix = LEGACY_CONFIG_GROUP + ".";
		List<String> legacyKeys = configManager.getConfigurationKeys(legacyPrefix);

		if (legacyKeys.isEmpty()) {
			return;
		}

		log.info("Migrating {} setting(s) from legacy config group '{}' to '{}'",
			legacyKeys.size(), LEGACY_CONFIG_GROUP, CONFIG_GROUP);

		for (String legacyKey : legacyKeys) {
			String key = legacyKey.substring(legacyPrefix.length());

			String value = configManager.getConfiguration(LEGACY_CONFIG_GROUP, key);
			log.debug("Migrating config key '{}' (value present: {})", key, value != null);
			if (value != null) {
				configManager.setConfiguration(CONFIG_GROUP, key, value);
			}

			configManager.unsetConfiguration(LEGACY_CONFIG_GROUP, key);
		}

		log.info("Legacy config migration complete");
	}

	private static final String[] DEFAULT_ON_KEYS = {
		ConfigKeys.PUBLIC_CHAT,
		ConfigKeys.FRIENDS_CHAT,
		ConfigKeys.EXAMINE_CHAT,
		ConfigKeys.DIALOG,
		ConfigKeys.PLAYER_DIALOG,
		ConfigKeys.SYSTEM_MESSAGES,
		ConfigKeys.LOGIN_LOGOUT,
	};

	private void seedFirstRunDefaults() {
		for (String key : SPEECH_ENGINE_KEYS) {
			// any setting at all means this is not a first run
			if (configManager.getConfiguration(CONFIG_GROUP, key) != null) return;
		}

		if (!NativeSpeech.isSupported()) return;
		if (textToSpeech.isPiperSetUp()) return;

		log.info("First run without piper, defaulting the enabled message types to the system voices");
		for (String key : DEFAULT_ON_KEYS) {
			configManager.setConfiguration(CONFIG_GROUP, key, SpeechEngine.SYSTEM);
		}
	}

	private static final String[] SPEECH_ENGINE_KEYS = {
		ConfigKeys.PUBLIC_CHAT,
		ConfigKeys.PRIVATE_CHAT,
		ConfigKeys.PRIVATE_OUT_CHAT,
		ConfigKeys.FRIENDS_CHAT,
		ConfigKeys.CLAN_CHAT,
		ConfigKeys.CLAN_GUEST_CHAT,
		ConfigKeys.GIM_CHAT,
		ConfigKeys.EXAMINE_CHAT,
		ConfigKeys.NPC_OVERHEAD,
		ConfigKeys.DIALOG,
		ConfigKeys.PLAYER_DIALOG,
		ConfigKeys.REQUESTS,
		ConfigKeys.SYSTEM_MESSAGES,
		ConfigKeys.LOGIN_LOGOUT,
		ConfigKeys.TWITCH_CHAT,
	};

	private void migrateSpeechToggles() {
		int migrated = 0;

		for (String key : SPEECH_ENGINE_KEYS) {
			String value = configManager.getConfiguration(CONFIG_GROUP, key);

			if ("true".equals(value)) {
				configManager.setConfiguration(CONFIG_GROUP, key, SpeechEngine.PIPER);
			}
			else if ("false".equals(value)) {
				configManager.setConfiguration(CONFIG_GROUP, key, SpeechEngine.OFF);
			}
			else {
				continue;
			}

			migrated++;
		}

		if (migrated > 0) {
			log.info("Migrated {} message type(s) from an on/off toggle to a choice of engine", migrated);
		}
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
			case ConfigKeys.SHORTENED_PHRASES:
			case ConfigKeys.COMMON_ABBREVIATIONS:
				log.trace("Detected abbreviation changes, reloading into TextToSpeech");
				textToSpeech.loadShortenedPhrases();
				break;

			case ConfigKeys.PERSONAL_VOICE:
			case ConfigKeys.GLOBAL_NPC_VOICE:
			case ConfigKeys.SYSTEM_VOICE:
			case ConfigKeys.TWITCH_VOICE:
				log.trace("Detected voice changes from config, loading in new voices");
				updateConfigVoice(event.getKey(), event.getNewValue());
				break;

			case ConfigKeys.ELEVENLABS_API_KEY:
				log.trace("Detected ElevenLabs API key change, reloading the voice library");
				clientThread.invokeLater(() -> textToSpeech.startElevenLabs());
				break;

			case ConfigKeys.NATIVE_SPEECH:
				// spawning the process is too slow to do on the EDT
				final boolean enabled = config.nativeSpeechEnabled();
				clientThread.invokeLater(() -> {
					if (enabled) {
						textToSpeech.startNativeSpeech();
					}
					else {
						textToSpeech.stopNativeSpeech();
					}
					realignConfiguredVoices();
				});
				break;
		}

		if (isSpeechEngineKey(event.getKey())) {
			realignConfiguredVoices();
		}
	}

	private static boolean isSpeechEngineKey(String key) {
		for (String engineKey : SPEECH_ENGINE_KEYS) {
			if (engineKey.equals(key)) return true;
		}
		return false;
	}

	private void realignConfiguredVoices() {
		realignVoice(ConfigKeys.PERSONAL_VOICE, config.personalVoiceID(), config.publicChat());
		realignVoice(ConfigKeys.GLOBAL_NPC_VOICE, config.globalNpcVoice(), config.npcDialog());
		realignVoice(ConfigKeys.SYSTEM_VOICE, config.systemVoice(), config.systemMessages());
		realignVoice(ConfigKeys.TWITCH_VOICE, config.twitchVoice(), config.twitchChat());
	}

	private void realignVoice(String configKey, String currentValue, SpeechEngine engine) {
		if (engine.isOff()) return;
		// ElevenLabs picks its voices from its own settings and the Custom Characters tab,
		// so it should not overwrite the piper/system voice ids configured here.
		if (engine == SpeechEngine.ELEVENLABS) return;

		VoiceID current = VoiceID.fromIDString(currentValue);
		if (current == null) return;
		if (TextToSpeech.engineOfModel(current.getModelName()) == engine) return;

		VoiceID replacement = voiceManager.anyVoiceFor(engine);
		if (replacement == null) {
			log.debug("{} is set to {} which is not {}, but that engine has no voices to swap in",
				configKey, current, engine);
			return;
		}

		log.info("{} was {}, which {} cannot speak. Using {} instead.",
			configKey, current, engine, replacement);
		configManager.setConfiguration(CONFIG_GROUP, configKey, replacement.toVoiceIDString());
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
			case ConfigKeys.TWITCH_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", MagicUsernames.TWITCH, voiceID);
					voiceManager.setDefaultVoiceIDForUsername(MagicUsernames.TWITCH, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", MagicUsernames.TWITCH);
					voiceManager.resetForUsername(MagicUsernames.TWITCH);
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
