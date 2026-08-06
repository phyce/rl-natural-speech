package dev.phyce.naturalspeech.ui.game;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.tts.VoiceID;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Value;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.MouseWheelListener;

/**
 * A scrollable, searchable voice list in the chatbox, in the spirit of RuneLite's item search.
 * <p>
 * Scrolling matters as much as searching here: nobody remembers voice names, so the list has to be
 * browsable, not just filterable. The mouse wheel, the arrow keys and page up/down all move a window
 * over the results; typing narrows them.
 * <p>
 * {@code ChatboxPanelManager} registers this as a mouse wheel listener automatically because the
 * class implements {@link MouseWheelListener}.
 */
public class VoiceSearchChatbox extends ChatboxTextInput implements MouseWheelListener {

	private static final int FONT_SIZE = 16;
	private static final int LINE_HEIGHT = 15;
	private static final int PADDING = 6;

	private final ChatboxPanelManager chatboxPanelManager;

	private final List<Choice> choices = new ArrayList<>();
	private final List<Choice> results = new ArrayList<>();

	private Consumer<VoiceID> onSelected;
	private Runnable onBack;

	private int scrollOffset = 0;
	/** How many rows the last render actually fit, so scrolling can clamp to something real. */
	private int visibleRows = 1;

	@Inject
	protected VoiceSearchChatbox(ChatboxPanelManager chatboxPanelManager, ClientThread clientThread) {
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;

		lines(1);
		prompt("Search voices");

		onChanged(search -> clientThread.invokeLater(() -> {
			filter();
			update();
		}));
	}

	public VoiceSearchChatbox voices(List<Choice> voices) {
		choices.clear();
		choices.addAll(voices);
		filter();
		return this;
	}

	public VoiceSearchChatbox title(String title) {
		prompt(title);
		return this;
	}

	public VoiceSearchChatbox onSelected(Consumer<VoiceID> onSelected) {
		this.onSelected = onSelected;
		return this;
	}

	/** Runs when the panel is closed without a pick, so the caller can reopen its own menu. */
	public VoiceSearchChatbox onBack(Runnable onBack) {
		this.onBack = onBack;
		return this;
	}

	private void filter() {
		results.clear();
		scrollOffset = 0;

		String search = getValue() == null ? "" : getValue().toLowerCase().trim();

		for (Choice choice : choices) {
			if (search.isEmpty() || choice.getSearchText().contains(search)) {
				results.add(choice);
			}
		}
	}

	private int maxScroll() {
		return Math.max(0, results.size() - visibleRows);
	}

	private void scrollBy(int rows) {
		int next = Math.max(0, Math.min(scrollOffset + rows, maxScroll()));

		if (next != scrollOffset) {
			scrollOffset = next;
			clientThread.invokeLater(this::update);
		}
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
		if (!chatboxPanelManager.shouldTakeInput()) return event;
		if (results.size() <= visibleRows) return event;

		scrollBy(event.getWheelRotation());
		event.consume();
		return event;
	}

	@Override
	public void keyPressed(KeyEvent event) {
		if (!chatboxPanelManager.shouldTakeInput()) return;

		switch (event.getKeyCode()) {
			// The base class maps these to cursor movement, which is meaningless on a one line field
			case KeyEvent.VK_UP:
				event.consume();
				scrollBy(-1);
				return;
			case KeyEvent.VK_DOWN:
				event.consume();
				scrollBy(1);
				return;
			case KeyEvent.VK_PAGE_UP:
				event.consume();
				scrollBy(-visibleRows);
				return;
			case KeyEvent.VK_PAGE_DOWN:
				event.consume();
				scrollBy(visibleRows);
				return;
			default:
				super.keyPressed(event);
		}
	}

