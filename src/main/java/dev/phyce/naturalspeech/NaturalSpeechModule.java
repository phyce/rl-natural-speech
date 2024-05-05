package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.clienteventhandlers.CommandExecutedEventHandler;
import dev.phyce.naturalspeech.clienteventhandlers.MenuEventHandler;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.spamdetection.SpamDetection;
import dev.phyce.naturalspeech.clienteventhandlers.SpeechEventHandler;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperEngine;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4.SAPI4Engine;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Engine;
import dev.phyce.naturalspeech.userinterface.panels.TopLevelPanel;
import dev.phyce.naturalspeech.utils.ChatHelper;
import dev.phyce.naturalspeech.utils.ClientHelper;

/**
 * plugin fields are wrapped in a field object
 * <ul>
 * <li>Enables Guice to perform unordered cyclic dependency injection (through proxies)</li>
 * <li>Allows plugin objects to leave scope and be garbage collected. Otherwise, RuneLite Plugin objects never leave scope.</li>
 * <li>Allows better hot-reloading because we can re-instantiate plugin objects</li>
 * </ul>
 * Could be an abstract module inside a childInjector, but we're lazy-injecting anyway.
 */
@PluginSingleton
class NaturalSpeechModule {
	final RuntimePathConfig runtimeConfig;
	final VoiceManager voiceManager;
	final MuteManager muteManager;
	final SpeechManager speechManager;
	final PiperEngine piperEngine;
	final SAPI4Engine sapi4Engine;
	final SAPI5Engine sapi5Engine;
	final SpamFilterPluglet spamFilterPluglet;
	final ChatFilterPluglet chatFilterPluglet;
	final SpamDetection spamDetection;
	final SpeechEventHandler speechEventHandler;
	final MenuEventHandler menuEventHandler;
	final CommandExecutedEventHandler commandExecutedEventHandler;
	final VolumeManager volumeManager;
	final AudioEngine audioEngine;
	final TopLevelPanel topLevelPanel;
	final PluginEventBus pluginEventBus;
	final PluginExecutorService pluginExecutorService;
	final ClientHelper clientHelper;
	final ChatHelper chatHelper

	@Inject
	public NaturalSpeechModule(
		RuntimePathConfig runtimeConfig,
		VoiceManager voiceManager,
		MuteManager muteManager,
		VolumeManager volumeManager,
		AudioEngine audioEngine,
		TopLevelPanel topLevelPanel,

		SpeechManager speechManager,
		PiperEngine piperEngine,
		SAPI4Engine sapi4Engine,
		SAPI5Engine sapi5Engine,

		SpamFilterPluglet spamFilterPluglet,
		ChatFilterPluglet chatFilterPluglet,
		SpamDetection spamDetection,

		SpeechEventHandler speechEventHandler,
		MenuEventHandler menuEventHandler,
		CommandExecutedEventHandler commandExecutedEventHandler,

		// This Executor is entirely separate from RuneLites executor service.
		// See Plugin configure function for binding
		PluginExecutorService pluginExecutorService,
		// This private eventbus is entirely separate from RuneLites' EventBus
		PluginEventBus pluginEventBus,
		ClientHelper clientHelper,
		ChatHelper chatHelper

	) {
		this.runtimeConfig = runtimeConfig;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.speechManager = speechManager;
		this.piperEngine = piperEngine;
		this.sapi4Engine = sapi4Engine;
		this.sapi5Engine = sapi5Engine;
		this.spamFilterPluglet = spamFilterPluglet;
		this.chatFilterPluglet = chatFilterPluglet;
		this.spamDetection = spamDetection;
		this.speechEventHandler = speechEventHandler;
		this.menuEventHandler = menuEventHandler;
		this.commandExecutedEventHandler = commandExecutedEventHandler;
		this.volumeManager = volumeManager;
		this.audioEngine = audioEngine;
		this.topLevelPanel = topLevelPanel;
		this.pluginEventBus = pluginEventBus;
		this.pluginExecutorService = pluginExecutorService;
		this.clientHelper = clientHelper;
		this.chatHelper = chatHelper;
	}

}
