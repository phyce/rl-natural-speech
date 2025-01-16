package dev.phyce.naturalspeech;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.audio.AudioEngine;
import static dev.phyce.naturalspeech.MenuStrings.CONFIG_STRING;
import static dev.phyce.naturalspeech.MenuStrings.SPEAK_STRING;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import dev.phyce.naturalspeech.userinterface.ingame.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.utils.ChatIcons;
import dev.phyce.naturalspeech.utils.ChatIcons.ChatIcon;
import dev.phyce.naturalspeech.utils.TextUtil;
import net.runelite.api.Menu;
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
import net.runelite.client.util.Text;
import org.checkerframework.checker.nullness.qual.Nullable;

@Slf4j
public class MenuModule implements PluginModule {

	private final Client client;
	private final NaturalSpeechConfig config;
	private final SpeechManager speechManager;
	private final Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;
	private final VoiceManager voiceManager;
	private final AudioEngine audioEngine;
	private final MuteManager muteManager;
	private final ConfigManager configManager;
	private final ChatIcons icons;

	@Inject
	public MenuModule(
		Client client,
		NaturalSpeechConfig config,
		SpeechManager speechManager,
		Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider,
		VoiceManager voiceManager,
		AudioEngine audioEngine,
		MuteManager muteManager,
		ConfigManager configManager,
		ChatIcons chatIcons
	) {
		this.client = client;
		this.config = config;
		this.speechManager = speechManager;
		this.voiceConfigChatboxTextInputProvider = voiceConfigChatboxTextInputProvider;
		this.voiceManager = voiceManager;
		this.audioEngine = audioEngine;
		this.muteManager = muteManager;
		this.configManager = configManager;
		this.icons = chatIcons;

	}

	@Subscribe
	private void onMenuOpened(MenuOpened event) {

		if (!speechManager.isAlive()) return;
		final MenuEntry[] entries = event.getMenuEntries();

		drawEntityMenu(entries);
		drawMuteMenus(entries);
		drawVoiceMenus(entries, 1);
		drawVolumeMenus(entries, 1);
	}

	private void drawVolumeMenus(MenuEntry[] entries, int menuIndex) {
		List<Integer> interfaces = List.of(InterfaceID.CHATBOX);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			final String hook = Text.removeFormattingTags(entry.getOption());
			final boolean canHook = interfaces.contains(groupId) && hook.contains("Set chat mode: Public");

			if (!canHook) continue;

			String hintMemory = configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_INGAME_VOLUME_MENU_TO_MUTE);
			boolean hasHinted = Boolean.parseBoolean(hintMemory) || audioEngine.isMuted();

			String hint = hasHinted ? "" : "<col=aaaaaa>click to mute</col> ";
			ChatIcon icon = audioEngine.isMuted() ? icons.muted : icons.unmuted;
			String option = hint + (audioEngine.isMuted() ? "Unmute" : "Speech");
			String action = audioEngine.isMuted() ? "Speech" : "Volume";

