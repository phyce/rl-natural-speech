package dev.phyce.naturalspeech.utils;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import static dev.phyce.naturalspeech.statics.PluginResources.*;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ChatIconManager;

@PluginSingleton
public class ChatIcons {

	public final ChatIcon muted;
	public final ChatIcon unmuted;
	public final ChatIcon logo;
	public final ChatIcon logo_disabled;
	public final ChatIcon checkbox;
	public final ChatIcon checkboxChecked;
	public final ChatIcon checkmark;
	public final ChatIcon xmark;

	@Inject
	public ChatIcons(ChatIconManager chatIconManager) {

		muted = new ChatIcon(chatIconManager, INGAME_MUTE_ICON);
		unmuted = new ChatIcon(chatIconManager, INGAME_UNMUTE_ICON);
		logo = new ChatIcon(chatIconManager, INGAME_NATURAL_SPEECH_SMALL_ICON);
		logo_disabled = new ChatIcon(chatIconManager, INGAME_NATURAL_SPEECH_SMALL_ICON_DISABLED);
		checkbox = new ChatIcon(chatIconManager, INGAME_CHECKBOX);
		checkboxChecked = new ChatIcon(chatIconManager, INGAME_CHECKBOX_CHECKED);
		checkmark = new ChatIcon(chatIconManager, INGAME_CHECKMARK);
		xmark = new ChatIcon(chatIconManager, INGAME_XMARK);
	}


	public static class ChatIcon {
		private final ChatIconManager iconManager;
		private final int id;

		ChatIcon(ChatIconManager iconManager, BufferedImage image) {
			this.iconManager = iconManager;
			this.id = iconManager.registerChatIcon(image);
		}

		public String get() {
			int index = iconManager.chatIconIndex(id);
			return "<img=" + index + ">";
		}

	}
}
