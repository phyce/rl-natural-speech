package dev.phyce.naturalspeech.userinterface.ingame;

import com.google.inject.Inject;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.ChatIcons;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;

@Slf4j
public class VoiceConfigChatboxTextInput extends ChatboxTextInput {
	private static final int LINE_HEIGHT = 20;
	// private static final int CHATBOX_HEIGHT = 120;

	private final ChatboxPanelManager chatboxPanelManager;

	private EntityID entityID;

	private Consumer<String> onInvalid = null;
	private Consumer<String> onValid = null;

	private String configKey = null;


	@Inject
	public VoiceConfigChatboxTextInput(
		ChatboxPanelManager chatboxPanelManager,
		ClientThread clientThread,
		VoiceManager voiceManager,
		ConfigManager configManager,
		VoiceManager voiceManager1,
		Client client, ChatIcons icons
	) {
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;

		lines(1);
		prompt("Enter voice in voice:id format. Example: libritts:120");

		onDone(string ->
		{
			if (string == null) return;
			if (!string.isEmpty()) {
				VoiceID voiceId = VoiceID.fromIDString(string);

				if (voiceId != null && !voiceManager.isActive(voiceId)) {
					clientThread.invoke(() ->
						client.addChatMessage(
							ChatMessageType.CONSOLE,
							"",
							String.format(icons.logo.get() +
									"<col=ff7878>" +
									"Failed: Voice \"%s\"(" + icons.checkmark.get() + ")" +
									" format is valid, " +
									"but does not exist or the \"%s\" engine is disabled in settings." +
									"</col>",
								string, voiceId.modelName),
							"")
					);

					if (onInvalid != null) {
						onInvalid.accept(string);
					}
				}
				else if (voiceId != null) {
					voiceManager.set(entityID, voiceId);
					voiceManager.save();

					if (configKey != null) {
						configManager.setConfiguration(CONFIG_GROUP, configKey, string);
					}

					if (onValid != null) {
						onValid.accept(string);
					}

					clientThread.invoke(() ->
						client.addChatMessage(
							ChatMessageType.CONSOLE,
							"",
							String.format(icons.logo.get() + "<col=000000>Success: %s voice set to %s</col>",
								entityID.toShortString(), string),
							"")
					);

				}
				else {
					log.info("Attempting to set invalid voiceID with {}", string);
					clientThread.invoke(() ->
						client.addChatMessage(
							ChatMessageType.CONSOLE,
							"",
							String.format(
								icons.logo.get() +
									"<col=ff1818>Failed: Invalid voice format \"%s\"(" + icons.xmark.get() + ").<br>" +
									"<col=ff1818>Example format: \"libritts:123\"",
									string),
							"")
					);

					if (onInvalid != null) {
						onInvalid.accept(string);
					}
				}
			}
			else {
				voiceManager.unset(entityID);

				if (configKey != null) {
					configManager.unsetConfiguration(CONFIG_GROUP, configKey);
				}
				if (onValid != null) {
					onValid.accept(string);
				}
				clientThread.invoke(() ->
					client.addChatMessage(
						ChatMessageType.CONSOLE,
						"",
						String.format(icons.logo.get() + "<col=00000>Success: %s voice setting cleared.</col>",
							entityID.toShortString()),
						"")
				);
			}

		});
	}

	public VoiceConfigChatboxTextInput configKey(String key) {
		this.configKey = key;
		return this;
	}

	public VoiceConfigChatboxTextInput onValid(Consumer<String> callback) {
		this.onValid = callback;
		return this;
	}

	public VoiceConfigChatboxTextInput onInvalid(Consumer<String> callback) {
		this.onInvalid = callback;
		return this;
	}


	@Override
	protected void update() {
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();

		Widget promptWidget = container.createChild(-1, WidgetType.TEXT);
		promptWidget.setText(getPrompt());
		promptWidget.setTextColor(0x800000);
		promptWidget.setFontId(getFontID());
		promptWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		promptWidget.setOriginalX(0);
		promptWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		promptWidget.setOriginalY(5);
		promptWidget.setOriginalHeight(LINE_HEIGHT);
		promptWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
		promptWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
		promptWidget.setWidthMode(WidgetSizeMode.MINUS);
		promptWidget.revalidate();

		buildEdit(0, 5 + LINE_HEIGHT, container.getWidth(), LINE_HEIGHT);

		Widget separator = container.createChild(-1, WidgetType.LINE);
		separator.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		separator.setOriginalX(0);
		separator.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		separator.setOriginalY(4 + (LINE_HEIGHT * 2));
		separator.setOriginalHeight(0);
		separator.setOriginalWidth(16);
		separator.setWidthMode(WidgetSizeMode.MINUS);
		separator.revalidate();
	}

	public VoiceConfigChatboxTextInput entityID(EntityID entityID) {
		this.entityID = entityID;
		return this;
	}
}
