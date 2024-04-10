package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig.ConfigKeys;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.guice.PluginSingletonScope;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import dev.phyce.naturalspeech.tts.AudioLineNames;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VolumeManager;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.LoggerFactory;

@Slf4j
@PluginDescriptor(name=CONFIG_GROUP)
public class NaturalSpeechPlugin extends Plugin {

	static {
		// Setup package level logger level
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

	// region: RuneLite Dependencies
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
	// endregion

	// region: Fields
	// Scope holds references to all the singletons, provides them to guice injections
	private PluginSingletonScope pluginSingletonScope;
	private NaturalSpeech ns;
	private NavigationButton navButton;
	private final Set<Object> rlEventBusSubscribers = new HashSet<>();
	// endregion


	// region: helpers
	/**
	 * registers and remembers, used to safely unregister all objects with {@link #unregisterRLEventBusAll()}.
	 */
	private void registerRLEventBus(@NonNull Object object) {
		if (rlEventBusSubscribers.contains(object)) {
			log.error("Attempting to double register {} to eventBus, skipping.", object.getClass().getSimpleName());
		}
		else {
			eventBus.register(object);
			rlEventBusSubscribers.add(object);
		}
	}

	/**
	 * unregisters all eventBus objects registered using {@link #registerRLEventBus(Object)}
	 */
	private void unregisterRLEventBusAll() {
		for (Object object : rlEventBusSubscribers) {
			eventBus.unregister(object);
		}
		rlEventBusSubscribers.clear();
	}

	private void saveConfigs() {
		ns.voiceManager.saveVoiceConfig();
		ns.piperEngine.saveModelConfig();
		ns.runtimeConfig.savePiperPath(ns.runtimeConfig.getPiperPath());
		ns.muteManager.saveConfig();
	}

	private void applyConfigVoice(String configKey, String voiceString) {
		VoiceID voiceID;
		voiceID = VoiceID.fromIDString(voiceString);

		switch (configKey) {
			case ConfigKeys.PERSONAL_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", AudioLineNames.LOCAL_USER, voiceID);
					ns.voiceManager.setDefaultVoiceIDForUsername(AudioLineNames.LOCAL_USER, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", AudioLineNames.LOCAL_USER);
					ns.voiceManager.resetForUsername(AudioLineNames.LOCAL_USER);
				}
				break;
			case ConfigKeys.GLOBAL_NPC_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", AudioLineNames.GLOBAL_NPC, voiceID);
					ns.voiceManager.setDefaultVoiceIDForUsername(AudioLineNames.GLOBAL_NPC, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", AudioLineNames.GLOBAL_NPC);
					ns.voiceManager.resetForUsername(AudioLineNames.GLOBAL_NPC);
				}
				break;
			case ConfigKeys.SYSTEM_VOICE:
				if (voiceID != null) {
					log.debug("Setting voice for {} to {}", AudioLineNames.SYSTEM, voiceID);
					ns.voiceManager.setDefaultVoiceIDForUsername(AudioLineNames.SYSTEM, voiceID);
				}
				else {
					log.debug("Invalid voice for {}, resetting voices.", AudioLineNames.SYSTEM);
					ns.voiceManager.resetForUsername(AudioLineNames.SYSTEM);
				}
				break;
		}
	}

	// endregion

	// region: Override Methods
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
		// Objects marked with @PluginScopeSingletons will enter this scope
		// These objects will be GC-able after pluginSingletonScope.exit()
		pluginSingletonScope.enter();

		// plugin fields are wrapped in a field object
		ns = injector.getInstance(NaturalSpeech.class);

		// Abstracting the massive client event handlers into their own files
		// registers to eventbus, unregistered automatically using unregisterRLEventBusAll() in shutdown()
		registerRLEventBus(ns.speechEventHandler);
		registerRLEventBus(ns.menuEventHandler);
		registerRLEventBus(ns.commandExecutedEventHandler);
		registerRLEventBus(ns.spamFilterPluglet);
		registerRLEventBus(ns.chatFilterPluglet);
		registerRLEventBus(ns.volumeManager);

		// Build panel and navButton
		{
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
			navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(icon)
				.priority(1)
				.panel(ns.topLevelPanel)
				.build();
			clientToolbar.addNavigation(navButton);
		}

		// Load Abbreviations is a method that can be called later when configs are changed
		ns.textToSpeech.loadAbbreviations();

		if (config.autoStart()) {
			ns.textToSpeech.start();
		}

		applyConfigVoice(ConfigKeys.PERSONAL_VOICE, config.personalVoiceID());
		applyConfigVoice(ConfigKeys.GLOBAL_NPC_VOICE, config.globalNpcVoice());
		applyConfigVoice(ConfigKeys.SYSTEM_VOICE, config.systemVoice());

		ns.audioEngine.setMasterGain(VolumeManager.volumeToGain(config.masterVolume()));

		log.info("NaturalSpeech plugin has started");
	}

	@Override
	public void shutDown() {
		// unregister eventBus so handlers do not run after shutdown.
		unregisterRLEventBusAll();

		ns.topLevelPanel.shutdown();

		clientToolbar.removeNavigation(navButton);
		navButton = null;

		ns.textToSpeech.stop();

		saveConfigs();

		pluginSingletonScope.exit(); // objects in this scope will be garbage collected after scope exit
		ns = null;

		log.info("NaturalSpeech plugin has shutDown");
	}

	// FIXME(Louis): Apply optimal default setting on reset config
	@Override
	public void resetConfiguration() {
		ns.runtimeConfig.reset();
	}

	// endregion

	// region: Event Subscribers

	/**
	 * update AudioEngine 8 times per tick on the client thread.
	 */
	@Schedule(period=600 / 8, unit=ChronoUnit.MILLIS)
	public void updateAudioEngine() {
		ns.audioEngine.update();
	}

	@Subscribe
	private void onClientShutdown(ClientShutdown e) {
		// shutDown is not called on X button client exit, so we need to listen to clientShutdown
		saveConfigs();
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		if (ns.textToSpeech.isStarted()) {
			switch (event.getKey()) {
				case ConfigKeys.MUTE_SELF:
					log.trace("Detected mute-self toggle, clearing audio queue.");
					ns.textToSpeech.silence((otherLineName) -> otherLineName.equals(AudioLineNames.LOCAL_USER));
					break;

				case ConfigKeys.MUTE_OTHERS:
					log.trace("Detected mute-others toggle, clearing audio queue.");
					ns.textToSpeech.silence((otherLineName) -> !otherLineName.equals(AudioLineNames.LOCAL_USER));
					break;

			}
		}

		switch (event.getKey()) {
			case ConfigKeys.COMMON_ABBREVIATIONS:
			case ConfigKeys.CUSTOM_ABBREVIATIONS:
				log.trace("Detected abbreviation changes, reloading into TextToSpeech");
				ns.textToSpeech.loadAbbreviations();
				break;

			case ConfigKeys.PERSONAL_VOICE:
			case ConfigKeys.GLOBAL_NPC_VOICE:
			case ConfigKeys.SYSTEM_VOICE:
				log.trace("Detected voice changes from config, loading in new voices");
				applyConfigVoice(event.getKey(), event.getNewValue());
				break;
		}

		if (event.getKey().equals(ConfigKeys.MASTER_VOLUME)) {
			log.trace("Detected master volume change to {}, updating audio engine", config.masterVolume());
			ns.audioEngine.setMasterGain(VolumeManager.volumeToGain(config.masterVolume()));
		}
	}
	// endregion

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
