package dev.phyce.naturalspeech.clienteventhandlers;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_MUTE_ICON;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_UNMUTE_ICON;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.userinterface.ingame.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.utils.Standardize;
import dev.phyce.naturalspeech.utils.Texts;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
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
		Actor actor = entry.getActor();

		// if there are no targets for this menu entry, it should be a client ui menu entry.
		String standardActorName = actor != null
			// true: Dawncore (level-90)
			// false: <img=123><col=ffffff>Dawncore</col>
			? Standardize.name(actor) : Standardize.name(entry.getTarget());

		checkNotNull(standardActorName);

		Standardize.SID sid;
		if (actor instanceof NPC) {
			sid = new Standardize.SID((NPC) actor);
		}
		else {
			sid = new Standardize.SID(standardActorName);
		}


		boolean isUnmuted = muteManager.isMuted(sid);
		boolean isListened = muteManager.isListened(sid);
		boolean isAllowed = muteManager.isAllowed(sid);

		String statusColorTag = isAllowed ? "<col=78B159>" : "<col=DD2E44>";
		String status = isAllowed ? getIconImgTag(unmuteIconId) : getIconImgTag(muteIconId);

		VoiceID voiceID = voiceManager.getVoice(sid);

		if (!voiceManager.contains(sid)) {
			statusColorTag = "<col=888888>";
		}

		// reformat the target name
		String target = Texts.removeLevelFromTargetName(entry.getTarget());
		if (voiceID != null) {
			// re-colorize the target name with the voiceID
			target = String.format("%s %s(%s)</col>", target, statusColorTag, voiceID);
		}
		else {
			target = String.format("%s %s(voice-error)</col>", target, statusColorTag);
		}

		MenuEntry parent = client.createMenuEntry(index + 1)
			.setOption(status + " Voice")
			.setTarget(target)
			.setType(MenuAction.RUNELITE_SUBMENU);

		{
			final String value = voiceID != null ? voiceID.toVoiceIDString() : "";
			MenuEntry configVoiceEntry = client.createMenuEntry(1)
				.setOption("Configure")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					voiceConfigChatboxTextInputProvider.get()
						.configSID(sid)
						.value(value)
						.build();
				});
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
			if (isUnmuted) {
				MenuEntry muteEntry = client.createMenuEntry(1)
					.setOption("Mute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> muteManager.mute(sid));
				muteEntry.setParent(parent);

			}
			else {
				MenuEntry unmuteEntry = client.createMenuEntry(1)
					.setOption("Unmute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> muteManager.mute(sid));
				unmuteEntry.setParent(parent);
			}
		}

		if (isListened) {
			MenuEntry unlistenEntry = client.createMenuEntry(0)
				.setOption("Unlisten")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> muteManager.mute(sid));
			unlistenEntry.setParent(parent);
		}
		else {
			MenuEntry listenEntry = client.createMenuEntry(0)
				.setOption("Listen")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.mute(sid);
					muteManager.setListenMode(true);
				});
			listenEntry.setParent(parent);
		}
	}

	private String getIconImgTag(int iconId) {
		int imgId = chatIconManager.chatIconIndex(iconId);
		return "<img=" + imgId + ">";
	}

}