			MenuEntry parent = client.createMenuEntry(menuIndex)
				.setOption(icon.get() + option)
				.setTarget(action)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					audioEngine.setMute(!audioEngine.isMuted());
					if (!hasHinted) {
						configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_INGAME_VOLUME_MENU_TO_MUTE, true);
					}
				});

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

			if (audioEngine.isMuted()) continue;

			Menu subMenu = parent.createSubMenu();

			final int currentVolumeIndex = config.masterVolume() / 10;

			for (int colorIndex = colorScheme.length - 1; colorIndex > -1; colorIndex--) {
				final int volume = colorIndex * 10;

				final String colorTag = "<col=" + colorScheme[colorIndex] + ">";
				final String meter = colorIndex == currentVolumeIndex ? "> " : "- ";

				subMenu.createMenuEntry(0)
					.setTarget(meter + colorTag + volume + "%")
					.setType(MenuAction.RUNELITE)
					.onClick((e) -> audioEngine.setMasterVolume(volume));
			}
		}
	}

	private void drawVoiceMenus(MenuEntry[] entries, int menuIndex) {
		List<Integer> interfaces = List.of(InterfaceID.CHATBOX);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (interfaces.contains(groupId)) {
				List<VoiceConfigMenu> results = VoiceConfigMenu.find(Text.removeFormattingTags(entry.getOption()));

				for (VoiceConfigMenu result : results) {
					if (result.children == null) {
						_drawVoiceMenu(null, result, 1, MenuAction.RUNELITE);
					}
					else {
						MenuEntry parent = _drawVoiceMenu(null, result, 1, MenuAction.RUNELITE);

						Menu subMenu = parent.createSubMenu();

						for (VoiceConfigMenu child : result.children) {
							Preconditions.checkNotNull(child);

							_drawVoiceMenu(subMenu, child, 1, MenuAction.RUNELITE);
						}
					}
				}
			}
		}
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
						_drawMuteMenu(null, result, 1, MenuAction.RUNELITE);
					}
					else {
						MenuEntry parent = _drawMuteMenu(null, result, 1, MenuAction.RUNELITE);

						Menu subMenu = parent.createSubMenu();

						for (TabConfigMenu child : result.children) {
							Preconditions.checkNotNull(child);

							_drawMuteMenu(subMenu, child, 1, MenuAction.RUNELITE);
						}
					}
				}
			}
		}
	}

	private MenuEntry _drawVoiceMenu(Menu subMenu, VoiceConfigMenu tab, int index, MenuAction menuType) {

		String iconTag = drawIconTag(tab.icon, true);

		String action = tab.actionName;

		MenuEntry currentMenuEntry;

		if (subMenu == null) {
			currentMenuEntry = client.createMenuEntry(index);
			//			return client.createMenuEntry(index)
			//				.setOption(iconTag + action)
			//				.setTarget(tab.targetName)
			//				.setType(menuType)
			//				.onClick(e -> {
			//					if (tab.entityID == null) return;
			//					if (tab.configKey == null) return;
			//
			//					String value = voiceManager.get(tab.entityID).map(VoiceID::toString).orElse("");
			//					voiceConfigChatboxTextInputProvider.get()
			//						.configKey(tab.configKey)
			//						.entityID(tab.entityID)
			//						.value(value)
			//						.build();
			//				});
		} else {
			currentMenuEntry = subMenu.createMenuEntry(index);
			//			return subMenu.createMenuEntry(index)
			//				.setOption(iconTag + action)
			//				.setTarget(tab.targetName)
			//				.setType(menuType)
			//				.onClick(e -> {
			//					if (tab.entityID == null) return;
			//					if (tab.configKey == null) return;
			//
			//					String value = voiceManager.get(tab.entityID).map(VoiceID::toString).orElse("");
			//					voiceConfigChatboxTextInputProvider.get()
			//						.configKey(tab.configKey)
			//						.entityID(tab.entityID)
			//						.value(value)
			//						.build();
			//				});
		}

		return currentMenuEntry
			.setOption(iconTag + action)
			.setTarget(tab.targetName)
			.setType(menuType)
			.onClick(e -> {
				if (tab.entityID == null) return;
				if (tab.configKey == null) return;

				String value = voiceManager.get(tab.entityID).map(VoiceID::toString).orElse("");
				voiceConfigChatboxTextInputProvider.get()
					.configKey(tab.configKey)
					.entityID(tab.entityID)
					.value(value)
					.build();
			});
	}

	private MenuEntry _drawMuteMenu(Menu subMenu, TabConfigMenu tab, int index, MenuAction menuType) {

		final boolean state = Arrays.stream(tab.configKeys)
			.anyMatch(key -> configManager.getConfiguration(CONFIG_GROUP, key, boolean.class));

		String iconTag = drawIconTag(tab.icon, state);

		String action = state ? tab.falseAction : tab.trueAction;

		MenuEntry currentMenuEntry;
		if (subMenu == null) {
			currentMenuEntry = client.createMenuEntry(index);
		} else {
			currentMenuEntry = subMenu.createMenuEntry(index);;
		}

		return currentMenuEntry
			.setOption(iconTag + action)
			.setTarget(tab.name)
			.setType(menuType)
			.onClick(e -> Arrays.stream(tab.configKeys)
				.forEach(key -> configManager.setConfiguration(CONFIG_GROUP, key, !state))
			);
	}

	private String drawIconTag(MenuIconSet set, boolean state) {
		switch (set) {
			case MUTE_UNMUTE_REVERSED:
				return !state ? icons.unmuted.get() : icons.muted.get();
			case MUTE_UNMUTE:
				return state ? icons.unmuted.get() : icons.muted.get();
			case CHECKMARK_TOGGLE:
				return state ? icons.checkboxChecked.get() : icons.checkbox.get();
			case CHECKMARK_TOGGLE_REVERSED:
				return !state ? icons.checkboxChecked.get() : icons.checkbox.get();
			case LOGO:
				return icons.logo.get();
			case LOGO_TOGGLE:
				return state ? icons.logo.get() : icons.logo_disabled.get();
			case LOGO_TOGGLE_REVERSED:
				return !state ? icons.logo.get() : icons.logo_disabled.get();
			case NO_ICON:
			default:
				return "";
		}
	}


	private void drawEntityMenu(MenuEntry[] entries) {
		if (config.holdShiftRightClickMenu() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		List<Integer> interfaces = List.of(
			InterfaceID.FRIEND_LIST,
			InterfaceID.FRIENDS_CHAT,
			InterfaceID.CHATBOX,
			InterfaceID.PRIVATE_CHAT,
			InterfaceID.GROUP_IRON
		);

		final List<String> detectableOptions = List.of("Message", "Add friend", "Remove friend");

		for (int index = entries.length - 1; index >= 0; index--) {
			final MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			final List<MenuAction> triggerEntries = List.of(
				MenuAction.PLAYER_EIGHTH_OPTION,
				MenuAction.EXAMINE_NPC
			);

			if (triggerEntries.contains(entry.getType())) {
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

		boolean isMuted = muteManager.isMuted(entityID);
		boolean isListened = muteManager.isListened(entityID);
		boolean isAllowed = muteManager.isAllowed(entityID);

		String statusColorTag = isAllowed ? "<col=78B159>" : "<col=888888>"; // "<col=DD2E44>"
		String status = isAllowed ? icons.unmuted.get() : icons.muted.get();


		if (!voiceManager.get(entityID).map(voiceManager::speakable).orElse(false)) {
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


		final MenuEntry parent;
		{
			boolean hasHinted = Boolean.parseBoolean(
				configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_INGAME_ENTITY_MENU_TO_MUTE));

			String hint = hasHinted ? "" : "<col=aaaaaa>click to mute</col> ";

			parent = client.createMenuEntry(index + 1)
				.setOption(status + hint + "Voice")
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					if (!hasHinted) {
						configManager.setConfiguration(
							CONFIG_GROUP,
							ConfigKeys.Hints.HINTED_INGAME_ENTITY_MENU_TO_MUTE,
							true);
					}

					if (!isMuted) {
						muteManager.mute(entityID);
						speechManager.silence(name -> name.equals(String.valueOf(entityID.hashCode())));
					}
					else {
						muteManager.unmute(entityID);
					}
				});
		}
		Menu subMenu = parent.createSubMenu();
		{

			boolean hasHinted = Boolean.parseBoolean(
				configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_INGAME_ENTITY_MENU_SET_VOICE));

			String hint = hasHinted ? "" : "<col=aaaaaa>click to set voice</col> ";

			MenuEntry configVoiceEntry = subMenu.createMenuEntry(1)
				.setOption(hint + "Change Voice")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					if (!hasHinted) {
						configManager.setConfiguration(
							CONFIG_GROUP,
							ConfigKeys.Hints.HINTED_INGAME_ENTITY_MENU_SET_VOICE,
							true);
					}

					voiceConfigChatboxTextInputProvider.get()
						.entityID(entityID)
						.value(voiceID.toVoiceIDString())
						.build();
				});
		}
		if (muteManager.isListenMode()) {
			MenuEntry stopListenEntry = subMenu.createMenuEntry(1)
				.setOption("Stop Listen Mode")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.setListenMode(false);
					muteManager.clearListens();
				});
		}
		else {
			if (!isMuted) {
				MenuEntry muteEntry = subMenu.createMenuEntry(1)
					.setOption("Mute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						muteManager.mute(entityID);
						speechManager.silence(name -> name.equals(String.valueOf(entityID.hashCode())));
					});

			}
			else {
				MenuEntry unmuteEntry = subMenu.createMenuEntry(1)
					.setOption("Unmute")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> muteManager.unmute(entityID));
			}
		}

		if (isListened) {
			MenuEntry unlistenEntry = subMenu.createMenuEntry(0)
				.setOption("Unlisten")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {
					muteManager.unlisten(entityID);
					speechManager.silenceAll();
				});
		}
		else {
			MenuEntry listenEntry = subMenu.createMenuEntry(0)
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
	MUTE_UNMUTE_REVERSED,
	MUTE_UNMUTE,
	LOGO,
	LOGO_TOGGLE,
	LOGO_TOGGLE_REVERSED,
	CHECKMARK_TOGGLE,
	CHECKMARK_TOGGLE_REVERSED
}

