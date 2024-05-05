package dev.phyce.naturalspeech.userinterface.ingame;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.texttospeech.VoiceID;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.utils.Standardize;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;

@Slf4j
public class VoiceConfigChatboxTextInput extends ChatboxTextInput {
	private static final int LINE_HEIGHT = 20;
	private static final int CHATBOX_HEIGHT = 120;
	private final ChatboxPanelManager chatboxPanelManager;
	private Standardize.SID sid;

	@Inject
	public VoiceConfigChatboxTextInput(
		ChatboxPanelManager chatboxPanelManager,
		ClientThread clientThread,
		VoiceManager voiceManager
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
				if (voiceId != null) {
					voiceManager.setVoice(sid, voiceId);
					voiceManager.saveVoiceConfig();
				} else {
					log.info("Attempting to set invalid voiceID with {}", string);
				}
			} else {
				voiceManager.unsetVoice(sid);
			}
		});
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

	public VoiceConfigChatboxTextInput configSID(Standardize.SID sid) {
		this.sid = sid;
		return this;
	}
}
