package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.enums.SpeechType;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.tts.MuteManager;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.VoiceSource;
import dev.phyce.naturalspeech.ui.game.VoicePickerChatbox;
import dev.phyce.naturalspeech.utils.TextUtil;
import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
public class MenuEventHandler {

	private final Client client;
	private final ChatIconManager chatIconManager;
	private final NaturalSpeechConfig config;

	private final TextToSpeech textToSpeech;
	private final Provider<VoicePickerChatbox> voicePickerChatboxProvider;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;

	private final int muteIconId;
	private final int unmuteIconId;


	private String getIconImgTag(int iconId) {
		int imgId = chatIconManager.chatIconIndex(iconId);
		return "<img=" + imgId + ">";
	}

	@Inject
	public MenuEventHandler(Client client, ChatIconManager chatIconManager, NaturalSpeechConfig config,
							TextToSpeech textToSpeech,
							Provider<VoicePickerChatbox> voicePickerChatboxProvider,
							VoiceManager voiceManager, MuteManager muteManager) {
		this.client = client;
		this.chatIconManager = chatIconManager;
		this.config = config;
		this.textToSpeech = textToSpeech;
		this.voicePickerChatboxProvider = voicePickerChatboxProvider;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;

		muteIconId =
			chatIconManager.registerChatIcon(ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "mute.png"));
		unmuteIconId =
			chatIconManager.registerChatIcon(ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "unmute.png"));

	}

	@Subscribe
	private void onMenuOpened(MenuOpened event) {
		if (config.holdShiftRightClickMenu() && !client.isKeyPressed(KeyCode.KC_SHIFT)) return;

		if (!textToSpeech.isAnyEngineRunning()) return;
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

	private VoiceSource displaySource(SpeechType speechType) {
		VoiceSource source = voiceManager.getSource(speechType);
		if (textToSpeech.isSourceRunning(source)) return source;
		return textToSpeech.isEngineRunning(SpeechEngine.PIPER) ? VoiceSource.PIPER : VoiceSource.ELEVENLABS;
	}

	private String voiceName(VoiceID voiceID) {
		String name = textToSpeech.getVoiceName(voiceID);
		return name != null ? name : voiceID.toVoiceIDString();
	}

	private void assignRandomVoice(@CheckForNull NPC npc, String standardActorName, Gender gender, VoiceSource source) {
		String genderName = gender == Gender.MALE ? "male" : "female";
		VoiceID voiceID = voiceManager.randomVoice(gender, source);
		if (voiceID == null) {
			feedback("No " + genderName + " voices available");
			return;
		}
		voiceManager.applyConfiguredVoice(npc, standardActorName, voiceID);
		feedback(standardActorName + " set to " + voiceName(voiceID));
	}

	private void feedback(String message) {
		client.addChatMessage(ChatMessageType.CONSOLE, "", "Natural Speech: " + message, null);
	}

	/**
	 * $1 name, $2 level text
	 */

	public void drawOptions(MenuEntry entry, int index) {
		Actor actor = entry.getActor();

		// if there are no targets for this menu entry, it should be a client ui menu entry.
		String standardActorName = actor != null
			// example: Dawncore (level-90)
			? Text.standardize(Objects.requireNonNull(actor.getName()))
			// example: <img=123><col=ffffff>Dawncore</col>
			: Text.standardize(Text.removeTags(entry.getTarget()));
		String targetName = TextUtil.removeLevelFromTargetName(entry.getTarget());

		NPC npc;
		boolean isUnmuted;
		boolean isListened;
		boolean isAllowed;
		if (actor instanceof NPC) {
			npc = Objects.requireNonNull(entry.getNpc());

			isUnmuted = muteManager.isNpcUnmuted(npc);
			isListened = muteManager.isNpcListened(npc);
			isAllowed = muteManager.isNpcAllowed(npc);
		}
		else if (actor instanceof Player) {
			npc = null;

			isUnmuted = muteManager.isUsernameUnmuted(standardActorName);
			isListened = muteManager.isUsernameListened(standardActorName);
			isAllowed = muteManager.isUsernameAllowed(standardActorName);
		}
		else {
			npc = null;
			isUnmuted = muteManager.isUsernameUnmuted(standardActorName);
			isListened = muteManager.isUsernameListened(standardActorName);
			isAllowed = muteManager.isUsernameAllowed(standardActorName);
		}

		String statusColorTag = isAllowed ? "<col=78B159>" : "<col=DD2E44>";
		String status = isAllowed ? getIconImgTag(unmuteIconId) : getIconImgTag(muteIconId);

		{
			VoiceSource source = displaySource(npc != null ? SpeechType.NPC_DIALOG : SpeechType.PUBLIC_CHAT);
			VoiceID voiceID;
			if (npc != null) {
				try {
					voiceID = voiceManager.getVoiceIDFromNPCId(npc.getId(), npc.getName(), source);
				} catch (VoiceSelectionOutOfOption ignored) {
					voiceID = null;
				}

				if (!voiceManager.containsNPC(npc.getId(), Objects.requireNonNull(npc.getName()))) {
					statusColorTag = "<col=888888>";
				}
			}
			else {
				try {
					voiceID = voiceManager.getVoiceIDFromUsername(standardActorName, source);
				} catch (VoiceSelectionOutOfOption ignored) {
					voiceID = null;
					log.error("Voice Selection Out of option for {}", standardActorName);
				}

				if (!voiceManager.containsUsername(standardActorName)) {
					statusColorTag = "<col=888888>";
				}
			}


			String voiceName = voiceID != null ? voiceName(voiceID) : "voice-error";

			MenuEntry parent = client.createMenuEntry(index + 1)
				.setOption(status + " Voice")
				.setTarget(targetName)
				.setType(MenuAction.RUNELITE);

			Menu subMenu = parent.createSubMenu();

			if (isListened) {
				subMenu.createMenuEntry(0)
					.setOption("Unlisten")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						if (npc != null) {
							muteManager.unlistenNpc(npc);
						}
						else {
							muteManager.unlistenUsername(standardActorName);
						}
					});
			}
			else {
				subMenu.createMenuEntry(0)
					.setOption("Listen")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						if (npc != null) {
							muteManager.listenNpc(npc);
						}
						else {
							muteManager.listenUsername(standardActorName);
						}
						muteManager.setListenMode(true);
					});
			}

			{
				final String value = voiceID != null ? voiceID.toVoiceIDString() : "";
				subMenu.createMenuEntry(1)
					.setOption("Configure")
					.setTarget(String.format("%s(%s)</col>", statusColorTag, voiceName))
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						voicePickerChatboxProvider.get()
							.configNPC(npc) // can be null and will be ignored
							.configUsername(standardActorName)
							.value(value)
							.build();
					});
			}

			subMenu.createMenuEntry(1)
				.setOption("Random female voice")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> assignRandomVoice(npc, standardActorName, Gender.FEMALE, source));

			subMenu.createMenuEntry(1)
				.setOption("Random male voice")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> assignRandomVoice(npc, standardActorName, Gender.MALE, source));

			if (muteManager.isListenMode()) {
				subMenu.createMenuEntry(1)
					.setOption("Stop Listen Mode")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						muteManager.setListenMode(false);
						muteManager.clearListens();
					});
			}
			else {
				if (isUnmuted) {
					subMenu.createMenuEntry(1)
						.setOption("Mute")
						.setType(MenuAction.RUNELITE)
						.onClick(e -> {
							if (npc != null) {
								muteManager.muteNpc(npc);
							}
							else {
								muteManager.muteUsername(standardActorName);
							}
						});

				}
				else {
					subMenu.createMenuEntry(1)
						.setOption("Unmute")
						.setType(MenuAction.RUNELITE)
						.onClick(e -> {
							if (npc != null) {
								muteManager.unmuteNpc(npc);
							}
							else {
								muteManager.unmuteUsername(standardActorName);
							}
						});
				}
			}
		}
	}
}
