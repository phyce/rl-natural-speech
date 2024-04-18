package dev.phyce.naturalspeech.ui.panels;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.events.SpeechEngineStarted;
import dev.phyce.naturalspeech.events.SpeechEngineStopped;
import dev.phyce.naturalspeech.events.TextToSpeechFailedStart;
import dev.phyce.naturalspeech.events.TextToSpeechStarted;
import dev.phyce.naturalspeech.events.TextToSpeechStopped;
import dev.phyce.naturalspeech.events.piper.PiperModelStarted;
import dev.phyce.naturalspeech.events.piper.PiperModelStopped;
import dev.phyce.naturalspeech.events.piper.PiperPathChanged;
import dev.phyce.naturalspeech.events.piper.PiperRepositoryChanged;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4Engine;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4Repository;
import dev.phyce.naturalspeech.tts.wsapi5.SAPI5Alias;
import dev.phyce.naturalspeech.tts.wsapi5.SAPI5Engine;
import dev.phyce.naturalspeech.tts.wsapi5.SAPI5Process;
import dev.phyce.naturalspeech.ui.components.IconTextField;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceExplorerPanel extends EditorPanel {

	static {
		BufferedImage sectionRetractIcon =
			ImageUtil.loadImageResource(MainSettingsPanel.class, "section_icons/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
	}

	private final PiperRepository piperRepository;
	private final SAPI4Repository sapi4Repository;
	private final SAPI4Engine sapi4Engine;
	private final SAPI5Engine sapi5Engine;

	private final TextToSpeech textToSpeech;

	public static final ImageIcon SECTION_EXPAND_ICON;
	private static final ImageIcon SECTION_RETRACT_ICON;

	final ImageIcon speechTextIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "speechText.png"));

	private static final ImmutableList<String> SEARCH_HINTS = ImmutableList.of("Male", "Female");

	@Getter
	private final IconTextField speechText;
	private final IconTextField searchBar;
	private final FixedWidthPanel sectionListPanel;
	private final List<VoiceListItem> voiceListItems = new ArrayList<>();
	private final HashMultimap<String, VoiceListItem> searchToItemBiMap = HashMultimap.create();
	private final Map<String, JPanel> modelSections = new HashMap<>();
	private JPanel microsoftSegment;
	private JPanel centerStoppedWarning;
	private JPanel centerNoEngineWarning;
	private JPanel centerCopyHint;

	@Inject
	public VoiceExplorerPanel(
		PiperRepository piperRepository,
		SAPI4Repository sapi4Repository,
		SAPI4Engine sapi4Engine,
		SAPI5Engine sapi5Engine,
		TextToSpeech textToSpeech,
		PluginEventBus pluginEventBus
	) {
		this.piperRepository = piperRepository;
		this.sapi4Repository = sapi4Repository;
		this.sapi4Engine = sapi4Engine;
		this.sapi5Engine = sapi5Engine;
		this.textToSpeech = textToSpeech;

		pluginEventBus.register(this);

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Search Bar
		searchBar = new IconTextField();
		searchBar.setPlaceholderText("Enter name or gender");
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		SEARCH_HINTS.forEach(searchBar.getSuggestionListModel()::addElement);
		searchBar.getDocument().addDocumentListener(new SearchBarListener());

		// Speech Text Bar
		speechText = new IconTextField();
		speechText.setIcon(speechTextIcon);
		speechText.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		speechText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		speechText.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		speechText.setText("Hello, Natural Speech");
		speechText.setToolTipText("Sentence to be spoken.");
		speechText.setPlaceholderText("Enter a sentence");

		// Float Top/North Wrapper Panel, for search and speech text bar.
		JPanel topPanel = new JPanel();
		topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		topPanel.setLayout(new GridLayout(0, 1, 0, PluginPanel.BORDER_OFFSET));
		topPanel.add(searchBar);
		topPanel.add(speechText);
		this.add(topPanel, BorderLayout.NORTH);

		// Speakers panel containing individual speaker item panels
		sectionListPanel = new FixedWidthPanel();
		sectionListPanel.setBorder(new EmptyBorder(0, 5, 0, 5));
		sectionListPanel.setLayout(new OnlyVisibleGridLayout(0, 1, 0, 5));


		// North panel wraps and fixes the speakerList north
		JPanel speakerListNorthWrapper = new FixedWidthPanel();
		speakerListNorthWrapper.setLayout(new BorderLayout());
		speakerListNorthWrapper.add(sectionListPanel, BorderLayout.NORTH);


		// A parent scroll view pane for speakerListPanel
		JScrollPane speakerScrollPane = new JScrollPane(speakerListNorthWrapper);
		speakerScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		this.add(speakerScrollPane);

		buildSpeakerList();
	}

	@Subscribe
	private void onPiperModelStarted(PiperModelStarted event) {
		String modelName = event.getPiper().getModelLocal().getModelName();

		JPanel section = modelSections.get(modelName);
		if (section != null) {
			section.setVisible(true);
			SwingUtilities.invokeLater(sectionListPanel::revalidate);
		}
		else {
			// buildSpeakerList builds sections using PiperRepository
			// if this triggers that means a new model was not in PiperRepository.
			log.error("Started model not found in VoiceExplorer:{}", modelName);
		}
		updateWarnings();
	}

	@Subscribe
	private void onPiperModelStopped(PiperModelStopped event) {
		String modelName = event.getPiper().getModelLocal().getModelName();

		JPanel section = modelSections.get(modelName);
		if (section != null) {
			section.setVisible(false);
			SwingUtilities.invokeLater(sectionListPanel::revalidate);
		}
		else {
			// buildSpeakerList builds sections using PiperRepository
			// if this triggers that means a new model was not in PiperRepository.
			log.error("Started model not found in VoiceExplorer:{}", modelName);
		}
		updateWarnings();
	}

	@Subscribe
	private void onSpeechEngineStarted(SpeechEngineStarted event) {
		if (event.getSpeechEngine() == sapi4Engine || event.getSpeechEngine() == sapi5Engine) {
			if (sapi4Engine.isStarted() || sapi5Engine.isStarted()) {
				microsoftSegment.setVisible(true);
				SwingUtilities.invokeLater(sectionListPanel::revalidate);
			}
		}
		updateVoiceListItems();
		updateWarnings();
	}

	@Subscribe
	private void onSpeechEngineStopped(SpeechEngineStopped event) {
		if (event.getSpeechEngine() == sapi4Engine || event.getSpeechEngine() == sapi5Engine) {
			if (!sapi4Engine.isStarted() && !sapi5Engine.isStarted()) {
				microsoftSegment.setVisible(false);
				SwingUtilities.invokeLater(sectionListPanel::revalidate);
			}
		}
		updateVoiceListItems();
		updateWarnings();
	}

	@Subscribe
	private void onTextToSpeechFailedStart(TextToSpeechFailedStart event) {
		if (event.getReason() == TextToSpeechFailedStart.Reason.ALL_FAILED) {
			centerNoEngineWarning.setVisible(true);
			centerStoppedWarning.setVisible(false);
			centerCopyHint.setVisible(false);
		}
		else {
			centerNoEngineWarning.setVisible(false);
			centerStoppedWarning.setVisible(true);
			centerCopyHint.setVisible(false);
		}
	}

	@Subscribe
	private void onTextToSpeechStarted(TextToSpeechStarted event) {
		updateWarnings();
	}

	@Subscribe
	private void onTextToSpeechStopped(TextToSpeechStopped event) {
		updateWarnings();
	}

	@Subscribe
	private void onPiperPathChanged(PiperPathChanged event) {
		SwingUtilities.invokeLater(this::buildSpeakerList);
	}

	@Subscribe
	private void onPiperRepositoryChanged(PiperRepositoryChanged event) {
		SwingUtilities.invokeLater(this::buildSpeakerList);
	}

	private void updateWarnings() {
		boolean showWarning = !textToSpeech.isStarted();
		centerStoppedWarning.setVisible(showWarning);
		centerCopyHint.setVisible(!showWarning);
		revalidate();
	}

	private void updateVoiceListItems() {
		for (VoiceListItem voiceListItem : voiceListItems) {
			if (textToSpeech.canSpeak(voiceListItem.getVoiceMetadata().getVoiceId())) {
				voiceListItem.setVisible(true);
			}
			else {
				voiceListItem.setVisible(false);
			}
		}
		revalidate();
	}

	private void buildSpeakerList() {
		sectionListPanel.removeAll();

		JLabel stoppedWarning =
			new JLabel("<html>Natural Speech is not running<br>Start the engine to see voices</html>",
				SwingConstants.CENTER);
		stoppedWarning.setBorder(new EmptyBorder(5, 5, 5, 5));
		stoppedWarning.setFont(FontManager.getRunescapeFont());
		stoppedWarning.setForeground(Color.BLACK);
		stoppedWarning.setBackground(new Color(0xFFBB33));
		stoppedWarning.setOpaque(true);
		centerStoppedWarning = new JPanel();
		centerStoppedWarning.setVisible(false);
		centerStoppedWarning.setLayout(new BorderLayout());
		centerStoppedWarning.add(stoppedWarning, BorderLayout.CENTER);

		JLabel noEngineWarning =
			new JLabel("<html>There are no available voices installed</html>", SwingConstants.CENTER);
		noEngineWarning.setBorder(new EmptyBorder(5, 5, 5, 5));
		noEngineWarning.setFont(FontManager.getRunescapeFont());
		noEngineWarning.setForeground(Color.BLACK);
		noEngineWarning.setBackground(new Color(0xFFBB33));
		noEngineWarning.setOpaque(true);
		centerNoEngineWarning = new JPanel();
		centerNoEngineWarning.setVisible(false);
		centerNoEngineWarning.setLayout(new BorderLayout());
		centerNoEngineWarning.add(noEngineWarning, BorderLayout.CENTER);

		JLabel copyHint = new JLabel("click to copy, paste to voice setting", SwingConstants.CENTER);
		copyHint.setFont(FontManager.getRunescapeFont());
		copyHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		centerCopyHint = new JPanel();
		centerCopyHint.setOpaque(false);
		centerCopyHint.setLayout(new BorderLayout());
		centerCopyHint.add(copyHint, BorderLayout.CENTER);

		sectionListPanel.add(centerNoEngineWarning);
		sectionListPanel.add(centerStoppedWarning);
		sectionListPanel.add(centerCopyHint);

		List<String> sapi4Models = sapi4Repository.getVoices();
		List<SAPI5Process.SAPI5Voice> sapi5Models = sapi5Engine.getAvailableSAPI5s();
		List<PiperRepository.ModelURL> piperModelURLS = piperRepository.getModelURLS();

		buildMicrosoftModelSegment(sapi4Models, sapi5Models);

		for (PiperRepository.ModelURL modelURL : piperModelURLS) {
			if (piperRepository.hasModelLocal(modelURL.getModelName())) {
				buildPiperModelSegment(modelURL.getModelName());
			}
		}

		builderSearchTermMultiMap();

		updateWarnings();
	}

	private void builderSearchTermMultiMap() {
		searchToItemBiMap.clear();
		for (VoiceListItem voiceListItem : voiceListItems) {


			String name = voiceListItem.getVoiceMetadata().getName().toLowerCase();
			List<String> nameTokens = List.of(name.split(" "));

			String modelName = voiceListItem.getVoiceMetadata().getVoiceId().modelName.toLowerCase();
			String id = voiceListItem.getVoiceMetadata().getVoiceId().getId().toLowerCase();
			String gender = voiceListItem.getVoiceMetadata().getGender().string;

			// can use trees, but let's use hashes for now, even faster, and our voice list is tiny
			// Assuming 64-bit pointer = 8byte
			// 1000~ voices * 10~ averageNameLength * (4byte int-hash-key + 8byte value pointer) = 120KB of memory
			// keys are also unique, so any overlapping keys are ignored,
			// ex. "abc" -> "a", "ab", "abc"
			//     "abb" -> "abb" (keys already exists for "a" and "ab")
			List<String> partials = new ArrayList<>();
			StringBuilder partialBuilder = new StringBuilder();
			for (String nameToken : nameTokens) {
				partialBuilder.setLength(0);
				for (char c : nameToken.toCharArray()) {
					partialBuilder.append(c);
					partials.add(partialBuilder.toString());
				}
			}

			// so tiny it's not worth calculating; numbered keys are highly overlapping
			partialBuilder.setLength(0);
			for (char c : id.toCharArray()) {
				partialBuilder.append(c);
				partials.add(partialBuilder.toString());
			}

			partials.forEach(partialTerm -> searchToItemBiMap.put(partialTerm, voiceListItem));
			nameTokens.forEach(nameToken -> searchToItemBiMap.put(nameToken, voiceListItem));
//			searchToItemBiMap.put(name, voiceListItem); // partial already contains complete
			searchToItemBiMap.put(modelName, voiceListItem);
			searchToItemBiMap.put(id, voiceListItem);
			searchToItemBiMap.put(gender, voiceListItem);
		}
	}

	private void buildMicrosoftModelSegment(List<String> sapi4Models, List<SAPI5Process.SAPI5Voice> sapi5Models) {
		microsoftSegment = new JPanel();
		microsoftSegment.setLayout(new BoxLayout(microsoftSegment, BoxLayout.Y_AXIS));
		microsoftSegment.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		microsoftSegment.setVisible(false);

		final JPanel sectionHeader = new JPanel();
		sectionHeader.setLayout(new BorderLayout());
		sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		// For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
		// border on the right only affects the width when closed, fixing the issue.
		sectionHeader.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		microsoftSegment.add(sectionHeader);

		final JButton sectionToggle = new JButton(SECTION_RETRACT_ICON);
		sectionToggle.setPreferredSize(new Dimension(18, 0));
		sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		sectionToggle.setToolTipText("Retract");
		SwingUtil.removeButtonDecorations(sectionToggle);
		sectionHeader.add(sectionToggle, BorderLayout.WEST);

		final String name = "microsoft";
		final String description = name;
		final JLabel sectionName = new JLabel(name);
		sectionName.setForeground(ColorScheme.BRAND_ORANGE);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		sectionName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		final JPanel sectionContent = new JPanel();
		sectionContent.setLayout(new OnlyVisibleGridLayout(0, 1, 0, 5));
		sectionContent.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		microsoftSegment.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		microsoftSegment.add(sectionContent, BorderLayout.SOUTH);

		// Add listeners to each part of the header so that it's easier to toggle them
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSpeakerSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSpeakerSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		toggleSpeakerSection(sectionToggle, sectionContent);

		sapi4Models.stream()
			.sorted()
			.forEach((modelName) -> {
				VoiceMetadata metadata =
					new VoiceMetadata("", Gender.MALE, new VoiceID(SAPI4Engine.SAPI4_MODEL_NAME, modelName));
				VoiceListItem speakerItem = new VoiceListItem(this, textToSpeech, metadata);
				voiceListItems.add(speakerItem);
				sectionContent.add(speakerItem);
			});

		sapi5Models.stream()
			.sorted(Comparator.comparing(SAPI5Process.SAPI5Voice::getName))
			.forEach((voice) -> {
				String modelName = SAPI5Alias.sapiToModelName.getOrDefault(voice.getName(), voice.getName());

				// The ID is the model name, no need to display the full name
				VoiceMetadata metadata = new VoiceMetadata("", voice.getGender(),
					new VoiceID(SAPI5Engine.SAPI5_MODEL_NAME, modelName)
				);

				VoiceListItem speakerItem = new VoiceListItem(this, textToSpeech, metadata);
				voiceListItems.add(speakerItem);
				sectionContent.add(speakerItem);
			});

		sectionListPanel.add(microsoftSegment);
		modelSections.put("microsoft", microsoftSegment);
	}

	private void toggleSpeakerSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState ? "Retract" : "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	private void buildPiperModelSegment(String modelName) {

		final JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.setVisible(false);

		final JPanel sectionHeader = new JPanel();
		sectionHeader.setLayout(new BorderLayout());
		sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		// For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
		// border on the right only affects the width when closed, fixing the issue.
		sectionHeader.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		section.add(sectionHeader);

		final JButton sectionToggle = new JButton(SECTION_RETRACT_ICON);
		sectionToggle.setPreferredSize(new Dimension(18, 0));
		sectionToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		sectionToggle.setToolTipText("Retract");
		SwingUtil.removeButtonDecorations(sectionToggle);
		sectionHeader.add(sectionToggle, BorderLayout.WEST);

		final String name = modelName;
		final String description = modelName;
		final JLabel sectionName = new JLabel(name);
		sectionName.setForeground(ColorScheme.BRAND_ORANGE);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		sectionName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		final JPanel sectionContent = new JPanel();
		sectionContent.setLayout(new OnlyVisibleGridLayout(0, 1, 0, 5));
		sectionContent.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		section.add(sectionContent, BorderLayout.SOUTH);

		// Add listeners to each part of the header so that it's easier to toggle them
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSpeakerSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSpeakerSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		toggleSpeakerSection(sectionToggle, sectionContent);

		try {
			PiperRepository.ModelLocal modelLocal = piperRepository.loadModelLocal(modelName);

			Arrays.stream(modelLocal.getPiperVoiceMetadata())
				.sorted(Comparator.comparing(a -> a.getPiperVoiceID()))
				.forEach((piperVoiceMetadata) -> {
					VoiceListItem speakerItem =
						new VoiceListItem(this, textToSpeech, VoiceMetadata.from(piperVoiceMetadata));
					voiceListItems.add(speakerItem);
					sectionContent.add(speakerItem);
				});

		} catch (IOException e) {throw new RuntimeException(e);}

		sectionListPanel.add(section);
		modelSections.put(modelName, section);
	}

	void searchFilter(String searchInput) {
		try {
			if (searchInput.isEmpty()) {
				for (VoiceListItem speakerItems : voiceListItems) {speakerItems.setVisible(true);}
				return;
			}

			// split search by space and comma
			List<String> searchTerms = Arrays.stream(searchInput.toLowerCase().split("[,\\s]+"))
				.filter(s -> !s.isEmpty())
				.map(String::trim)
				.map(String::toLowerCase).collect(Collectors.toList());

			if (searchTerms.isEmpty()) {
				for (VoiceListItem speakerItems : voiceListItems) {speakerItems.setVisible(true);}
				return;
			}

			Set<VoiceListItem> results = new HashSet<>(searchToItemBiMap.get(searchTerms.get(0)));
			for (int i = 1, n = searchTerms.size(); i < n; i++) {
				String term = searchTerms.get(i);
				results.removeIf(item -> !searchToItemBiMap.containsEntry(term, item));
			}

			voiceListItems.forEach((item) -> {item.setVisible(results.contains(item));});
		} finally {
			sectionListPanel.revalidate();
		}

	}

	public void shutdown() {
		this.removeAll();
	}

	@Override
	public void onActivate() {
		super.onActivate();

		SwingUtilities.invokeLater(() -> setVisible(true));
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		SwingUtilities.invokeLater(() -> setVisible(false));
	}

	private class SearchBarListener implements DocumentListener {

		public void search() {
			searchFilter(searchBar.getText());
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			search();
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			search();
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			search();
		}
	}
}
