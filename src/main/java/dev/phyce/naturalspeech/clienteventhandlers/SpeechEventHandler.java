package dev.phyce.naturalspeech.clienteventhandlers;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.ChatHelper;
import dev.phyce.naturalspeech.utils.ClientHelper;
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
	private final ClientHelper clientHelper;
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
		ClientHelper clientHelper,
		SpeechManager speechManager,
		NaturalSpeechConfig config,
		VolumeManager volumeManager,
		VoiceManager voiceManager,
		MuteManager muteManager,
		ClientThread clientThread,
		ChatHelper chatHelper
	) {
		this.client = client;
		this.clientHelper = clientHelper;
		this.speechManager = speechManager;
		this.config = config;
		this.volumeManager = volumeManager;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;

		this.clientThread = clientThread;
		this.chatHelper = chatHelper;
	}

	@Subscribe(priority=-100)
	@VisibleForTesting
	void onChatMessage(ChatMessage message) throws ModelLocalUnavailableException {
		if (!speechManager.isStarted()) return;

		if (chatHelper.isMuted(message)) return;

		log.trace("Speaking Chat Message: {}", message);

		EntityID entityID = chatHelper.getEntityID(message);
		VoiceID voiceId = voiceManager.resolve(entityID);

		ChatHelper.ChatType chatType = chatHelper.getChatType(message);
		Supplier<Float> volume = volumeManager.chat(chatType, entityID);

		String lineName = String.valueOf(entityID.hashCode());

		String text = chatHelper.standardizeChatMessageText(message);

		if (deduplicate(message)) {
			return;
		}

		speechManager.speak(voiceId, text, volume, lineName);
	}

	@Subscribe
	@VisibleForTesting
	void onWidgetLoaded(WidgetLoaded event) {
		if (!config.dialogEnabled()) return;
		if (!speechManager.isStarted()) return;

		if (event.getGroupId() == InterfaceID.DIALOG_PLAYER) {
			_speakDialogPlayer();
		}
		else if (event.getGroupId() == InterfaceID.DIALOG_NPC) {
			_speakDialogNPC();
		}
	}

	/*
	Player chat is handled by onChatMessage
	This event is exclusively used for NPC overhead text
	 */
	@Subscribe
	@VisibleForTesting
	void onOverheadTextChanged(OverheadTextChanged event) {
		if (!(event.getActor() instanceof NPC)) {return;}

		if (!speechManager.isStarted()) return;

		if (!config.npcOverheadEnabled()) return;

		NPC npc = (NPC) event.getActor();
		EntityID entityID = EntityID.npc(npc);

		if (!muteManager.isAllowed(entityID)) return;

		String lineName = entityID.toString();
		Supplier<Float> volume = volumeManager.npc(npc);

		VoiceID voiceID;
		voiceID = voiceManager.resolve(entityID);

		String text = chatHelper.standardizeOverheadText(event);

		speechManager.speak(voiceID, text, volume, lineName);
	}

	private void _speakDialogNPC() {
		// InvokeAtTickEnd to wait until the text has loaded in
		clientThread.invokeAtTickEnd(() -> {
			speechManager.silence((lineName) -> lineName.equals(Names.DIALOG));

			Widget textWidget = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
			Widget headModelWidget = client.getWidget(ComponentID.DIALOG_NPC_HEAD_MODEL);

			if (textWidget == null || textWidget.getText() == null) {
				log.error("NPC dialog textWidget or textWidget.getText() is null");
				return;
			}
			if (headModelWidget == null) {
				log.error("NPC head model textWidget is null");
				return;
			}

			int npcId = clientHelper.widgetModelIdToNpcId(headModelWidget.getModelId());
			EntityID entityID = EntityID.id(npcId);
			log.trace("NPC {} dialog detected headModelWidget:{} textWidget:{} ", entityID, headModelWidget.getModelId(), textWidget.getText());


			if (!muteManager.isAllowed(entityID)) {
				log.debug("NPC Dialogue is muted. CompId:{}", entityID);
				return;
			}

			String text = chatHelper.standardizeWidgetText(textWidget, config.enableTextReplacementsForNPCDialog());
			VoiceID voiceID = voiceManager.resolve(entityID);

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
			VoiceID voiceID = voiceManager.resolve(EntityID.USER);

			speechManager.speak(voiceID, text, volumeManager.dialog(), Names.DIALOG);
		});
	}

	private boolean deduplicate(ChatMessage message) {
		ChatHelper.ChatType chatType = chatHelper.getChatType(message);
		if (chatType == ChatHelper.ChatType.System) {
			long currentTime = System.currentTimeMillis();
			if (lastDialogMessage.message.equals(message.getMessage())) {
				if ((currentTime - lastDialogMessage.timestamp) < 5000) return true;
			}
			lastDialogMessage.timestamp = currentTime;
			lastDialogMessage.message = message.getMessage();
		}
		return false;
	}

}
