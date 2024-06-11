package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.singleton.PluginSingletonScope;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.Names;
import static dev.phyce.naturalspeech.statics.PluginResources.NATURAL_SPEECH_ICON;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperModelEngine;
import dev.phyce.naturalspeech.userinterface.mainsettings.PiperModelMonitorItem;
import dev.phyce.naturalspeech.userinterface.voiceexplorer.VoiceListItem;
import dev.phyce.naturalspeech.userinterface.voicehub.PiperModelItem;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import javax.swing.SwingUtilities;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
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
import org.slf4j.LoggerFactory;

@Slf4j
@PluginDescriptor(name=CONFIG_GROUP)
public class NaturalSpeechPlugin extends Plugin {

	public static final String CONFIG_GROUP = "NaturalSpeech";

	static {
		// Gets package level logger for modification (the parent to all plugin class loggers)
		final Logger logger = (Logger) LoggerFactory.getLogger(NaturalSpeechPlugin.class.getPackageName());
		String result = System.getProperty("nslogger");
		if (result != null) {
			logger.info("nslogger VM property found, setting logger level to {}", result);
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
	private NaturalSpeechConfig config;
	@Inject
	private EventBus runeliteEventBus;
	// endregion

	// region: Fields
	// Scope holds references to all the singletons, provides them to guice for injection
	private PluginSingletonScope pluginSingletonScope;
	private NaturalSpeechModule module;
	private NavigationButton navButton;
	private final Set<Object> clientEventBusSubscribers = new HashSet<>();
	// endregion

	// region: Development
	// Simulate no text-to-speech runtime
	public static boolean _SIMULATE_NO_TTS;
	// Simulate only operating system text-to-speech runtime
	public static boolean _SIMULATE_MINIMUM_MODE;
	// endregion


	// region: Override Methods
	@Override
	public void configure(Binder binder) {
		pluginSingletonScope = new PluginSingletonScope();
		binder.bindScope(PluginSingleton.class, pluginSingletonScope);

		binder.install(new FactoryModuleBuilder().build(PiperModelMonitorItem.Factory.class));
		binder.install(new FactoryModuleBuilder().build(PiperModelItem.Factory.class));
		binder.install(new FactoryModuleBuilder().build(PiperModelEngine.Factory.class));
		binder.install(new FactoryModuleBuilder().build(VoiceListItem.Factory.class));
	}

	@Override
	public void startUp() {

		if (pluginSingletonScope.isScoping()) {
			// A scoping block may have failed to exit due to uncaught exceptions in startup() or shutdown()
			pluginSingletonScope.exit();
		}

		// Objects marked with @PluginSingleton will enter this scope
		// These objects will be GC-able after pluginSingletonScope.exit()
		pluginSingletonScope.enter();

		// For development, used to simulate when users don't have any TTS available
		_SIMULATE_NO_TTS = config.simulateNoEngine();
		_SIMULATE_MINIMUM_MODE = config.simulateMinimumMode();

		// plugin fields are wrapped in a field object
		try {
			module = injector.getInstance(NaturalSpeechModule.class);
		} catch (ProvisionException e) {
			e.getErrorMessages()
				.forEach(message -> log.error("Provision ErrorResult:{}", message.getMessage(), message.getCause()));
			throw new IllegalStateException("Failed to create NaturalSpeechModule");
		}

		// Abstracting the massive client event handlers into their own files
		// registers to eventbus, unregistered automatically using unregisterClientEventBusAll() in shutdown()
		registerClientEventBus(module.speechEventHandler);
		registerClientEventBus(module.menuEventHandler);
		registerClientEventBus(module.commandExecutedEventHandler);

		registerClientEventBus(module.spamFilterPluglet);
		registerClientEventBus(module.chatFilterPluglet);
		registerClientEventBus(module.volumeManager);

		// Build panel and navButton
		SwingUtilities.invokeLater(() -> {
			navButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(NATURAL_SPEECH_ICON)
				.priority(1)
				.panel(module.topLevelPanel)
				.build();
			clientToolbar.addNavigation(navButton);
		});

		// Load Abbreviations is a method that can be called later when configs are changed
		module.chatHelper.loadCustomReplacements();

		loadSpeechEngines();

		if (config.autoStart()) {
			module.speechManager.start();
		}

		applyConfigVoice(ConfigKeys.PERSONAL_VOICE, config.personalVoiceID());
		applyConfigVoice(ConfigKeys.GLOBAL_NPC_VOICE, config.globalNpcVoice());
		applyConfigVoice(ConfigKeys.SYSTEM_VOICE, config.systemVoice());

		module.audioEngine.setMasterGain(VolumeManager.volumeToGain(config.masterVolume()));

		log.info("NaturalSpeech plugin has started");
	}

	private void loadSpeechEngines() {
		module.speechManager.loadEngine(module.piperEngine);
		module.speechManager.loadEngine(module.sapi4Engine);
		module.speechManager.loadEngine(module.sapi5Engine);
		module.speechManager.loadEngine(module.macSpeechEngine);
	}

	@Override
	public void shutDown() {
		// unregister eventBus so handlers do not run after shutdown.
		unregisterClientEventBusAll();

		module.topLevelPanel.shutdown();

		clientToolbar.removeNavigation(navButton);
		navButton = null;

		module.speechManager.stop();

		saveConfigs();

		module.pluginExecutorService.shutdown();

		pluginSingletonScope.exit(); // objects in this scope will be garbage collected after scope exit
		module = null;

		log.info("NaturalSpeech plugin has shutDown");
	}


	// FIXME(Louis): Apply optimal default setting on reset config
	@Override
	public void resetConfiguration() {
		module.runtimeConfig.reset();
	}

	// endregion

	// region: Event Subscribers

	/**
	 * update AudioEngine 8 times per tick on the client thread. (calculates dynamic volumes)
	 */
	@Schedule(period=600 / 8, unit=ChronoUnit.MILLIS)
	public void updateAudioEngine() {
		module.audioEngine.update();
	}

	@Subscribe
	private void onClientShutdown(ClientShutdown e) {
		// shutDown is not called on X button client exit, so we need to listen to clientShutdown
		saveConfigs();
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		if (module.speechManager.isStarted()) {
			switch (event.getKey()) {
				case ConfigKeys.MUTE_SELF:
					log.trace("Detected mute-self toggle, clearing audio queue.");
					module.speechManager.silence((otherLineName) -> otherLineName.equals(Names.USER));
					break;

				case ConfigKeys.MUTE_OTHERS:
					log.trace("Detected mute-others toggle, clearing audio queue.");
					module.speechManager.silence((otherLineName) -> !otherLineName.equals(Names.USER));
					break;

			}
		}

		switch (event.getKey()) {
			case ConfigKeys.CUSTOM_TEXT_REPLACEMENTS:
				log.trace("Detected abbreviation changes, reloading into TextToSpeech");
				module.chatHelper.loadCustomReplacements();
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
			module.audioEngine.setMasterGain(VolumeManager.volumeToGain(config.masterVolume()));
		}

		if (event.getKey().equals(ConfigKeys.DEVELOPER_SIMULATE_NO_TTS)) {
			NaturalSpeechPlugin._SIMULATE_NO_TTS = config.simulateNoEngine();
		}
		else if (event.getKey().equals(ConfigKeys.DEVELOPER_MINIMUM_MODE)) {
			NaturalSpeechPlugin._SIMULATE_MINIMUM_MODE = config.simulateMinimumMode();
		}

	}

	// endregion

	// region: helpers

	/**
	 * registers and remembers, used to safely unregister all objects with {@link #unregisterClientEventBusAll()}.
	 */
	private void registerClientEventBus(@NonNull Object object) {
		if (clientEventBusSubscribers.contains(object)) {
			log.error("Attempting to double register {} to eventBus, skipping.", object.getClass().getSimpleName());
		}
		else {
			runeliteEventBus.register(object);
			clientEventBusSubscribers.add(object);
		}
	}

	/**
	 * unregisters all eventBus objects registered using {@link #registerClientEventBus(Object)}
	 */
	private void unregisterClientEventBusAll() {
		for (Object object : clientEventBusSubscribers) {
			runeliteEventBus.unregister(object);
		}
		clientEventBusSubscribers.clear();
	}

	private void saveConfigs() {
		module.voiceManager.save();
		module.piperConfig.save();
		module.muteManager.save();
	}

	private void applyConfigVoice(String configKey, String voiceString) {
		VoiceID voiceID;
		voiceID = VoiceID.fromIDString(voiceString);

		switch (configKey) {
			case ConfigKeys.PERSONAL_VOICE:
				if (voiceID != null) {
					log.debug("Setting personal voice to {}", voiceID);
					module.voiceManager.set(EntityID.USER, voiceID);
				}
				else {
					log.debug("Invalid personal voice {}, resetting.", voiceString);
					module.voiceManager.unset(EntityID.USER);
				}
				break;
			case ConfigKeys.GLOBAL_NPC_VOICE:
				if (voiceID != null) {
					log.debug("Setting global npc voice to {}", voiceID);
					module.voiceManager.set(EntityID.GLOBAL_NPC, voiceID);
				}
				else {
					log.debug("Invalid global npc voice to {}, resetting.", voiceString);
					module.voiceManager.unset(EntityID.GLOBAL_NPC);
				}
				break;
			case ConfigKeys.SYSTEM_VOICE:
				if (voiceID != null) {
					log.debug("Setting system voice to {}", voiceID);
					module.voiceManager.set(EntityID.SYSTEM, voiceID);
				}
				else {
					log.debug("Invalid system voice {}, resetting.", voiceString);
					module.voiceManager.unset(EntityID.SYSTEM);
				}
				break;
		}
		saveConfigs();
	}
	// endregion

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}

}
