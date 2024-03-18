package dev.phyce.naturalspeech;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.helpers.CustomMenuEntry;
import dev.phyce.naturalspeech.helpers.PluginHelper;
import static dev.phyce.naturalspeech.helpers.PluginHelper.*;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.ui.game.VoiceConfigChatboxTextInput;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class MenuEventHandler {

	private final Client client;
	private final TextToSpeech textToSpeech;

	private final Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;

	private final VoiceManager voiceManager;

	@Inject
	public MenuEventHandler(Client client, TextToSpeech textToSpeech,
							Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider,
							VoiceManager voiceManager) {
		this.client = client;
		this.textToSpeech = textToSpeech;
		this.voiceConfigChatboxTextInputProvider = voiceConfigChatboxTextInputProvider;
		this.voiceManager = voiceManager;
	}

	@Subscribe
	private void onMenuOpened(MenuOpened event) {
		if (textToSpeech.activePiperProcessCount() < 1) return;
		final MenuEntry[] entries = event.getMenuEntries();

		Set<Integer> interfaces = new HashSet<>();
		interfaces.add(InterfaceID.FRIEND_LIST);
		interfaces.add(InterfaceID.FRIENDS_CHAT);
		interfaces.add(InterfaceID.CHATBOX);
		interfaces.add(InterfaceID.PRIVATE_CHAT);
		interfaces.add(InterfaceID.GROUP_IRON);

		for (int index = entries.length - 1; index >= 0; index--) {
			MenuEntry entry = entries[index];

			final int componentId = entry.getParam1();
			final int groupId = WidgetUtil.componentToInterface(componentId);

			if (entry.getType() == MenuAction.PLAYER_EIGHTH_OPTION) {drawOptions(entry, index);}
			else if (entry.getType() == MenuAction.EXAMINE_NPC) {drawOptions(entry, index);}
			else if (interfaces.contains(groupId) && entry.getOption().equals("Report")) drawOptions(entry, index);
		}
	}

	public synchronized void drawOptions(MenuEntry entry, int index) {
		String regex = "<col=[0-9a-f]+>([^<]+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(entry.getTarget());

		matcher.find();
		String username = matcher.group(1).trim();

		String status;
		if (isBeingListened(username)) {status = "<col=78B159>O";}
		else {status = "<col=DD2E44>0";}

		CustomMenuEntry muteOptions =
			new CustomMenuEntry(String.format("%s <col=ffffff>Voice <col=ffffff>(%s) <col=ffffff>>", status, username),
				index);

		if (isBeingListened(username)) {
			if (!getAllowList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Stop listening", -1, function -> {
					unlisten(username);
				}));
			}
			else {
				muteOptions.addChild(new CustomMenuEntry("Mute", -1, function -> {
					mute(username);
				}));
			}
			if (getAllowList().isEmpty() && PluginHelper.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Mute others", -1, function -> {
					listen(username);
					textToSpeech.clearOtherPlayersAudioQueue(username);
				}));
			}
		}
		else {
			if (!PluginHelper.getBlockList().isEmpty()) {
				muteOptions.addChild(new CustomMenuEntry("Unmute", -1, function -> {
					unmute(username);
				}));
			}
			else {
				muteOptions.addChild(new CustomMenuEntry("Listen", -1, function -> {
					listen(username);
				}));
			}
		}

		if (!getBlockList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear block list", -1, function -> {
				getBlockList().clear();
			}));
		}
		else if (!getAllowList().isEmpty()) {
			muteOptions.addChild(new CustomMenuEntry("Clear allow list", -1, function -> {
				getAllowList().clear();
			}));
		}

		Actor actor = entry.getActor();
		muteOptions.addChild(new CustomMenuEntry("Reset", -1, function -> {

			if(actor instanceof NPC) {
				voiceManager.resetVoiceIDForNPC((NPC) actor);
			} else {
				voiceManager.resetForUsername(Text.standardize(actor.getName()));
			}

		}));

		muteOptions.addChild(new CustomMenuEntry("Configure", -1, function -> {
			voiceConfigChatboxTextInputProvider.get()
				.setType("individual")
				.insertActor(actor)
				.build();
		}));

		//if player show Configure/Reset
		//if npc also show -all options


//		if(actor instanceof NPC) {
//			muteOptions.addChild(new CustomMenuEntry("Configure-all", -1, function -> {
//				voiceConfigChatboxTextInputProvider.get()
//					.setType("all")
//					.insertActor(actor)
//					.build();
//			}));
//
//			muteOptions.addChild(new CustomMenuEntry("Reset-all", -1, function -> {
//				voiceManager.resetVoiceIDForNPCName(actor.getName());
//			}));
//		}

		muteOptions.addTo(client);
	}
}
