package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.userinterface.ingame.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.utils.ChatIcons;
import dev.phyce.naturalspeech.utils.TextUtil;
import net.runelite.api.Menu;
import static dev.phyce.naturalspeech.utils.Utils.inArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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
	private final NaturalSpeechConfig config;

	private final SpeechManager speechManager;
	private final Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;
	private final ConfigManager configManager;
	private final ChatIcons icons;

	@Inject
	public MenuEventHandler(
		Client client,
		ChatIconManager chatIconManager,
		NaturalSpeechConfig config,
		SpeechManager speechManager,
		Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider,
		VoiceManager voiceManager,
		MuteManager muteManager,
		ConfigManager configManager,
		ChatIcons chatIcons
	) {
		this.client = client;
		this.config = config;
		this.speechManager = speechManager;
		this.voiceConfigChatboxTextInputProvider = voiceConfigChatboxTextInputProvider;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;
		this.configManager = configManager;
		this.icons = chatIcons;
	}

	@Subscribe
	private void onMenuOpened(MenuOpened event) {
		if (config.holdShiftRightClickMenu() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		if (!speechManager.isStarted()) return;
		final MenuEntry[] entries = event.getMenuEntries();

		drawEntityMenu(entries);
		drawMuteMenus(entries);
		drawVoiceMenus(entries);
		drawVolumeMenus(entries);

		log.info("MENU DEBUG: \n{}", Arrays.stream(entries)
			.reduce("", (acc, entry) -> acc + entry.toString() + "\nMENU DEBUG: ", String::concat)
		);
	}

	private void drawVolumeMenus(MenuEntry[] entries) {
		List<Integer> interfaces = List.of(InterfaceID.CHATBOX);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (interfaces.contains(groupId)
				&& Text.removeFormattingTags(entry.getOption()).contains("Set chat mode: Public")) {
				MenuEntry parent = client.createMenuEntry(index)
					.setTarget(icons.unmute.get() + "Set Volume")
					.setType(MenuAction.RUNELITE);

				Menu subMenu = parent.createSubMenu();

				final String[] colorScheme = new String[] {
					"808080",
					"878b7d",
					"8d977a",
					"93a277",
					"99ae74",
					"9fba70",
					"a4c56b",
					"a9d166",
					"aede60",
					"b3ea5a",
					"b8f652",
				};

				final int currentVolumeIndex = config.masterVolume() / 10;

				for (int i = colorScheme.length - 1; i > -1; i--) {
					final int volume = i * 10;
					final String colorTag = "<col=" + colorScheme[i] + ">";
					final String meter = i == currentVolumeIndex ? "> " : "- ";
					subMenu.createMenuEntry(0)
						.setTarget(meter + colorTag + volume + "%")
						.setType(MenuAction.RUNELITE)
						.onClick(
							(e) -> configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.MASTER_VOLUME, volume)
						);
				}
			}
		}
	}

	private void drawVoiceMenus(MenuEntry[] entries) {
		List<Integer> interfaces = List.of(InterfaceID.CHATBOX);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (interfaces.contains(groupId)) {
				List<VoiceConfigMenu> results = VoiceConfigMenu.find(Text.removeFormattingTags(entry.getOption()));

				for (VoiceConfigMenu result : results) {
					if (result.children == null) {
						_drawVoiceMenu(null, result, 0, MenuAction.RUNELITE);
					}
					else {
						MenuEntry parent = _drawVoiceMenu(null, result, 0, MenuAction.RUNELITE);

						Menu subMenu = parent.createSubMenu();

						for (VoiceConfigMenu child : result.children) {
							Preconditions.checkNotNull(child);

							_drawVoiceMenu(subMenu, child, 0, MenuAction.RUNELITE);
						}
					}
				}
			}
		}
	}

	private MenuEntry _drawVoiceMenu(Menu subMenu, VoiceConfigMenu tab, int index, MenuAction menuType) {

		String iconTag = drawIconTag(tab.icon, true);

		String action = tab.actionName;

		return subMenu.createMenuEntry(index + 1)
			.setOption(iconTag + action)
			.setTarget(tab.targetName)
			.setType(menuType)
			.onClick(e -> {
				if (tab.entityID == null) return;
				if (tab.configKey == null) return;

				voiceConfigChatboxTextInputProvider.get()
					.configKey(tab.configKey)
					.entityID(tab.entityID)
					.value(voiceManager.get(tab.entityID).transform(VoiceID::toString).or(""))
					.build();
			});
	}

	private void drawMuteMenus(MenuEntry[] entries) {
		List<Integer> interfaces = List.of(InterfaceID.CHATBOX);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (interfaces.contains(groupId)) {
				List<TabConfigMenu> results = TabConfigMenu.find(Text.removeFormattingTags(entry.getOption()));

				for (TabConfigMenu result : results) {
					if (result.children == null) {
						_drawMuteMenu(null, result, 0, MenuAction.RUNELITE);
					}
					else {
						MenuEntry parent = _drawMuteMenu(null, result, 0, MenuAction.RUNELITE);

						Menu subMenu = parent.createSubMenu();

						for (TabConfigMenu child : result.children) {
							Preconditions.checkNotNull(child);

							_drawMuteMenu(subMenu, child, 0, MenuAction.RUNELITE);
						}
					}
				}
			}
		}
	}

	private String drawIconTag(MenuIconSet set, boolean state) {
		switch (set) {
			case MUTE_UNMUTE:
				return state ? icons.unmute.get() : icons.mute.get();

			case UNMUTE_MUTE:
				return !state ? icons.unmute.get() : icons.mute.get();

			case CHECK_CROSS_DIAMOND:
				return state ? icons.checkboxChecked.get() : icons.checkbox.get();

			case LOGO:
				return icons.logo.get();

			case NO_ICON:
			default:
				return "";
		}
	}

	private MenuEntry _drawMuteMenu(Menu subMenu, TabConfigMenu tab, int index, MenuAction menuType) {
		final boolean state = Arrays.stream(tab.configKeys)
			.anyMatch(key -> configManager.getConfiguration(CONFIG_GROUP, key, boolean.class));

		String iconTag = drawIconTag(tab.icon, state);

		String action = state ? tab.falseAction : tab.trueAction;

		return subMenu.createMenuEntry(index + 1)
			.setOption(iconTag + action)
			.setTarget(tab.name)
			.setType(menuType)
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

			MenuAction[] triggerEntries = new MenuAction[]{
				MenuAction.PLAYER_EIGHTH_OPTION,
				MenuAction.EXAMINE_NPC
			};

			if(inArray(entry.getType(), triggerEntries)) {
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
		String status = isAllowed ? icons.unmute.get() : icons.mute.get();


		if (!voiceManager.get(entityID).transform(voiceManager::isActive).or(false)) {
			statusColorTag = "<col=888888>";
		}

		VoiceID voiceID = voiceManager.resolve(entityID);
		// reformat the target name
		String target = TextUtil.removeLevelFromTargetName(entry.getTarget());
		//		if (hasSetting) {
		//			// re-colorize the target name with the voiceID
		target = String.format("%s %s(%s)</col>", target, statusColorTag, voiceID);
		//		}
		//		else {
		//			target = String.format("%s %s(voice-error)</col>", target, statusColorTag);
		//		}

		MenuEntry parent = client.createMenuEntry(index + 1)
			.setOption(status + "Voice")
			.setTarget(target)
			.setType(MenuAction.RUNELITE);

		Menu subMenu = parent.createSubMenu();

		{
			final String value = voiceID.toVoiceIDString();
			subMenu.createMenuEntry(0)
				.setOption("Configure")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> voiceConfigChatboxTextInputProvider.get()
					.entityID(entityID)
					.value(value)
					.build());
		}
		if (muteManager.isListenMode()) {
			subMenu.createMenuEntry(0)
				.setOption("Stop Listen Mode")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.setListenMode(false);
					muteManager.clearListens();
				});
		}
		else {
			if (!isUnmuted) {
				subMenu.createMenuEntry(0)
					.setOption("Mute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						muteManager.mute(entityID);
						speechManager.silence(name -> name.equals(String.valueOf(entityID.hashCode())));
					});
			}
			else {
				subMenu.createMenuEntry(0)
					.setOption("Unmute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> muteManager.unmute(entityID));
			}
		}

		if (isListened) {
			subMenu.createMenuEntry(0)
				.setOption("Unlisten")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.unlisten(entityID);
					speechManager.silenceAll();
				});
		}
		else {
			subMenu.createMenuEntry(0)
				.setOption("Listen")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.listen(entityID);
					muteManager.setListenMode(true);
					speechManager.silenceAll();
				});
		}
	}

}

