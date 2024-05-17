package dev.phyce.naturalspeech.utils;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import static dev.phyce.naturalspeech.statics.PluginResources.*;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ChatIconManager;

@PluginSingleton
public class ChatIcons {

	public final ChatIcon mute;
	public final ChatIcon unmute;
	public final ChatIcon logo;
	public final ChatIcon checkbox;
	public final ChatIcon checkboxChecked;
	public final ChatIcon checkmark;
	public final ChatIcon xmark;

	@Inject
	public ChatIcons(ChatIconManager chatIconManager) {

		mute = new ChatIcon(chatIconManager, INGAME_MUTE_ICON);
		unmute = new ChatIcon(chatIconManager, INGAME_UNMUTE_ICON);
		logo = new ChatIcon(chatIconManager, INGAME_NATURAL_SPEECH_SMALL_ICON);
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
			int idx = iconManager.chatIconIndex(id);
			return "<img=" + idx + ">";
		}
	}
}
