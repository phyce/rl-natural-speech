package dev.phyce.naturalspeech.ui.game;

import com.google.gson.Gson;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Actor;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import okhttp3.OkHttpClient;

public class VoiceConfigChatboxTextInput extends ChatboxTextInput {
	private static final int LINE_HEIGHT = 20;
	private static final int CHATBOX_HEIGHT = 120;
	private final ChatboxPanelManager chatboxPanelManager;
	private Actor actor;
	@Inject
	private TextToSpeech textToSpeech;

	@Inject
	public VoiceConfigChatboxTextInput(
		ChatboxPanelManager chatboxPanelManager,
		ClientThread clientThread,
		ScheduledExecutorService scheduledExecutorService,
		OkHttpClient okHttpClient, Gson gson) {
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;
		lines(1);
		prompt("Enter voice in model:id format. Example: libritts:120");

		onDone(string ->
		{
			if (string != null && string.length() > 0) {
				Pattern pattern = Pattern.compile("^(.*?):(\\d+)$");
				Matcher matcher = pattern.matcher(string);
				if (matcher.find()) {
					String model = matcher.group(1);
					int id = Integer.parseInt(matcher.group(2));

					textToSpeech.setActorVoiceID(actor, model, id);
					textToSpeech.saveVoiceConfig();
				}
			}
		});
	}

	public VoiceConfigChatboxTextInput insertActor(Actor actor) {
		this.actor = actor;
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
}
