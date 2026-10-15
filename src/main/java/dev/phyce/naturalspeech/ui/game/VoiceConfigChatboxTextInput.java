package dev.phyce.naturalspeech.ui.game;

import com.google.inject.Inject;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;

public class VoiceConfigChatboxTextInput extends ChatboxTextInput {
	private static final int LINE_HEIGHT = 20;
	private static final String HINT = "Pressing 'Enter' saves the voice ID. Leave it empty to clear it.";
	private final ChatboxPanelManager chatboxPanelManager;
	private Runnable onChooseVoice = () -> {};
	@CheckForNull
	private String error;

	@Inject
	public VoiceConfigChatboxTextInput(ChatboxPanelManager chatboxPanelManager, ClientThread clientThread) {
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;
		lines(1);
		onChanged(text -> error = null);
	}

	public VoiceConfigChatboxTextInput onChooseVoice(Runnable onChooseVoice) {
		this.onChooseVoice = onChooseVoice;
		return this;
	}

	public VoiceConfigChatboxTextInput onSubmit(Predicate<String> onSubmit) {
		onDone(onSubmit);
		return this;
	}

	public void error(String error) {
		this.error = error;
		clientThread.invoke(this::update);
	}

	@Override
	protected void update() {
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();

		text(container, getPrompt(), 0x800000, 5);

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

		Widget chooseVoice = text(container, "Choose a voice", 0, 8 + (LINE_HEIGHT * 2));
		chooseVoice.setAction(0, "Choose");
		chooseVoice.setHasListener(true);
		chooseVoice.setOnOpListener((JavaScriptCallback) event -> onChooseVoice.run());
		chooseVoice.setOnMouseOverListener((JavaScriptCallback) event -> chooseVoice.setTextColor(0xFFFFFF));
		chooseVoice.setOnMouseLeaveListener((JavaScriptCallback) event -> chooseVoice.setTextColor(0));

		if (error != null) {
			text(container, error, 0x800000, 8 + (LINE_HEIGHT * 3));
		}
		else {
			text(container, HINT, 0x666666, 8 + (LINE_HEIGHT * 3));
		}
	}

	private Widget text(Widget container, String text, int color, int y) {
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setText(text);
		widget.setTextColor(color);
		widget.setFontId(getFontID());
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		widget.setOriginalX(0);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		widget.setOriginalY(y);
		widget.setOriginalHeight(LINE_HEIGHT);
		widget.setXTextAlignment(WidgetTextAlignment.CENTER);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		widget.revalidate();
		return widget;
	}
}
