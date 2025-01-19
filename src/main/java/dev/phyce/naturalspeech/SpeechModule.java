package dev.phyce.naturalspeech;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Provider;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.audio.VolumeManager;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.MagicNames;
import dev.phyce.naturalspeech.utils.ChatHelper;
import dev.phyce.naturalspeech.texttospeech.MuteManager;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import dev.phyce.naturalspeech.userinterface.ingame.VoiceConfigChatboxTextInput;
import dev.phyce.naturalspeech.utils.ChatIcons;
import dev.phyce.naturalspeech.utils.ClientHelper;
import dev.phyce.naturalspeech.entity.EntityID;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SpeechModule implements PluginModule {
	private final Client client;
	private final ClientHelper clientHelper;
	private final NaturalSpeechConfig config;
	private final ConfigManager configManager;
	private final SpeechManager speechManager;
	private final VolumeManager volumeManager;
	private final VoiceManager voiceManager;
	private final MuteManager muteManager;
	private final ClientThread clientThread;
	private final ChatHelper chatHelper;
	private final ChatIcons chatIcons;
	private final Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider;

	private final LastDialogMessage lastDialogMessage = new LastDialogMessage();
	private Widget allTabIcon;

	private static class LastDialogMessage {
		public String message = "";
		public long timestamp = 0;
	}

	@Nullable
	private Widget previousDialogFrame = null;

	@Inject
	public SpeechModule(
			Client client,
			ClientHelper clientHelper,
			SpeechManager speechManager,
			NaturalSpeechConfig config,
			ConfigManager configManager,
			VolumeManager volumeManager,
			VoiceManager voiceManager,
			MuteManager muteManager,
			ClientThread clientThread,
			ChatHelper chatHelper,
			ChatIcons chatIcons,
			Provider<VoiceConfigChatboxTextInput> voiceConfigChatboxTextInputProvider
	) {
		this.client = client;
		this.clientHelper = clientHelper;
		this.configManager = configManager;
		this.speechManager = speechManager;
		this.config = config;
		this.volumeManager = volumeManager;
		this.voiceManager = voiceManager;
		this.muteManager = muteManager;

		this.clientThread = clientThread;
		this.chatHelper = chatHelper;
		this.chatIcons = chatIcons;
		this.voiceConfigChatboxTextInputProvider = voiceConfigChatboxTextInputProvider;

	}

	@Subscribe
	void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState()) {
			case LOGGED_IN:
			case HOPPING:
				clientThread.invokeLater(() -> {
				});
				break;
		}
	}

	/**
	 * Used for all player chats
	 */
	@Subscribe(priority=-100)
	@VisibleForTesting
	void onChatMessage(ChatMessage message) {
		if (!speechManager.isAlive()) return;

		if (chatHelper.isMuted(message)) return;

		log.trace("Speaking Chat Message: {}", message);

		EntityID entityID = chatHelper.getEntityID(message);
		VoiceID voiceId = voiceManager.resolve(entityID);

		ChatHelper.ChatType chatType = chatHelper.getChatType(message);
		Supplier<Float> volume = volumeManager.chat(chatType, entityID);

		String lineName = String.valueOf(entityID.hashCode());

		String text = chatHelper.standardizeChatMessageText(chatType, message);

		if (deduplicate(message)) return;

		speechManager.speak(voiceId, text, volume, lineName);
	}

	/**
	 * NPC Overhead
	 */
	@Subscribe
	@VisibleForTesting
	void onOverheadTextChanged(OverheadTextChanged event) {
		if (!(event.getActor() instanceof NPC)) return;
		if (!speechManager.isAlive()) return;
		if (!config.npcOverheadEnabled()) return;
		if (chatHelper.isAreaDisabled()) return;
		if (chatHelper.isTooCrowded()) return;

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

	/**
	 * Used for dialog
	 */
	@Subscribe
	@VisibleForTesting
	void onWidgetLoaded(WidgetLoaded event) {
		if (!config.dialogEnabled()) return;
		if (!speechManager.isAlive()) return;

		switch (event.getGroupId()) {
			case InterfaceID.DIALOG_OPTION:
				speechManager.silence((lineName) -> lineName.equals(MagicNames.DIALOG));
				break;
			case InterfaceID.DIALOG_PLAYER:
				_speakDialogPlayer();
				break;
			case InterfaceID.DIALOG_NPC:
				_speakDialogNPC();
				break;
			case InterfaceID.CHATBOX:
				Widget allTabText = client.getWidget(10616838);
				if (allTabText != null) {
					allTabText.setText("All<br> ");
					allTabIcon = allTabText.getParent().createChild(-1, WidgetType.TEXT);

					String icon = config.masterMute() ? chatIcons.muted.get() : chatIcons.unmuted.get();
					allTabIcon.setText("<br>" + icon);
					allTabIcon.setFontId(FontID.PLAIN_11);
					allTabIcon.setWidthMode(1);
					allTabIcon.setXTextAlignment(1);
					allTabIcon.setYTextAlignment(1);
					allTabIcon.setOriginalWidth(allTabText.getOriginalWidth());
					allTabIcon.setOriginalHeight(allTabText.getOriginalHeight());
//					iconWidget.setOriginalWidth(22);
				}
				break;
		}
	}
	/*
	Player chat is handled by onChatMessage
	This event is exclusively used for NPC overhead text
	 */

	private void _speakDialogNPC() {
		// InvokeAtTickEnd to wait until the text has loaded in
		clientThread.invokeAtTickEnd(() -> {
			speechManager.silence((lineName) -> lineName.equals(MagicNames.DIALOG));

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
			log.trace("NPC {} dialog detected headModelWidget:{} textWidget:{} ", entityID, headModelWidget.getModelId(),
					textWidget.getText());

			buildNPCMuteButton(entityID);

			if (!muteManager.isAllowed(entityID)) {
				log.debug("NPC Dialogue is muted. Entity:{}", entityID);
				return;
			}

			String text = chatHelper.standardizeWidgetText(textWidget);
			VoiceID voiceID = voiceManager.resolve(entityID);

			speechManager.speak(voiceID, text, volumeManager.dialog(), MagicNames.DIALOG);

		});
	}

	private void _speakDialogPlayer() {

		// InvokeAtTickEnd to wait until the text has loaded in
		clientThread.invokeAtTickEnd(() -> {
			speechManager.silence((lineName) -> lineName.equals(MagicNames.DIALOG));

			Widget textWidget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
			if (textWidget == null || textWidget.getText() == null) {
				log.error("Player dialog textWidget or textWidget.getText() is null");
				return;
			}
			log.trace("Player dialog textWidget detected:{}", textWidget.getText());

			buildPlayerMuteButton();

			if (config.muteSelf()) {
				log.debug("Player Dialogue is self muted.");
				return;
			}

			String text = chatHelper.standardizeWidgetText(textWidget);
			VoiceID voiceID = voiceManager.resolve(EntityID.LOCAL_PLAYER);

			speechManager.speak(voiceID, text, volumeManager.dialog(), MagicNames.DIALOG);
		});
	}

	private void buildPlayerMuteButton() {
		final EntityID entityID = EntityID.LOCAL_PLAYER;
		Widget dialogFrame = client.getWidget(14221312);

		if (Objects.equal(dialogFrame, previousDialogFrame)) {
			return;
		}
		else {
			previousDialogFrame = dialogFrame;
		}

		Widget muteButton = dialogFrame.createChild(-1, WidgetType.TEXT);
		muteButton.setOriginalWidth(16);
		muteButton.setOriginalHeight(16);
		muteButton.setOriginalX(2);
		muteButton.setOriginalY(3);
		muteButton.revalidate();
		muteButton.setNoClickThrough(true);
		muteButton.setHasListener(true);

		String text = config.muteSelf() ? chatIcons.muted.get() : chatIcons.unmuted.get();
		if (!Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_DIALOG_BUTTON))) {
			text += "<lt>--- click to mute yourself, right-click to change voice";
		}
		muteButton.setText(text);
		muteButton.setFontId(FontID.PLAIN_11);
		muteButton.setTextColor(0x333333);
		muteButton.setAction(0, config.muteSelf() ? "Unmute" : "Mute");
		muteButton.setAction(1, "Change Voice");

		muteButton.setOnOpListener((JavaScriptCallback) s -> {
			configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_DIALOG_BUTTON, true);
			switch (s.getOp()) {
				case 1: {
					configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.MUTE_SELF, !config.muteSelf());

					boolean muted = config.muteSelf();
					if (muted) {
						speechManager.silence(line -> Objects.equal(line, MagicNames.DIALOG));
					}

					muteButton.setText(muted ? chatIcons.muted.get() : chatIcons.unmuted.get());
					muteButton.setAction(0, muted ? "Unmute" : "Mute");
					break;
				}
				case 2: {

					final Optional<VoiceID> result = VoiceID.fromIDString(config.personalVoiceID());
					voiceConfigChatboxTextInputProvider.get()
							.configKey(ConfigKeys.PERSONAL_VOICE)
							.value(result.map(VoiceID::toVoiceIDString).orElse(""))
							.build();
					break;
				}
			}
		});
	}

	private void buildNPCMuteButton(final EntityID entityID) {
		Widget dialogFrame = client.getWidget(15138816);

		if (Objects.equal(dialogFrame, previousDialogFrame)) {
			return;
		}
		else {
			previousDialogFrame = dialogFrame;
		}

		Widget muteButton = dialogFrame.createChild(-1, WidgetType.TEXT);

		{
			final boolean muted = muteManager.isMuted(entityID);
			muteButton.setOriginalWidth(16);
			muteButton.setOriginalHeight(16);
			muteButton.setOriginalX(2);
			muteButton.setOriginalY(3);
			muteButton.revalidate();

			muteButton.setNoClickThrough(true);
			muteButton.setHasListener(true);

			String text = muted ? chatIcons.muted.get() : chatIcons.unmuted.get();
			if (!Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_DIALOG_BUTTON))) {
				text += "<lt>--- click to mute this npc, right-click to change voice";
			}
			muteButton.setText(text);
			muteButton.setFontId(FontID.PLAIN_11);
			muteButton.setTextColor(0x333333);

			muteButton.setAction(0, muted ? "Unmute" : "Mute");
			muteButton.setAction(1, "Change Voice");
		}

		muteButton.setOnOpListener((JavaScriptCallback) s -> {
			configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.Hints.HINTED_DIALOG_BUTTON, true);
			switch (s.getOp()) {
				case 1: {
					if (muteManager.isMuted(entityID)) {
						muteManager.unmute(entityID);
					}
					else {
						muteManager.mute(entityID);
						speechManager.silence(line -> Objects.equal(line, MagicNames.DIALOG));
					}
					final boolean muted = muteManager.isMuted(entityID);
					muteButton.setText(muted ? chatIcons.muted.get() : chatIcons.unmuted.get());
					muteButton.setAction(0, muted ? "Unmute" : "Mute");
					break;
				}
				case 2: {
					final VoiceID voiceID = voiceManager.resolve(entityID);
					voiceConfigChatboxTextInputProvider.get()
							.entityID(entityID)
							.value(voiceID.toVoiceIDString())
							.build();
					break;
				}
			}
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
