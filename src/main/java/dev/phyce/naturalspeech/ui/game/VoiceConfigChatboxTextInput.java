package dev.phyce.naturalspeech.ui.game;

import com.google.gson.Gson;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import okhttp3.OkHttpClient;

@Slf4j
public class VoiceConfigChatboxTextInput extends ChatboxTextInput {
	private static final int LINE_HEIGHT = 20;
	private static final int CHATBOX_HEIGHT = 120;
	private final ChatboxPanelManager chatboxPanelManager;
	private NPC npc;
	@SuppressWarnings("FieldCanBeLocal")
	private final VoiceManager voiceManager;
	private final Client client;
	private final ClientThread clientThread;
	private String standardActorName;

	@Inject
	public VoiceConfigChatboxTextInput(
		ChatboxPanelManager chatboxPanelManager,
		ClientThread clientThread,
		ScheduledExecutorService scheduledExecutorService,
		OkHttpClient okHttpClient, Gson gson, TextToSpeech textToSpeech, VoiceManager voiceManager,
		Client client) {
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;
		this.voiceManager = voiceManager;
		this.client = client;
		this.clientThread = clientThread;
		lines(1);
		prompt("Enter voice in voice:id format. Example: libritts:120");

		// onDone fires on the AWT thread, but applying a voice reads NPC composition and name, which
		// assert they are on the client thread. Hopping is not optional: the assertion aborts the
		// handler part-way, leaving the voice written but everything after it skipped.
		// Cast because onDone is overloaded for Consumer and Predicate, and an implicitly typed
		// lambda leaves the compiler unable to pick between them
		onDone((Consumer<String>) string -> clientThread.invokeLater(() -> apply(string)));
	}

	private void apply(String string) {
		if (string == null) return;

		{
			if (!string.isEmpty()) {
				VoiceID voiceId = VoiceID.fromIDString(string);
				if (voiceId != null) {
					log.info("{} set to {}", standardActorName, voiceId);
					voiceManager.applyConfiguredVoice(npc, standardActorName, voiceId);

					feedback(standardActorName + " set to " + voiceId + ", added to Custom Characters.");
				} else {
					log.info("Attempting to set invalid voiceID with {}", string);
					// Used to fail silently, which is indistinguishable from the plugin ignoring you
					feedback("'" + string + "' is not a valid voice id. Use model:id, for example "
						+ "libritts:120, microsoft:david or elevenlabs:21m00Tcm4TlvDq8ikWAM.");
				}
			} else {
				voiceManager.clearConfiguredVoice(npc, standardActorName);
			}
		}
	}

	/**
	 * Reports back in the chatbox. Uses CONSOLE rather than GAMEMESSAGE because the plugin mutes
	 * console messages for text-to-speech, so this shows up without being read aloud.
	 */
	private void feedback(String message) {
		client.addChatMessage(ChatMessageType.CONSOLE, "", "Natural Speech: " + message, null);
	}

	public VoiceConfigChatboxTextInput configNPC(@Nullable NPC actor) {
		this.npc = actor;
		return this;
	}

	public VoiceConfigChatboxTextInput configUsername(@NonNull String standardActorName) {
		this.standardActorName = standardActorName;
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
