package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.audio.AudioEngine;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_MUTE_ICON;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_UNMUTE_ICON;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

public class NavButtonModule implements PluginModule {

	private final ConfigManager configManager;
	private final ClientToolbar clientToolbar;
	private final AudioEngine audioEngine;
	private NavigationButton muteNavButton;
	private NavigationButton unmuteNavButton;
	private static final int NAVBUTTON_PRIORITY = -10;

	@Inject
	public NavButtonModule(
			ConfigManager configManager,
			ClientToolbar clientToolbar,
			AudioEngine audioEngine
	) {
		this.configManager = configManager;
		this.clientToolbar = clientToolbar;
		this.audioEngine = audioEngine;
	}

	@Override
	public void startUp() {
		muteNavButton = NavigationButton.builder()
				.tooltip("Mute Natural Speech")
				.icon(INGAME_UNMUTE_ICON)
				.priority(NAVBUTTON_PRIORITY)
				.onClick(() -> setMute(true))
				.build();

		unmuteNavButton = NavigationButton.builder()
				.tooltip("Unmute Natural Speech")
				.icon(INGAME_MUTE_ICON)
				.priority(NAVBUTTON_PRIORITY)
				.onClick(() -> setMute(false))
				.build();

		refresh();
	}

	private void refresh() {
		clientToolbar.removeNavigation(unmuteNavButton);
		clientToolbar.removeNavigation(muteNavButton);
		clientToolbar.addNavigation(audioEngine.isMuted() ? unmuteNavButton : muteNavButton);
	}

	private void setMute(boolean mute) {
		audioEngine.setMute(mute);
		refresh();
	}

	@Override
	public void shutDown() {
		clientToolbar.removeNavigation(unmuteNavButton);
		clientToolbar.removeNavigation(muteNavButton);
	}
}