enum MenuIconSet {
	NO_ICON,
	MUTE_UNMUTE,
	UNMUTE_MUTE,
	LOGO,
	CHECK_CROSS_DIAMOND
}

@AllArgsConstructor
enum VoiceConfigMenu {
	YOUR_VOICE(
		ConfigKeys.PERSONAL_VOICE,
		EntityID.USER,
		"",
		"Yourself",
		null,
		null,
		MenuIconSet.LOGO
	),

	GLOBAL_NPC_VOICE(
		ConfigKeys.GLOBAL_NPC_VOICE,
		EntityID.GLOBAL_NPC,
		"",
		"Global NPC",
		null,
		null,
		MenuIconSet.LOGO
	),

	SYSTEM_VOICE(
		ConfigKeys.SYSTEM_VOICE,
		EntityID.SYSTEM,
		"",
		"System",
		null,
		null,
		MenuIconSet.LOGO
	),

	PARENT(
		null,
		null,
		"Voices",
		"Configure",
		new VoiceConfigMenu[] {YOUR_VOICE, GLOBAL_NPC_VOICE, SYSTEM_VOICE},
		new String[] {"Set chat mode: Public"},
		MenuIconSet.LOGO
	);

	@Nullable
	final String configKey;
	@Nullable
	final EntityID entityID;
	@NonNull
	final String targetName;
	@NonNull
	final String actionName;
	@Nullable
	final VoiceConfigMenu[] children;
	@Nullable
	final String[] optionHook;
	@NonNull
	final MenuIconSet icon;

