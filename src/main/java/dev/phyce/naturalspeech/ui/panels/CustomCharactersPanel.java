package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.tts.CharacterVoices;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabs;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsCache;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeech;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;

/**
 * Per-character voices for every engine. Each row has a gender picker, an engine picker and a voice
 * id, where the id overrides the gender when set and the gender otherwise narrows the automatic pick.
 * <p>
 * Voices are written to the same store as the in-game right-click Configure option, so the two are
 * always in agreement. A character's voice only applies when the message type is set to that same
 * engine — a piper voice pinned here is not used by a message type routed to ElevenLabs.
 * <p>
 * Your own character is configured in the plugin settings, not here.
 */
@Singleton
public class CustomCharactersPanel extends PluginPanel {

	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(6, 6, 6, 6);

	private static final String GENDER_AUTO = "Auto";
	private static final String GENDER_MALE = "Male";
	private static final String GENDER_FEMALE = "Female";

	private static final String ENGINE_PIPER = "Piper";
	private static final String ENGINE_SYSTEM = "System";
	private static final String ENGINE_ELEVENLABS = "ElevenLabs";
	private static final String GENDER_LABEL = "Gender";
	private static final String ID_LABEL = "Id";

	private final CharacterVoices characters;
	private final VoiceManager voiceManager;
	private final Runnable charactersListener;
	private final JTextField nameField = new JTextField();
	private final JTextField searchField = new JTextField();
	private final JPanel listContainer = new JPanel(new DynamicGridLayout(0, 1, 0, 6));

	@Inject
	CustomCharactersPanel(CharacterVoices characters, VoiceManager voiceManager) {
		super(false);

		this.characters = characters;
		this.voiceManager = voiceManager;

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Where the actual content lives
		FixedWidthPanel contentPanel = new FixedWidthPanel();
		contentPanel.setBorder(BORDER_PADDING);
		contentPanel.setLayout(new DynamicGridLayout(0, 1, 0, 3));
		contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		contentPanel.add(sectionLabel("Characters"));
		contentPanel.add(hintLabel("<html>A character can hold one voice per engine. The one used is "
			+ "whichever engine the message type is set to. Your own voice is in the plugin settings.</html>"));

		contentPanel.add(fieldLabel("Search"));
		searchField.setToolTipText("Filter the characters below");
		onTextChange(searchField, text -> refreshList());
		contentPanel.add(searchField);

		contentPanel.add(fieldLabel("Add character"));
		JPanel addRow = new JPanel(new BorderLayout(4, 0));
		addRow.setOpaque(false);
		JButton addButton = new JButton("Add");
		addButton.setMargin(new Insets(2, 6, 2, 6));
		addButton.addActionListener(event -> addFromField());
		nameField.addActionListener(event -> addFromField());
		addRow.add(nameField, BorderLayout.CENTER);
		addRow.add(addButton, BorderLayout.EAST);
		contentPanel.add(addRow);

		listContainer.setOpaque(false);
		contentPanel.add(listContainer);

		contentPanel.add(sectionLabel("Cache"));
		JButton clearCache = new JButton("Clear ElevenLabs cache");
		clearCache.setToolTipText("Delete every cached ElevenLabs clip");
		clearCache.addActionListener(event -> clearCache());
		contentPanel.add(clearCache);

		// wrap for scrolling, fixed to NORTH so the list grows southward
		JPanel northWrapper = new FixedWidthPanel();
		northWrapper.setLayout(new BorderLayout());
		northWrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(northWrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		// Can't use Short.MAX_VALUE like the docs say because of JDK-8079640
		scrollPane.setPreferredSize(new Dimension(0x7000, 0x7000));

		this.add(scrollPane);

		// Characters can also be added from the in-game right-click Configure option, which runs on
		// the client thread — hop to the EDT before touching Swing.
		charactersListener = () -> SwingUtilities.invokeLater(this::refreshList);
		characters.addListener(charactersListener);

		refreshList();
	}

	/** Adds (if missing) and shows a character. */
	public void selectCharacter(String name) {
		SwingUtilities.invokeLater(() -> {
			characters.add(name);
			searchField.setText("");
			refreshList();
		});
	}

	public void shutdown() {
		characters.removeListener(charactersListener);
		listContainer.removeAll();
	}

	@Override
	public void onActivate() {
		super.onActivate();
		refreshList();
	}

	private void clearCache() {
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete all cached audio? Lines will be re-generated (and re-billed) next time they're spoken.",
			"Clear audio cache", JOptionPane.YES_NO_OPTION);

		if (choice == JOptionPane.YES_OPTION) {
			long removed = ElevenLabsCache.clearAll();
			JOptionPane.showMessageDialog(this, "Cleared " + removed + " cached clip(s).");
		}
	}

	private void addFromField() {
		String name = nameField.getText().trim();
		if (name.isEmpty()) return;

		characters.add(name);
		nameField.setText("");
		refreshList();
	}

