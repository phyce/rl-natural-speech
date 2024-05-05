package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.utils.ChatHelper;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.exceptions.VoiceSelectionOutOfOption;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.Standardize;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SpeechEventHandler {
	private final Client client;
	private final NaturalSpeechConfig config;
	private final SpeechManager speechManager;
	private final VolumeManager volumeManager;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;
	private final ClientThread clientThread;
	private final ChatHelper chatHelper;

	private final LastDialogMessage lastDialogMessage = new LastDialogMessage();

	private static class LastDialogMessage {
		public String message = "";
		public long timestamp = 0;
	}

	@Inject
	public SpeechEventHandler(
		Client client,
		SpeechManager speechManager,
		NaturalSpeechConfig config,
		VolumeManager volumeManager,
		VoiceManager voiceManager,
		MuteManager muteManager,
		ClientThread clientThread,
		ChatHelper chatHelper
	) {
		this.client = client;
		this.speechManager = speechManager;
		this.config = config;
		this.volumeManager = volumeManager;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;

		this.clientThread = clientThread;
		this.chatHelper = chatHelper;
	}



	@Subscribe(priority=-100)
	private void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (!speechManager.isStarted()) return;

		if (chatHelper.isMuted(message)) return;

		ChatHelper.VoiceType voiceType = chatHelper.getVoiceType(message);

		if (voiceType == ChatHelper.VoiceType.Unknown) {
			log.error("ChatMessage ignored, didn't match innerVoice, otherPlayerVoice, or SystemVoice. name:{} type:{} message:{}",
				message.getName(), message.getType(), message.getMessage());
			return;
		}

		Standardize.SID sid = chatHelper.getSID(message);
		VoiceID voiceId = voiceManager.getVoice(sid);
		String lineName = sid.toString();
		Supplier<Float> volume = volumeManager.chat(voiceType, sid);

		String text = chatHelper.standardizeChatText(message);

		if (deduplicate(voiceType, text)) {
			return;
		}

		speechManager.speak(voiceId, text, volume, lineName);
	}

	@Subscribe(priority=-100)
	private void onWidgetLoaded(WidgetLoaded event) {
		if (!config.dialogEnabled()) return;
		if (!speechManager.isStarted()) return;

		if (event.getGroupId() == InterfaceID.DIALOG_PLAYER) {
			_speakDialogPlayer();
		}
		else if (event.getGroupId() == InterfaceID.DIALOG_NPC) {
			_speakDialogNPC();
		}
	}

	@Subscribe(priority=-1)
	private void onOverheadTextChanged(OverheadTextChanged event) {
		/*
		Player chat is handled by onChatMessage
		This event is exclusively used for NPC overhead text
		 */
		if (!speechManager.isStarted()) return;
		if (!(event.getActor() instanceof NPC)) {return;}
		if (!config.npcOverheadEnabled()) return;

		NPC npc = (NPC) event.getActor();
		Standardize.SID sid = new Standardize.SID(npc);

		if (!muteManager.isAllowed(sid)) return;

		String lineName = sid.toString();
		Supplier<Float> volume = volumeManager.npc(npc);

		VoiceID voiceID;
		voiceID = voiceManager.getVoice(sid);

		String text = chatHelper.standardizeOverheadText(event);

		speechManager.speak(voiceID, text, volume, lineName);
	}

	private void _speakDialogNPC() {
		// InvokeAtTickEnd to wait until the text has loaded in
		clientThread.invokeAtTickEnd(() -> {
			speechManager.silence((lineName) -> lineName.equals(Names.DIALOG));

			Widget textWidget = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
			Widget headModelWidget = client.getWidget(ComponentID.DIALOG_NPC_HEAD_MODEL);
			Widget npcNameWidget = client.getWidget(ComponentID.DIALOG_NPC_NAME);

			if (textWidget == null || textWidget.getText() == null) {
				log.error("NPC dialog textWidget or textWidget.getText() is null");
				return;
			}
			if (headModelWidget == null) {
				log.error("NPC head model textWidget is null");
				return;
			}
			if (npcNameWidget == null) {
				log.error("NPC name textWidget is null");
				return;
			}
			log.trace("NPC dialog textWidget detected:{}", textWidget.getText());

			Standardize.SID sid = new Standardize.SID(headModelWidget.getModelId());

			if (!muteManager.isAllowed(sid)) {
				log.debug("NPC Dialogue is muted. CompId:{}", sid);
				return;
			}

			boolean expand = config.useNpcCustomAbbreviations();
			String text = chatHelper.standardizeWidgetText(textWidget, expand);
			VoiceID voiceID = voiceManager.getVoice(sid);

			speechManager.speak(voiceID, text, volumeManager.dialog(), Names.DIALOG);
		});
	}

	private void _speakDialogPlayer() {
		// InvokeAtTickEnd to wait until the text has loaded in
		clientThread.invokeAtTickEnd(() -> {
			speechManager.silence((lineName) -> lineName.equals(Names.DIALOG));

			Widget textWidget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
			if (textWidget == null || textWidget.getText() == null) {
				log.error("Player dialog textWidget or textWidget.getText() is null");
				return;
			}
			log.trace("Player dialog textWidget detected:{}", textWidget.getText());

			String text = chatHelper.standardizeWidgetText(textWidget, true);
			VoiceID voiceID = voiceManager.getVoice(Standardize.LOCAL_PLAYER_SID);

			speechManager.speak(voiceID, text, volumeManager.dialog(), Names.DIALOG);
		});
	}

	private boolean deduplicate(ChatHelper.VoiceType voiceType, String text) {
		if (voiceType == ChatHelper.VoiceType.SystemVoice) {
			long currentTime = System.currentTimeMillis();
			if (lastDialogMessage.message.equals(text)) {
				if ((currentTime - lastDialogMessage.timestamp) < 5000) return true;
			}
			lastDialogMessage.timestamp = currentTime;
			lastDialogMessage.message = text;
		}
		return false;
	}

}