	static List<VoiceConfigMenu> find(String option) {
		return Arrays.stream(VoiceConfigMenu.values())
			.filter(tab -> tab.optionHook != null)
			.filter(tab -> Arrays.stream(tab.optionHook).filter(Objects::nonNull).anyMatch(option::startsWith))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}


@AllArgsConstructor
enum TabConfigMenu {
	_DIVIDER(
		new String[] {},
		"",
		null,
		null,
		"---------", "---------",
		MenuIconSet.NO_ICON
	),

	_SYSTEM_MESSAGE(
		new String[] {ConfigKeys.SYSTEM_MESSAGES},
		"System Notification",
		null,
		null,
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	PARENT_GIM_CHAT(
		new String[] {ConfigKeys.GIM_CHAT},
		"Group",
		new TabConfigMenu[] {_SYSTEM_MESSAGE},
		new String[] {"Group: Show all"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	PARENT_TRADE(
		new String[] {ConfigKeys.REQUESTS},
		"Trade Request",
		null,
		new String[] {"Trade: Show all"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	PARENT_CLAN(
		new String[] {ConfigKeys.CLAN_CHAT},
		"Clan",
		new TabConfigMenu[] {_SYSTEM_MESSAGE},
		new String[] {"Clan: Show all"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	PARENT_CHANNEL(
		new String[] {ConfigKeys.CLAN_GUEST_CHAT},
		"Channel",
		new TabConfigMenu[] {_SYSTEM_MESSAGE},
		new String[] {"Channel: Show all"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_PRIVATE_OUT(
		new String[] {ConfigKeys.PRIVATE_OUT_CHAT},
		"Private Sent",
		null,
		null,
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_PRIVATE_IN(
		new String[] {ConfigKeys.PRIVATE_CHAT},
		"Private Received",
		null,
		null,
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_FRIEND_ONLY_MODE(
		new String[] {ConfigKeys.FRIENDS_ONLY_MODE},
		"Friends Only Mode",
		null,
		null,
		"<col=ffff00>Disable</col>", "<col=ffff00>Enable</col>",
		MenuIconSet.CHECK_CROSS_DIAMOND
	),

	PARENT_PRIVATE(
		new String[] {ConfigKeys.PRIVATE_CHAT, ConfigKeys.PRIVATE_OUT_CHAT},
		"Private",
		new TabConfigMenu[] {_PRIVATE_IN, _PRIVATE_OUT, _FRIEND_ONLY_MODE},
		new String[] {"Private: Show all"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_DIALOGUE(
		new String[] {ConfigKeys.DIALOG},
		"Dialogue",
		null,
		null,
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_NPC_OVERHEAD(
		new String[] {ConfigKeys.NPC_OVERHEAD},
		"NPC Overhead",
		null,
		null,
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_EXAMINE_MESSAGE(
		new String[] {ConfigKeys.EXAMINE_CHAT},
		"Examine",
		null,
		null,
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	PARENT_GAME(
		new String[] {ConfigKeys.EXAMINE_CHAT, ConfigKeys.SYSTEM_MESSAGES, ConfigKeys.NPC_OVERHEAD, ConfigKeys.DIALOG},
		"Game",
		new TabConfigMenu[] {_SYSTEM_MESSAGE, _EXAMINE_MESSAGE, _NPC_OVERHEAD, _DIALOGUE},
		new String[] {"Game: Filter"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	PARENT_PUBLIC(
		new String[] {ConfigKeys.PUBLIC_CHAT},
		"Public",
		null,
		new String[] {"Public: Show autochat"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	_MUTE_YOURSELF(
		new String[] {ConfigKeys.MUTE_SELF},
		"Yourself",
		null,
		null,
		"<col=ffff00>Unmute</col>", "<col=ffff00>Mute</col>",
		MenuIconSet.UNMUTE_MUTE
	),

	_MUTE_OTHERS(
		new String[] {ConfigKeys.MUTE_OTHERS},
		"Others",
		null,
		null,
		"<col=ffff00>Unmute</col>", "<col=ffff00>Mute</col>",
		MenuIconSet.UNMUTE_MUTE
	),


	_MUTE_GRAND_EXCHANGE(
		new String[] {ConfigKeys.MUTE_GRAND_EXCHANGE},
		"In Grand Exchange",
		null,
		null,
		"<col=ffff00>Unmute</col>", "<col=ffff00>Mute</col>",
		MenuIconSet.UNMUTE_MUTE
	),


	PARENT_ALL(
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
		"All",
		new TabConfigMenu[] {
			_FRIEND_ONLY_MODE,
			_MUTE_GRAND_EXCHANGE,
			_MUTE_OTHERS,
			_MUTE_YOURSELF,
			_DIVIDER,
			_SYSTEM_MESSAGE,
			PARENT_GAME,
			PARENT_PUBLIC,
			PARENT_PRIVATE,
			PARENT_CHANNEL,
			PARENT_CLAN,
			PARENT_CLAN,
			PARENT_TRADE},
		new String[] {"Set chat mode: Public"},
		"<col=ffff00>Mute</col>", "<col=ffff00>Unmute</col>",
		MenuIconSet.MUTE_UNMUTE
	),

	;

	@NonNull
	final String[] configKeys;
	@NonNull
	final String name;
	@Nullable
	final TabConfigMenu[] children;
	@Nullable
	final String[] optionHook;
	@NonNull
	final String falseAction;
	@NonNull
	final String trueAction;
	final MenuIconSet icon;

	static List<TabConfigMenu> find(String option) {
		return Arrays.stream(TabConfigMenu.values())
			.filter(tab -> tab.optionHook != null)
			.filter(tab -> Arrays.stream(tab.optionHook).filter(Objects::nonNull).anyMatch(option::startsWith))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
