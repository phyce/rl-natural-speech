package dev.phyce.naturalspeech;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.singleton.PluginSingletonScope;
import dev.phyce.naturalspeech.texttospeech.engine.PiperEngine;
import dev.phyce.naturalspeech.userinterface.mainsettings.PiperModelMonitorItem;
import dev.phyce.naturalspeech.userinterface.voiceexplorer.VoiceListItem;
import dev.phyce.naturalspeech.userinterface.voicehub.PiperModelItem;
import java.awt.Dimension;
import java.awt.Graphics2D;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.slf4j.LoggerFactory;

@Slf4j
@PluginDescriptor(name=CONFIG_GROUP)
public class NaturalSpeechPlugin extends Plugin {
	public static final String VERSION = "2.0";
	public static final String CONFIG_GROUP = "NaturalSpeech";

	@Inject
	private EventBus clientEventBus;

	// Scope holds references to all the internal plugin singletons.
	// provides them to guice for injection, then clears them out on shutdown
	private final PluginSingletonScope pluginSingletonScope = new PluginSingletonScope();
	private NaturalSpeechModule module;

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

	private final OverlayMenuClicked phonyOpenConfigEvent = new OverlayMenuClicked(
			new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null),
			new Overlay(NaturalSpeechPlugin.this) {
				@Override
				public Dimension render(Graphics2D graphics) { return null;}
			});

	@Override
	public void startUp() {

		if (pluginSingletonScope.isScoping()) {
			// A scoping block may have failed to exit due to uncaught exceptions in startup() or shutdown()
			pluginSingletonScope.exit();
		}

		// Objects marked with @PluginSingleton will enter this scope
		pluginSingletonScope.enter();

		try {
			// plugin fields are wrapped in a field object
			module = injector.getInstance(NaturalSpeechModule.class);
		} catch (ProvisionException e) {
			e.getErrorMessages()
					.forEach(message -> log.error("Provision ErrorResult:{}", message.getMessage(), message.getCause()));
			throw new IllegalStateException("Failed to create NaturalSpeechModule");
		}

		module.submodules.forEach(clientEventBus::register);
		module.submodules.forEach(PluginModule::startUp);

		log.info("NaturalSpeech plugin has started");
	}

	@Override
	public void shutDown() {

		module.submodules.forEach(PluginModule::shutDown);
		module.submodules.forEach(clientEventBus::unregister);

		// objects in this scope will be garbage collected after scope exit
		pluginSingletonScope.exit();
		module = null;

		log.info("NaturalSpeech plugin has shutDown");
	}


	@Override
	public void resetConfiguration() {
		module.submodules.forEach(PluginModule::resetConfiguration);
	}

	public void openConfiguration() {
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.clientEventBus.post(phonyOpenConfigEvent);
	}

	@Override
	public void configure(Binder binder) {

		binder.bindScope(PluginSingleton.class, pluginSingletonScope);

		FactoryModuleBuilder factoryBuilder = new FactoryModuleBuilder();
		binder.install(factoryBuilder.build(PiperModelMonitorItem.Factory.class));
		binder.install(factoryBuilder.build(PiperModelItem.Factory.class));
		binder.install(factoryBuilder.build(PiperEngine.Factory.class));
		binder.install(factoryBuilder.build(VoiceListItem.Factory.class));

		binder.disableCircularProxies();
	}

	@Provides
	NaturalSpeechConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NaturalSpeechConfig.class);
	}
}