	@Override
	protected void update() {
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();

		Widget promptWidget = container.createChild(-1, WidgetType.TEXT);
		promptWidget.setText(getPrompt());
		promptWidget.setTextColor(0x800000);
		promptWidget.setFontId(getFontID());
		promptWidget.setOriginalX(0);
		promptWidget.setOriginalY(5);
		promptWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		promptWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		promptWidget.setOriginalHeight(FONT_SIZE);
		promptWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
		promptWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
		promptWidget.setWidthMode(WidgetSizeMode.MINUS);
		promptWidget.revalidate();

		buildEdit(0, 5 + FONT_SIZE, container.getWidth(), FONT_SIZE);

		Widget separator = container.createChild(-1, WidgetType.LINE);
		separator.setOriginalX(0);
		separator.setOriginalY(8 + (FONT_SIZE * 2));
		separator.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		separator.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		separator.setOriginalHeight(0);
		separator.setOriginalWidth(16);
		separator.setWidthMode(WidgetSizeMode.MINUS);
		separator.setTextColor(0x666666);
		separator.revalidate();

		int top = 12 + (FONT_SIZE * 2) + PADDING;
		int totalRows = Math.max(1, (container.getHeight() - top - PADDING) / LINE_HEIGHT);

		if (results.isEmpty()) {
			visibleRows = totalRows;
			caption(container, top, choices.isEmpty() ? "No voices available" : "No voices match that search");
			return;
		}

		// Give up a row to the counter only when there is actually something off screen
		boolean scrollable = results.size() > totalRows;
		visibleRows = scrollable ? Math.max(1, totalRows - 1) : totalRows;

		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));

		int from = scrollOffset;
		int to = Math.min(from + visibleRows, results.size());
		int y = top;

		for (int i = from; i < to; i++) {
			final Choice choice = results.get(i);

			Widget row = container.createChild(-1, WidgetType.TEXT);
			row.setText(choice.getLabel());
			row.setFontId(getFontID());
			row.setTextColor(0);
			row.setOriginalX(PADDING);
			row.setOriginalY(y);
			row.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			row.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			row.setOriginalHeight(LINE_HEIGHT);
			row.setOriginalWidth(PADDING * 2);
			row.setWidthMode(WidgetSizeMode.MINUS);
			row.setXTextAlignment(WidgetTextAlignment.LEFT);
			row.setYTextAlignment(WidgetTextAlignment.CENTER);
			row.setAction(0, "Select");
			row.setHasListener(true);
			row.setOnOpListener((JavaScriptCallback) ev -> select(choice));
			row.setOnMouseOverListener((JavaScriptCallback) ev -> row.setTextColor(0xFFFFFF));
			row.setOnMouseLeaveListener((JavaScriptCallback) ev -> row.setTextColor(0));
			row.revalidate();

			y += LINE_HEIGHT;
		}

		if (scrollable) {
			caption(container, y, String.format("%d-%d of %d  -  scroll for more", from + 1, to, results.size()));
		}
	}

	private void caption(Widget container, int y, String text) {
		Widget caption = container.createChild(-1, WidgetType.TEXT);
		caption.setText(text);
		caption.setFontId(getFontID());
		caption.setTextColor(0x666666);
		caption.setOriginalX(PADDING);
		caption.setOriginalY(y);
		caption.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		caption.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		caption.setOriginalHeight(LINE_HEIGHT);
		caption.setOriginalWidth(PADDING * 2);
		caption.setWidthMode(WidgetSizeMode.MINUS);
		caption.setXTextAlignment(WidgetTextAlignment.LEFT);
		caption.setYTextAlignment(WidgetTextAlignment.CENTER);
		caption.revalidate();
	}

	private void select(Choice choice) {
		// Taking the pick means this is no longer a cancel, so the back handler must not fire
		onBack = null;
		chatboxPanelManager.close();

		if (onSelected != null) onSelected.accept(choice.getVoiceID());
	}

	@Override
	protected void close() {
		Runnable back = onBack;
		onBack = null;

		super.close();

		if (back != null) back.run();
	}

	/** One selectable voice: what the player reads, what they can search, and what it resolves to. */
	@Value
	public static class Choice {
		String label;
		String searchText;
		VoiceID voiceID;

		public static Choice of(String label, VoiceID voiceID) {
			return new Choice(label, (label + " " + voiceID.toVoiceIDString()).toLowerCase(), voiceID);
		}
	}
}