@AllArgsConstructor
enum VoiceConfigMenu {
	YOUR_VOICE(
		ConfigKeys.PERSONAL_VOICE,
		EntityID.LOCAL_PLAYER,
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
		CONFIG_STRING,
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

final class MenuStrings {

	static final String MUTE_STRING = "<col=ffff00>Mute</col>";
	static final String SPEAK_STRING = "<col=ffff00>Speak</col>";
	static final String UNMUTE_STRING = "<col=ffff00>Unmute</col>";
	static final String CONFIG_STRING = "<col=ffff00>Config</col>";

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

	_GIM_MESSAGES(
		new String[] {/*TODO*/},
		"Group Notification",
		null,
		new String[] {"Group: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_GIM_CHAT(
		new String[] {ConfigKeys.GIM_CHAT},
		"Group",
		null,
		new String[] {"Group: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_TRADE(
		new String[] {ConfigKeys.REQUESTS},
		"Trade Request",
		null,
		new String[] {"Trade: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	PARENT_CLAN_NOTIFICATION(
		new String[] {/*TODO*/},
		"Clan Notification",
		null,
		new String[] {"Clan: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	PARENT_CLAN(
		new String[] {ConfigKeys.CLAN_CHAT},
		"Clan",
		null,
		new String[] {"Clan: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_CHANNEL_NOTIFICATION(
		new String[] {/*TODO*/},
		"Channel Notification",
		null,
		new String[] {"Channel: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_CHANNEL(
		new String[] {ConfigKeys.CLAN_GUEST_CHAT},
		"Channel",
		null,
		new String[] {"Channel: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_PRIVATE_OUT(
		new String[] {ConfigKeys.PRIVATE_OUT_CHAT},
		"Private Sent",
		null,
		new String[] {"Private: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_PRIVATE_IN(
		new String[] {ConfigKeys.PRIVATE_CHAT},
		"Private Received",
		null,
		new String[] {"Private: Show all"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),


	//	PARENT_PRIVATE(
	//			new String[] {ConfigKeys.PRIVATE_CHAT, ConfigKeys.PRIVATE_OUT_CHAT},
	//			"Private",
	//			new TabConfigMenu[] {_PRIVATE_IN, _PRIVATE_OUT},
	//			new String[] {"Private: Show all"},
	//			CONFIG_STRING, CONFIG_STRING,
	//			MenuIconSet.LOGO_TOGGLE
	//	),

	_DIALOGUE(
		new String[] {ConfigKeys.DIALOG},
		"Dialogue",
		null,
		new String[] {"Game: Filter"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_NPC_OVERHEAD(
		new String[] {ConfigKeys.NPC_OVERHEAD},
		"NPC Overhead",
		null,
		new String[] {"Game: Filter"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_EXAMINE_MESSAGE(
		new String[] {ConfigKeys.EXAMINE_CHAT},
		"Examine",
		null,
		new String[] {"Game: Filter"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_GAME_MESSAGE(
		new String[] {ConfigKeys.SYSTEM_MESSAGES},
		"Game Notification",
		null,
		new String[] {"Game: Filter"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	//	PARENT_GAME(
	//			new String[] {ConfigKeys.EXAMINE_CHAT, ConfigKeys.SYSTEM_MESSAGES, ConfigKeys.NPC_OVERHEAD, ConfigKeys.DIALOG},
	//			"Game",
	//			new TabConfigMenu[] {_GAME_MESSAGE, _EXAMINE_MESSAGE, _NPC_OVERHEAD, _DIALOGUE},
	//			new String[] {"Game: Filter"},
	//			CONFIG_STRING, CONFIG_STRING,
	//			MenuIconSet.LOGO_TOGGLE
	//	),

	_PUBLIC(
		new String[] {ConfigKeys.PUBLIC_CHAT},
		"Public",
		null,
		new String[] {"Public: Show autochat"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_MUTE_NPC(
		new String[] {ConfigKeys.NPC_OVERHEAD},
		"NPCs",
		null,
		new String[] {"Set chat mode: Public"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE
	),

	_MUTE_NON_FRIENDS(
		new String[] {ConfigKeys.FRIENDS_ONLY_MODE},
		"Non-Friends",
		null,
		new String[] {"Set chat mode: Public"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE_REVERSED
	),

	_MUTE_YOURSELF(
		new String[] {ConfigKeys.MUTE_SELF},
		"Yourself",
		null,
		new String[] {"Set chat mode: Public"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE_REVERSED
	),

	_MUTE_OTHERS_PLAYERS(
		new String[] {ConfigKeys.MUTE_OTHER_PLAYERS},
		"Other Players",
		null,
		new String[] {"Set chat mode: Public"},
		SPEAK_STRING, SPEAK_STRING,
		MenuIconSet.CHECKMARK_TOGGLE_REVERSED
	),


	//	_MUTE_GRAND_EXCHANGE(
	//			new String[] {ConfigKeys.MUTE_GRAND_EXCHANGE},
	//			"In Grand Exchange",
	//			null,
	//			null,
	//			UNMUTE_STRING, MUTE_STRING,
	//			MenuIconSet.LOGO_TOGGLE_REVERSED
	//	),
	//
	//
	//	PARENT_ALL(
	//			new String[] {},
	//			"Speakers",
	//			null,
	//			new String[] {"Set chat mode: Public"},
	//			CONFIG_STRING, CONFIG_STRING,
	//			MenuIconSet.LOGO
	//	),

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
	@NonNull
	final MenuIconSet icon;

	static List<TabConfigMenu> find(String option) {
		return Arrays.stream(TabConfigMenu.values())
			.filter(tab -> tab.optionHook != null)
			.filter(tab -> Arrays.stream(tab.optionHook).filter(Objects::nonNull).anyMatch(option::startsWith))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
