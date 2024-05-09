package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.entity.EntityID;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_MUTE_ICON;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_UNMUTE_ICON;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.userinterface.ingame.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.utils.Texts;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;

@Slf4j
public class MenuEventHandler {

	private final Client client;
	private final ChatIconManager chatIconManager;
	private final NaturalSpeechConfig config;

	private final SpeechManager speechManager;
	private final Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;

	private final int muteIconId;
	private final int unmuteIconId;

	@Inject
	public MenuEventHandler(
		Client client,
		ChatIconManager chatIconManager,
		NaturalSpeechConfig config,
		SpeechManager speechManager,
		Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider,
		VoiceManager voiceManager,
		MuteManager muteManager
	) {
		this.client = client;
		this.chatIconManager = chatIconManager;
		this.config = config;
		this.speechManager = speechManager;
		this.voiceConfigChatboxTextInputProvider = voiceConfigChatboxTextInputProvider;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;

		muteIconId = chatIconManager.registerChatIcon(INGAME_MUTE_ICON);
		unmuteIconId = chatIconManager.registerChatIcon(INGAME_UNMUTE_ICON);

	}

	@Subscribe
	private void onMenuOpened(MenuOpened event) {
		if (config.holdShiftRightClickMenu() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		if (!speechManager.isStarted()) return;
		final MenuEntry[] entries = event.getMenuEntries();

		List<Integer> interfaces = List.of(
			InterfaceID.FRIEND_LIST,
			InterfaceID.FRIENDS_CHAT,
			InterfaceID.CHATBOX,
			InterfaceID.PRIVATE_CHAT,
			InterfaceID.GROUP_IRON
		);

		List<String> detectableOptions = List.of("Message", "Add friend", "Remove friend");

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (entry.getType() == MenuAction.PLAYER_EIGHTH_OPTION || entry.getType() == MenuAction.EXAMINE_NPC) {
				drawOptions(entry, index);
			}
			else if (interfaces.contains(groupId) && detectableOptions.contains(entry.getOption())) {
				drawOptions(entry, 1);
			}
		}
	}

	/**
	 * $1 name, $2 level text
	 */

	public void drawOptions(MenuEntry entry, int index) {

		@Nullable
		Actor actor = entry.getActor();

		EntityID entityID;
		if (actor == null) {
			entityID = EntityID.name(entry.getTarget());
		}
		else if (actor instanceof NPC) {
			entityID = EntityID.npc((NPC) actor);
		}
		else if (actor instanceof Player) {
			entityID = EntityID.player((Player) actor);
		}
		else {
			log.error("Unknown actor type: {}", actor);
			return;
		}

		boolean isUnmuted = muteManager.isMuted(entityID);
		boolean isListened = muteManager.isListened(entityID);
		boolean isAllowed = muteManager.isAllowed(entityID);

		String statusColorTag = isAllowed ? "<col=78B159>" : "<col=DD2E44>";
		String status = isAllowed ? getIconImgTag(unmuteIconId) : getIconImgTag(muteIconId);


		if (!voiceManager.isActive(voiceManager.get(entityID))) {
			statusColorTag = "<col=888888>";
		}

		VoiceID voiceID = voiceManager.resolve(entityID);
		// reformat the target name
		String target = Texts.removeLevelFromTargetName(entry.getTarget());
//		if (hasSetting) {
//			// re-colorize the target name with the voiceID
		target = String.format("%s %s(%s)</col>", target, statusColorTag, voiceID);
//		}
//		else {
//			target = String.format("%s %s(voice-error)</col>", target, statusColorTag);
//		}

		MenuEntry parent = client.createMenuEntry(index + 1)
			.setOption(status + " Voice")
			.setTarget(target)
			.setType(MenuAction.RUNELITE_SUBMENU);

		{
			final String value = voiceID.toVoiceIDString();
			MenuEntry configVoiceEntry = client.createMenuEntry(1)
				.setOption("Configure")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> voiceConfigChatboxTextInputProvider.get()
					.entityID(entityID)
					.value(value)
					.build());
			configVoiceEntry.setParent(parent);
		}
		if (muteManager.isListenMode()) {
			MenuEntry stopListenEntry = client.createMenuEntry(1)
				.setOption("Stop Listen Mode")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.setListenMode(false);
					muteManager.clearListens();
				});
			stopListenEntry.setParent(parent);
		}
		else {
			if (!isUnmuted) {
				MenuEntry muteEntry = client.createMenuEntry(1)
					.setOption("Mute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						muteManager.mute(entityID);
						speechManager.silence(name -> name.equals(String.valueOf(entityID.hashCode())));
					});
				muteEntry.setParent(parent);

			}
			else {
				MenuEntry unmuteEntry = client.createMenuEntry(1)
					.setOption("Unmute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> muteManager.unmute(entityID));
				unmuteEntry.setParent(parent);
			}
		}

		if (isListened) {
			MenuEntry unlistenEntry = client.createMenuEntry(0)
				.setOption("Unlisten")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.unlisten(entityID);
					speechManager.silenceAll();
				});
			unlistenEntry.setParent(parent);
		}
		else {
			MenuEntry listenEntry = client.createMenuEntry(0)
				.setOption("Listen")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.listen(entityID);
					muteManager.setListenMode(true);
					speechManager.silenceAll();
				});
			listenEntry.setParent(parent);
		}
	}

	private String getIconImgTag(int iconId) {
		int imgId = chatIconManager.chatIconIndex(iconId);
		return "<img=" + imgId + ">";
	}

}
