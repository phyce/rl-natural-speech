package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_MUTE_ICON;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_UNMUTE_ICON;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.userinterface.ingame.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.utils.Texts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.NonNull;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;
import org.checkerframework.checker.nullness.qual.Nullable;

@Slf4j
public class MenuEventHandler {

	private final Client client;
	private final ChatIconManager chatIconManager;
	private final NaturalSpeechConfig config;

	private final SpeechManager speechManager;
	private final Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;
	private final ConfigManager configManager;

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
		MuteManager muteManager,
		ConfigManager configManager
	) {
		this.client = client;
		this.chatIconManager = chatIconManager;
		this.config = config;
		this.speechManager = speechManager;
		this.voiceConfigChatboxTextInputProvider = voiceConfigChatboxTextInputProvider;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.configManager = configManager;

		muteIconId = chatIconManager.registerChatIcon(INGAME_MUTE_ICON);
		unmuteIconId = chatIconManager.registerChatIcon(INGAME_UNMUTE_ICON);

	}

	@Subscribe
	private void onMenuOpened(MenuOpened event) {
		if (config.holdShiftRightClickMenu() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		if (!speechManager.isStarted()) return;
		final MenuEntry[] entries = event.getMenuEntries();


		drawEntityMenu(entries);
		drawChatMenu(entries);

		log.info("MENU DEBUG: \n{}",
			Arrays.stream(entries)
				.reduce("", (acc, entry) -> acc + entry.toString() + "\nMENU DEBUG: ", String::concat
				));
	}

	private void drawSpecialModeMenu(MenuEntry[] entries) {
		List<Integer> interfaces = List.of(
			InterfaceID.CHATBOX
		);


		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];


			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (interfaces.contains(groupId)) {
				List<TabConfigMenu> results = TabConfigMenu.find(Text.removeFormattingTags(entry.getOption()));

				for (TabConfigMenu result : results) {
					_drawMuteConfigMenu(result, 0);
				}
			}
		}
	}

	private void drawChatMenu(MenuEntry[] entries) {
		List<Integer> interfaces = List.of(
			InterfaceID.CHATBOX
		);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];


			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (interfaces.contains(groupId)) {
				List<TabConfigMenu> results = TabConfigMenu.find(Text.removeFormattingTags(entry.getOption()));

				for (TabConfigMenu result : results) {
					_drawMuteConfigMenu(result, 0);
				}
			}
		}
	}


	private void _drawMuteConfigMenu(TabConfigMenu tab, int index) {


		if (tab.children == null) {
			drawSubMenu(null, tab, index, MenuAction.RUNELITE);
		}
		else {
			MenuEntry parent = drawSubMenu(null, tab, index, MenuAction.RUNELITE_SUBMENU);

			for (TabConfigMenu child : tab.children) {
				Preconditions.checkNotNull(child);

				drawSubMenu(parent, child, 0, MenuAction.RUNELITE);
			}

		}
	}

	private MenuEntry drawSubMenu(MenuEntry parent, TabConfigMenu tab, int index, MenuAction menuType) {
		final boolean state = Arrays.stream(tab.configKeys)
			.anyMatch(key -> configManager.getConfiguration(CONFIG_GROUP, key, boolean.class));

		String status = state ? getIconImgTag(unmuteIconId) : getIconImgTag(muteIconId);
		String action = state ? "Mute" : "Unmute";

		return client.createMenuEntry(index + 1)
			.setOption(status + " <col=ffff00>" + action + "</col>")
			.setTarget(tab.name)
			.setType(menuType)
			.setParent(parent)
			.onClick(e -> Arrays.stream(tab.configKeys)
				.forEach(key -> configManager.setConfiguration(CONFIG_GROUP, key, !state))
			);
	}


	private void drawEntityMenu(MenuEntry[] entries) {
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
				drawEntityOptions(entry, index);
			}
			else if (interfaces.contains(groupId) && detectableOptions.contains(entry.getOption())) {
				drawEntityOptions(entry, 0);
			}
		}
	}

	/**
	 * $1 name, $2 level text
	 */

	public void drawEntityOptions(MenuEntry entry, int index) {

		@Nullable
		Actor actor = entry.getActor();

		EntityID entityID;
		if (actor == null) {
			String target = entry.getTarget();
			if (target.isEmpty()) {
				log.error("Empty target: {}", entry);
				return;
			}
			entityID = EntityID.name(target);
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

@AllArgsConstructor
enum SpecialModeMenu {
	FRIENDS_ONLY_MODE(

	)
}

@AllArgsConstructor
enum TabConfigMenu {
	PARENT_GIM_CHAT(
		new String[] {ConfigKeys.GIM_CHAT},
		"Group",
		null,
		new String[] {"Group: Show all"}),

	PARENT_TRADE(
		new String[] {ConfigKeys.REQUESTS},
		"Trade Request",
		null,
		new String[] {"Trade: Show all"}),

	PARENT_CLAN(
		new String[] {ConfigKeys.CLAN_CHAT},
		"Clan",
		null,
		new String[] {"Clan: Show all"}),

	PARENT_CHANNEL(
		new String[] {ConfigKeys.CLAN_GUEST_CHAT},
		"Channel",
		null,
		new String[] {"Channel: Show all"}),

	_PRIVATE_OUT(
		new String[] {ConfigKeys.PRIVATE_OUT_CHAT},
		"Private Sent",
		null,
		null),

	_PRIVATE_IN(
		new String[] {ConfigKeys.PRIVATE_CHAT},
		"Private Received",
		null,
		null),

	PARENT_PRIVATE(
		new String[] {ConfigKeys.PRIVATE_CHAT, ConfigKeys.PRIVATE_OUT_CHAT},
		"Private",
		new TabConfigMenu[] {_PRIVATE_IN, _PRIVATE_OUT},
		new String[] {"Private: Show all"}),

	_DIALOGUE(
		new String[] {ConfigKeys.DIALOG},
		"Dialogue",
		null,
		null),

	_NPC_OVERHEAD(
		new String[] {ConfigKeys.NPC_OVERHEAD},
		"NPC Overhead",
		null,
		null),

	_EXAMINE_MESSAGE(
		new String[] {ConfigKeys.EXAMINE_CHAT},
		"Examine",
		null,
		null),

	_SYSTEM_MESSAGE(
		new String[] {ConfigKeys.SYSTEM_MESSAGES},
		"Notification",
		null,
		null),

	PARENT_GAME(
		new String[] {ConfigKeys.EXAMINE_CHAT, ConfigKeys.SYSTEM_MESSAGES, ConfigKeys.NPC_OVERHEAD, ConfigKeys.DIALOG},
		"Game",
		new TabConfigMenu[] {_EXAMINE_MESSAGE, _SYSTEM_MESSAGE, _NPC_OVERHEAD, _DIALOGUE},
		new String[] {"Game: Filter"}),

	PARENT_PUBLIC(
		new String[] {ConfigKeys.PUBLIC_CHAT},
		"Public",
		null,
		new String[] {"Public: Show autochat"}),

	PARENT_EVERYTHING(
		new String[] {
			ConfigKeys.PUBLIC_CHAT,
			ConfigKeys.EXAMINE_CHAT,
			ConfigKeys.SYSTEM_MESSAGES,
			ConfigKeys.PRIVATE_CHAT,
			ConfigKeys.PRIVATE_OUT_CHAT,
			ConfigKeys.CLAN_GUEST_CHAT,
			ConfigKeys.CLAN_CHAT,
			ConfigKeys.REQUESTS,
			ConfigKeys.GIM_CHAT,
			ConfigKeys.DIALOG,
			ConfigKeys.NPC_OVERHEAD
		},
		"Everything",
		null,
		new String[] {"Set chat mode: Public"}),

	;

	//		PUBLIC("Public", "Public:"),
	//		PRIVATE("Private", "Private:"),
	//		CHANNEL("Channel", "Channel:"),
	//		CLAN("Clan", "Clan:"),
	//		TRADE("Trade", "Trade:");

	@NonNull
	final String[] configKeys;

	@NonNull
	final String name;

	@Nullable
	final TabConfigMenu[] children;

	@Nullable
	final String[] optionHook;

	static List<TabConfigMenu> find(String option) {
		ArrayList<TabConfigMenu> results = new ArrayList<>();
		for (TabConfigMenu tab : TabConfigMenu.values()) {
			if (tab.optionHook != null
				&& Arrays.stream(tab.optionHook)
				.filter(Objects::nonNull)
				.anyMatch(option::startsWith)
			) {
				results.add(tab);
			}
		}
		return results;
	}
}


























