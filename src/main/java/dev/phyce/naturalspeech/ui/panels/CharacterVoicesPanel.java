package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.CharacterVoiceDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCIDVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.NPCNameVoiceConfigDatum;
import dev.phyce.naturalspeech.configs.json.uservoiceconfigs.PlayerNameVoiceConfigDatum;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

public class CharacterVoicesPanel extends PluginPanel {

	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(6, 6, 6, 6);
	private static final int ROW_GAP = 6;
	private static final Comparator<Entry> ENTRY_ORDER = Comparator.comparing(entry -> entry.getLabel().toLowerCase());
	private static final Entry SAMPLE_ENTRY = new Entry("Sample", List.of(), voiceIDs -> {}, () -> {});
	private static final Runnable NO_OP = () -> {};

	private final VoiceManager voiceManager;
	private final ClientThread clientThread;
	private final Client client;
	private final Runnable charactersListener = () -> SwingUtilities.invokeLater(this::refreshIfShowing);
	private final CharacterList playersList;
	private final CharacterList npcsList;
	private boolean refreshQueued = false;

	@Inject
	public CharacterVoicesPanel(VoiceManager voiceManager, ClientThread clientThread, Client client) {
		super(false);
		this.voiceManager = voiceManager;
		this.clientThread = clientThread;
		this.client = client;

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		playersList = new CharacterList("Add player", voiceManager::addPlayer);
		npcsList = new CharacterList("Add NPC", voiceManager::addNpc);

		JPanel display = new JPanel(new BorderLayout());
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(8, 0, 0, 0));
		MaterialTab playersTab = addTab(tabGroup, "Players", playersList);
		addTab(tabGroup, "NPCs", npcsList);
		tabGroup.select(playersTab);

		this.add(tabGroup, BorderLayout.NORTH);
		this.add(display, BorderLayout.CENTER);

