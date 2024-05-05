package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.engine.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.VolumeManager;
import dev.phyce.naturalspeech.tts.engine.PiperEngine;
import dev.phyce.naturalspeech.tts.engine.SAPI4Engine;
import dev.phyce.naturalspeech.tts.engine.SAPI5Engine;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;

/**
 * plugin fields are wrapped in a field object
 * <ul>
 * <li>Enables Guice to perform unordered cyclic dependency injection (through proxies)</li>
 * <li>Allows plugin objects to leave scope and be garbage collected. Otherwise, RuneLite Plugin objects never leave scope.</li>
 * <li>Allows better hot-reloading because we can re-instantiate plugin objects</li>
 * </ul>
 * Could be an abstract module, but we're lazy-injecting anyway.
 */
@PluginSingleton
class NaturalSpeechModule {
	final NaturalSpeechRuntimeConfig runtimeConfig;
	final VoiceManager voiceManager;
	final MuteManager muteManager;
	final TextToSpeech textToSpeech;
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

	@Inject
	public NaturalSpeechModule(
		NaturalSpeechRuntimeConfig runtimeConfig,
		VoiceManager voiceManager,
		MuteManager muteManager,
		VolumeManager volumeManager,
		AudioEngine audioEngine,
		TopLevelPanel topLevelPanel,

		TextToSpeech textToSpeech,
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
		PluginEventBus pluginEventBus

	) {
		this.runtimeConfig = runtimeConfig;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.textToSpeech = textToSpeech;
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
	}

}
