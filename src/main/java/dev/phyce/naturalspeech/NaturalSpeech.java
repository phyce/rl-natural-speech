package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.guice.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.VolumeManager;
import dev.phyce.naturalspeech.ui.panels.TopLevelPanel;

/**
 * plugin fields are wrapped in a field object
 * 1. Enables Guice to perform unordered cyclic dependency injection (through proxies)
 * 2. Allows plugin objects to leave scope and be garbage collected
 * 3. Allows better hot-reloading because we can re-instantiate plugin objects
 */
@PluginSingleton
class NaturalSpeech {
	final NaturalSpeechRuntimeConfig runtimeConfig;
	final VoiceManager voiceManager;
	final MuteManager muteManager;
	final TextToSpeech textToSpeech;
	final SpamFilterPluglet spamFilterPluglet;
	final ChatFilterPluglet chatFilterPluglet;
	final SpamDetection spamDetection;
	final SpeechEventHandler speechEventHandler;
	final MenuEventHandler menuEventHandler;
	final CommandExecutedEventHandler commandExecutedEventHandler;
	final VolumeManager volumeManager;
	final AudioEngine audioEngine;
	final TopLevelPanel topLevelPanel;

	@Inject
	public NaturalSpeech(
		NaturalSpeechRuntimeConfig runtimeConfig,
		VoiceManager voiceManager,
		MuteManager muteManager,
		TextToSpeech textToSpeech,
		SpamFilterPluglet spamFilterPluglet,
		ChatFilterPluglet chatFilterPluglet,
		SpamDetection spamDetection,
		SpeechEventHandler speechEventHandler,
		MenuEventHandler menuEventHandler,
		CommandExecutedEventHandler commandExecutedEventHandler,
		VolumeManager volumeManager,
		AudioEngine audioEngine,
		TopLevelPanel topLevelPanel
	) {
		this.runtimeConfig = runtimeConfig;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.textToSpeech = textToSpeech;
		this.spamFilterPluglet = spamFilterPluglet;
		this.chatFilterPluglet = chatFilterPluglet;
		this.spamDetection = spamDetection;
		this.speechEventHandler = speechEventHandler;
		this.menuEventHandler = menuEventHandler;
		this.commandExecutedEventHandler = commandExecutedEventHandler;
		this.volumeManager = volumeManager;
		this.audioEngine = audioEngine;
		this.topLevelPanel = topLevelPanel;
	}

}