		voiceManager.addCharacterListener(charactersListener);
		refreshCharacters();
	}

	public void shutdown() {
		voiceManager.removeCharacterListener(charactersListener);
		this.removeAll();
	}

	@Override
	public void onActivate() {
		super.onActivate();
		refreshCharacters();
	}

	private static MaterialTab addTab(MaterialTabGroup tabGroup, String name, JComponent content) {
		MaterialTab tab = new MaterialTab(name, tabGroup, content);
		tabGroup.addTab(tab);
		return tab;
	}

	private void refreshIfShowing() {
		if (refreshQueued || !isShowing()) return;

		refreshQueued = true;
		refreshCharacters();
	}

	private void refreshCharacters() {
		clientThread.invokeLater(() -> {
			List<Entry> players = new ArrayList<>();
			for (PlayerNameVoiceConfigDatum datum : voiceManager.listPlayerVoices()) {
				players.add(savedEntry(datum, datum.getPlayerName()));
			}
			players.sort(ENTRY_ORDER);
			players.addAll(heardEntries(voiceManager.getHeardPlayers(), (name, voiceIDs) ->
				new Entry(name, voiceIDs, saved -> voiceManager.setPlayerVoices(name, saved), null)));

			List<Entry> npcs = new ArrayList<>();
			for (NPCNameVoiceConfigDatum datum : voiceManager.listNpcNameVoices()) {
				npcs.add(savedEntry(datum, datum.getNpcName()));
			}
			for (NPCIDVoiceConfigDatum datum : voiceManager.listNpcIdVoices()) {
				npcs.add(savedEntry(datum, npcIdLabel(datum)));
			}
			npcs.sort(ENTRY_ORDER);
			npcs.addAll(heardEntries(voiceManager.getHeardNpcs(), (name, voiceIDs) ->
				new Entry(name, voiceIDs, saved -> voiceManager.setNpcVoices(name, saved), null)));

			SwingUtilities.invokeLater(() -> {
				refreshQueued = false;
				playersList.setEntries(players);
				npcsList.setEntries(npcs);
			});
		});
	}

	private Entry savedEntry(CharacterVoiceDatum datum, String label) {
		List<VoiceID> voiceIDs = datum.getVoiceIDs().stream().filter(Objects::nonNull).collect(Collectors.toList());
		return new Entry(label, voiceIDs, saved -> voiceManager.setCharacterVoices(datum, saved),
			() -> voiceManager.removeCharacter(datum));
	}

	private static List<Entry> heardEntries(Map<String, VoiceID> heard,
											BiFunction<String, List<VoiceID>, Entry> toEntry) {
		List<Entry> entries = new ArrayList<>();
		heard.forEach((name, voiceID) -> entries.add(toEntry.apply(name, List.of(voiceID))));
		Collections.reverse(entries);
		return entries;
	}

	private String npcIdLabel(NPCIDVoiceConfigDatum datum) {
		String name = datum.getNpcName();
		if (name == null && client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState()) {
			NPCComposition composition = client.getNpcDefinition(datum.getNpcId());
			name = composition == null ? null : composition.getName();
		}
		return (name == null ? "NPC" : name) + " #" + datum.getNpcId();
	}

	private JPanel buildRow(Entry entry, Runnable onEditEnd) {
		JPanel row = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		row.setBorder(new EmptyBorder(6, 6, 6, 6));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(new JLabel(entry.getLabel()), BorderLayout.CENTER);
		Runnable remove = entry.getRemove();
		if (remove != null) {
			JButton removeButton = new JButton("X");
			removeButton.setToolTipText("Remove " + entry.getLabel());
			removeButton.setMargin(new Insets(0, 4, 0, 4));
			removeButton.addActionListener(event -> clientThread.invokeLater(remove));
			header.add(removeButton, BorderLayout.EAST);
		}
		else {
			JLabel autoLabel = new JLabel("auto");
			autoLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			autoLabel.setToolTipText("Picked automatically, edit the voice to keep it");
			header.add(autoLabel, BorderLayout.EAST);
		}
		row.add(header);

		List<VoiceID> voiceIDs = entry.getVoiceIDs();
		JTextField voiceField = new JTextField(voiceIDs.isEmpty() ? "" : voiceIDs.get(0).toVoiceIDString());
		voiceField.setToolTipText("Voice ID, example: libritts:120");
		JTextField fallbackField = new JTextField(voiceIDs.stream()
			.skip(1)
			.map(VoiceID::toVoiceIDString)
			.collect(Collectors.joining(", ")));
		fallbackField.setToolTipText("Voice IDs separated by commas, used in order");

		Runnable save = () -> {
			List<VoiceID> voices = parseVoiceIDs(voiceField.getText());
			List<VoiceID> fallbacks = parseVoiceIDs(fallbackField.getText());
			boolean voiceInvalid = voices == null || voices.size() > 1;
			voiceField.setForeground(voiceInvalid ? ColorScheme.PROGRESS_ERROR_COLOR : null);
			fallbackField.setForeground(fallbacks == null ? ColorScheme.PROGRESS_ERROR_COLOR : null);
			if (voiceInvalid || fallbacks == null) return;

			List<VoiceID> combined = new ArrayList<>(voices);
			fallbacks.stream().filter(fallback -> !combined.contains(fallback)).forEach(combined::add);
			clientThread.invokeLater(() -> entry.getSave().accept(combined));
		};
		onTextChange(voiceField, text -> save.run());
		onTextChange(fallbackField, text -> save.run());
		FocusAdapter editEnd = new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent event) {
				onEditEnd.run();
			}
		};
		voiceField.addFocusListener(editEnd);
		fallbackField.addFocusListener(editEnd);
		row.add(labeledRow("Voice", voiceField));
		row.add(labeledRow("Fallbacks", fallbackField));

		return row;
	}

	@CheckForNull
	private static List<VoiceID> parseVoiceIDs(String text) {
		List<VoiceID> voiceIDs = new ArrayList<>();
		for (String part : text.split(",")) {
			if (part.isBlank()) continue;

			VoiceID voiceID = VoiceID.fromIDString(part.trim());
			if (voiceID == null) return null;
			voiceIDs.add(voiceID);
		}
		return voiceIDs;
	}

	private static JPanel labeledRow(String text, Component field) {
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setPreferredSize(new Dimension(70, 0));

		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setOpaque(false);
		panel.add(label, BorderLayout.WEST);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	private static JLabel fieldLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(2, 0, 0, 0));
		return label;
	}

	private static JLabel hintLabel(String text) {
		JLabel label = new JLabel("<html><body style='width:150px'>" + text + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
		label.setBorder(new EmptyBorder(0, 0, 4, 0));
		return label;
	}

	private static void onTextChange(JTextField field, Consumer<String> consumer) {
		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				consumer.accept(field.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				consumer.accept(field.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				consumer.accept(field.getText());
			}
		});
	}

	private final class CharacterList extends JPanel {
		private final JTextField searchField = new JTextField();
		private final JPanel rowsArea = new JPanel(new BorderLayout());
		private final JPanel rows = new JPanel(new DynamicGridLayout(0, 1, 0, ROW_GAP));
		private final JLabel pageLabel = new JLabel("", SwingConstants.CENTER);
		private final JButton previousButton = new JButton("<");
		private final JButton nextButton = new JButton(">");
		private List<Entry> entries = List.of();
		private int page = 0;
		private int pageSize = 1;
		private int rowHeight = 0;
		private boolean stale = false;

		CharacterList(String addLabel, Consumer<String> add) {
			super(new BorderLayout());

			JPanel controls = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
			controls.setBorder(BORDER_PADDING);

			controls.add(fieldLabel("Search"));
			onTextChange(searchField, text -> {
				page = 0;
				render();
			});
			controls.add(searchField);

			controls.add(fieldLabel(addLabel));
			JTextField nameField = new JTextField();
			JButton addButton = new JButton("Add");
			addButton.setMargin(new Insets(2, 6, 2, 6));
			Runnable addFromField = () -> {
				String name = nameField.getText().trim();
				if (name.isEmpty()) return;

				nameField.setText("");
				clientThread.invokeLater(() -> add.accept(name));
			};
			addButton.addActionListener(event -> addFromField.run());
			nameField.addActionListener(event -> addFromField.run());

			JPanel addRow = new JPanel(new BorderLayout(4, 0));
			addRow.setOpaque(false);
			addRow.add(nameField, BorderLayout.CENTER);
			addRow.add(addButton, BorderLayout.EAST);
			controls.add(addRow);

			rows.setOpaque(false);
			rowsArea.setBorder(new EmptyBorder(0, 6, 0, 6));
			rowsArea.add(rows, BorderLayout.NORTH);
			rowsArea.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					updatePageSize();
				}
			});

			previousButton.addActionListener(event -> {
				page--;
				render();
			});
			nextButton.addActionListener(event -> {
				page++;
				render();
			});
			JPanel pager = new JPanel(new BorderLayout());
			pager.setBorder(BORDER_PADDING);
			pager.add(previousButton, BorderLayout.WEST);
			pager.add(pageLabel, BorderLayout.CENTER);
			pager.add(nextButton, BorderLayout.EAST);

			add(controls, BorderLayout.NORTH);
			add(rowsArea, BorderLayout.CENTER);
			add(pager, BorderLayout.SOUTH);
		}

		void setEntries(List<Entry> entries) {
			this.entries = entries;
			if (isEditing()) {
				stale = true;
				return;
			}
			render();
		}

		private boolean isEditing() {
			Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			return focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, rows);
		}

		private void renderIfStale() {
			if (stale && !isEditing()) render();
		}

		private void updatePageSize() {
			if (rowHeight == 0) rowHeight = buildRow(SAMPLE_ENTRY, NO_OP).getPreferredSize().height;

			int size = Math.max(1, (rowsArea.getHeight() + ROW_GAP) / (rowHeight + ROW_GAP));
			if (size == pageSize) return;

			int firstShown = page * pageSize;
			pageSize = size;
			page = firstShown / pageSize;
			render();
		}

		private void render() {
			stale = false;
			String search = searchField.getText().trim().toLowerCase();
			List<Entry> matches = entries.stream()
				.filter(entry -> entry.getLabel().toLowerCase().contains(search))
				.collect(Collectors.toList());

			int pageCount = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
			page = Math.max(0, Math.min(page, pageCount - 1));

			rows.removeAll();
			matches.stream()
				.skip((long) page * pageSize)
				.limit(pageSize)
				.forEach(entry -> rows.add(buildRow(entry, () -> SwingUtilities.invokeLater(this::renderIfStale))));
			if (matches.isEmpty()) {
				rows.add(hintLabel(entries.isEmpty() ? "No voices yet." : "No matches."));
			}

			pageLabel.setText("Page " + (page + 1) + " of " + pageCount);
			previousButton.setEnabled(page > 0);
			nextButton.setEnabled(page < pageCount - 1);
			rows.revalidate();
			rows.repaint();
		}
	}

	@Value
	private static class Entry {
		String label;
		List<VoiceID> voiceIDs;
		Consumer<List<VoiceID>> save;
		@CheckForNull
		Runnable remove;
	}
}