	private void refreshList() {
		listContainer.removeAll();

		String filter = searchField.getText().trim().toLowerCase();
		for (String name : characters.listNames()) {
			if (filter.isEmpty() || name.toLowerCase().contains(filter)) {
				listContainer.add(buildRow(name));
			}
		}

		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel buildRow(String name) {
		JPanel row = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		row.setBorder(new EmptyBorder(6, 6, 6, 6));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(new JLabel(name), BorderLayout.CENTER);

		JButton remove = new JButton("X");
		remove.setToolTipText("Remove " + name);
		remove.setMargin(new Insets(0, 4, 0, 4));
		remove.addActionListener(event -> {
			voiceManager.clearCharacterVoices(name);
			characters.remove(name);
			refreshList();
		});
		head.add(remove, BorderLayout.EAST);
		row.add(head);

		// One voice per engine: whichever the message type asks for is the one that gets used
		row.add(labeledRow(ENGINE_PIPER, voiceField(name, SpeechEngine.PIPER)));
		row.add(labeledRow(ENGINE_SYSTEM, voiceField(name, SpeechEngine.SYSTEM)));

		// ElevenLabs carries a gender as well, so it gets a heading with both fields beneath it
		row.add(groupLabel(ENGINE_ELEVENLABS));
		row.add(labeledRow(ID_LABEL, voiceField(name, SpeechEngine.ELEVENLABS)));

		JComboBox<String> genderBox = new JComboBox<>(new String[] {GENDER_AUTO, GENDER_MALE, GENDER_FEMALE});
		genderBox.setToolTipText(
			"Narrows the automatic ElevenLabs pick to voices of this gender, when no id is set above");
		genderBox.setSelectedItem(genderToLabel(characters.getGender(name)));
		genderBox.addActionListener(
			event -> characters.setGender(name, labelToGender((String) genderBox.getSelectedItem())));
		row.add(labeledRow(GENDER_LABEL, genderBox));

		return row;
	}

	/** Heading for a group of fields that belong to one engine. */
	private static JLabel groupLabel(String caption) {
		JLabel label = new JLabel(caption);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(4, 0, 0, 0));
		return label;
	}

	/** A voice id field bound to one engine for one character, saving as it is edited. */
	private JTextField voiceField(String name, SpeechEngine engine) {
		JTextField field = new JTextField(voiceToField(voiceManager.getCharacterVoice(name, engine), engine));
		field.setToolTipText(voiceHintFor(engine));

		onTextChange(field, text -> {
			String trimmed = text.trim();

			if (trimmed.isEmpty()) {
				field.setForeground(null);
				voiceManager.setCharacterVoice(name, engine, null);
				return;
			}

			VoiceID voiceID = composeVoiceID(engine, trimmed);
			// Red rather than a popup: this fires on every keystroke, and half-typed input is normal
			field.setForeground(voiceID == null ? ColorScheme.PROGRESS_ERROR_COLOR : null);

			if (voiceID != null) voiceManager.setCharacterVoice(name, engine, voiceID);
		});

		return field;
	}

	/** A fixed-width caption beside a field, so the engines line up as a column. */
	private static JPanel labeledRow(String caption, Component field) {
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setOpaque(false);

		JLabel label = new JLabel(caption);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setPreferredSize(new Dimension(64, 0));
		panel.add(label, BorderLayout.WEST);
		panel.add(field, BorderLayout.CENTER);

		return panel;
	}

	/** What the text field means for each engine. Piper needs the model, the others do not. */
	private static String voiceHintFor(SpeechEngine engine) {
		switch (engine) {
			case SYSTEM:
				return "System voice id, example: david";
			case ELEVENLABS:
				return "ElevenLabs voice id, example: 21m00Tcm4TlvDq8ikWAM";
			default:
				// Piper has many models, so the model has to be part of the id
				return "Piper voice as model:id, example: libritts:120";
		}
	}

	/** Builds a VoiceID for one engine, or null when the input is unusable. */
	@CheckForNull
	private static VoiceID composeVoiceID(SpeechEngine engine, String text) {
		switch (engine) {
			case SYSTEM:
				return new VoiceID(
					OSValidator.IS_MAC ? NativeSpeech.MACOS_MODEL_NAME : NativeSpeech.WINDOWS_MODEL_NAME, text);

			case ELEVENLABS:
				return ElevenLabs.voiceID(text);

			default:
				VoiceID voiceID = VoiceID.fromIDString(text);
				// Reject a bare id with no model, and microsoft:/elevenlabs: typed in the piper field
				if (voiceID == null) return null;
				if (TextToSpeech.engineOfModel(voiceID.getModelName()) != SpeechEngine.PIPER) return null;

				return voiceID;
		}
	}

	/** Piper shows the full model:id; the other engines show the bare id. */
	private static String voiceToField(@CheckForNull VoiceID voiceID, SpeechEngine engine) {
		if (voiceID == null) return "";

		return engine == SpeechEngine.PIPER ? voiceID.toVoiceIDString() : voiceID.getId();
	}

	private static JLabel sectionLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setBorder(new EmptyBorder(4, 0, 2, 0));
		return label;
	}

	private static JLabel fieldLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(2, 0, 0, 0));
		return label;
	}

	private static JLabel hintLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
		label.setBorder(new EmptyBorder(0, 0, 4, 0));
		return label;
	}

	private static void onTextChange(JTextField field, Consumer<String> consumer) {
		field.getDocument().addDocumentListener(new DocumentListener() {
			private void changed() {
				consumer.accept(field.getText());
			}

			@Override
			public void insertUpdate(DocumentEvent event) {changed();}

			@Override
			public void removeUpdate(DocumentEvent event) {changed();}

			@Override
			public void changedUpdate(DocumentEvent event) {changed();}
		});
	}

	private static String genderToLabel(@CheckForNull Gender gender) {
		if (gender == Gender.MALE) return GENDER_MALE;
		if (gender == Gender.FEMALE) return GENDER_FEMALE;
		return GENDER_AUTO;
	}

	@CheckForNull
	private static Gender labelToGender(String label) {
		if (GENDER_MALE.equals(label)) return Gender.MALE;
		if (GENDER_FEMALE.equals(label)) return Gender.FEMALE;
		return null;
	}
}
