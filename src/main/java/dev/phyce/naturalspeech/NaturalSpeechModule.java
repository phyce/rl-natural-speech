package dev.phyce.naturalspeech;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.configs.PiperConfig;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.configs.TutorialHints;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.spamdetection.ChatFilterPluglet;
import dev.phyce.naturalspeech.spamdetection.SpamFilterPluglet;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import dev.phyce.naturalspeech.userinterface.TopLevelPanel;
import dev.phyce.naturalspeech.utils.ChatHelper;

/**
 * plugin fields are wrapped in a field object
 * <ul>
 * <li>Allows plugin objects to leave scope and be garbage collected. Otherwise, RuneLite Plugin objects never leave scope.</li>
 * <li>Allows better hot-reloading because we can re-instantiate plugin objects</li>
 * </ul>
 * Could be an abstract module inside a childInjector, but we're lazy-injecting anyway.
 */
@PluginSingleton
class NaturalSpeechModule {
	final ImmutableSet<PluginModule> submodules;

	@Inject
	public NaturalSpeechModule(
			RuntimePathConfig runtimeConfig,
			VoiceManager voiceManager,
			MuteManager muteManager,
			VolumeManager volumeManager,
			AudioEngine audioEngine,
			SpeechManager speechManager,
			PiperConfig piperConfig,
			SpamFilterPluglet spamFilterPluglet,
			ChatFilterPluglet chatFilterPluglet,
			PluginExecutorService pluginExecutorService,
			ChatHelper chatHelper,
			TutorialHints tutorialHints,

			TopLevelPanel topLevelPanel,

			SpeechModule speechModule,
			MenuModule menuModule,
			CommandModule commandModule,
			NavButtonModule navButtonModule
	) {

		ImmutableSet.Builder<PluginModule> builder = ImmutableSet.builder();
		builder.add(runtimeConfig);
		builder.add(voiceManager);
		builder.add(muteManager);
		builder.add(volumeManager);
		builder.add(audioEngine);
		builder.add(topLevelPanel);
		builder.add(speechManager);
		builder.add(piperConfig);
		builder.add(spamFilterPluglet);
		builder.add(chatFilterPluglet);
		builder.add(speechModule);
		builder.add(menuModule);
		builder.add(commandModule);
		builder.add(chatHelper);
		builder.add(pluginExecutorService);
		builder.add(tutorialHints);
		builder.add(navButtonModule);

		this.submodules = builder.build();
	}

}
