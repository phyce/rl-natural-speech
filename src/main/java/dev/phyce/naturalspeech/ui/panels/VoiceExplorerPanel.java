package dev.phyce.naturalspeech.ui.panels;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.events.SpeechEngineStarted;
import dev.phyce.naturalspeech.events.SpeechEngineStopped;
import dev.phyce.naturalspeech.events.piper.PiperModelStarted;
import dev.phyce.naturalspeech.events.piper.PiperModelStopped;
import dev.phyce.naturalspeech.events.piper.PiperPathChanged;
import dev.phyce.naturalspeech.events.piper.PiperRepositoryChanged;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4Engine;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4Repository;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4VoiceCache;
import dev.phyce.naturalspeech.ui.components.IconTextField;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import java.awt.BorderLayout;
import java.awt.Component;
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
import java.util.Iterator;
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
import org.apache.commons.lang3.StringUtils;

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
	private final TextToSpeech textToSpeech;
	private final PluginEventBus pluginEventBus;

	public static final ImageIcon SECTION_EXPAND_ICON;
	private static final ImageIcon SECTION_RETRACT_ICON;

	final ImageIcon speechTextIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "speechText.png"));

	private static final ImmutableList<String> SEARCH_HINTS = ImmutableList.of("Male", "Female");

	@Getter
	private final IconTextField speechText;
	private final IconTextField searchBar;
	private final FixedWidthPanel sectionListPanel;
	private final JScrollPane speakerScrollPane;
	private final List<VoiceListItem> voiceListItems = new ArrayList<>();
	private final Map<String, JPanel> modelSections = new HashMap<>();
	private JPanel sapi4Segment;

	@Inject
	public VoiceExplorerPanel(
		PiperRepository piperRepository,
		SAPI4Repository sapi4Repository,
		TextToSpeech textToSpeech,
		PluginEventBus pluginEventBus
	) {
		this.piperRepository = piperRepository;
		this.sapi4Repository = sapi4Repository;
		this.textToSpeech = textToSpeech;
		this.pluginEventBus = pluginEventBus;

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
		searchBar.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				searchFilter(searchBar.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				searchFilter(searchBar.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				searchFilter(searchBar.getText());
			}
		});

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
		sectionListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// North panel wraps and fixes the speakerList north
		JPanel speakerListNorthWrapper = new FixedWidthPanel();
		speakerListNorthWrapper.setLayout(new BorderLayout());
		speakerListNorthWrapper.add(sectionListPanel, BorderLayout.NORTH);

		// A parent scroll view pane for speakerListPanel
		speakerScrollPane = new JScrollPane(speakerListNorthWrapper);
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
		} else {
			// buildSpeakerList builds sections using PiperRepository
			// if this triggers that means a new model was not in PiperRepository.
			log.error("Started model not found in VoiceExplorer:{}", modelName);
		}
	}

	@Subscribe
	private void onPiperModelStopped(PiperModelStopped event) {
		String modelName = event.getPiper().getModelLocal().getModelName();

		JPanel section = modelSections.get(modelName);
		if (section != null) {
			section.setVisible(false);
			SwingUtilities.invokeLater(sectionListPanel::revalidate);
		} else {
			// buildSpeakerList builds sections using PiperRepository
			// if this triggers that means a new model was not in PiperRepository.
			log.error("Started model not found in VoiceExplorer:{}", modelName);
		}
	}

	@Subscribe
	private void onSpeechEngineStarted(SpeechEngineStarted event) {
		if (event.getSpeechEngine() instanceof SAPI4Engine) {
			sapi4Segment.setVisible(true);
			SwingUtilities.invokeLater(sectionListPanel::revalidate);
		}
	}

	@Subscribe
	private void onSpeechEngineStopped(SpeechEngineStopped event) {
		if (event.getSpeechEngine() instanceof SAPI4Engine) {
			sapi4Segment.setVisible(false);
			SwingUtilities.invokeLater(sectionListPanel::revalidate);
		}
	}

	@Subscribe
	private void onPiperPathChanged(PiperPathChanged event) {
		SwingUtilities.invokeLater(this::buildSpeakerList);
	}

	@Subscribe
	private void onPiperRepositoryChanged(PiperRepositoryChanged event) {
		SwingUtilities.invokeLater(this::buildSpeakerList);
	}

	private void buildSpeakerList() {
		sectionListPanel.removeAll();
		List<String> sapi4Models = sapi4Repository.getVoices();
		if (sapi4Models != null && !sapi4Models.isEmpty()) {
			buildSAPI4ModelSegment();
		}

		for (PiperRepository.ModelURL modelURL : piperRepository.getModelURLS()) {
			if (piperRepository.hasModelLocal(modelURL.getModelName())) {
				buildPiperModelSegment(modelURL.getModelName());
			}
		}
	}

	private void buildSAPI4ModelSegment() {
		sapi4Segment = new JPanel();
		sapi4Segment.setLayout(new BoxLayout(sapi4Segment, BoxLayout.Y_AXIS));
		sapi4Segment.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		sapi4Segment.setVisible(false);

		final JPanel sectionHeader = new JPanel();
		sectionHeader.setLayout(new BorderLayout());
		sectionHeader.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		// For whatever reason, the header extends out by a single pixel when closed. Adding a single pixel of
		// border on the right only affects the width when closed, fixing the issue.
		sectionHeader.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		sapi4Segment.add(sectionHeader);

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
		sapi4Segment.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		sapi4Segment.add(sectionContent, BorderLayout.SOUTH);

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

		List<String> models = sapi4Repository.getVoices();

		models.stream()
			.sorted()
			.forEach((modelName) -> {
				SAPI4VoiceCache cache = SAPI4VoiceCache.findVoiceName(modelName);
				String sapi4Name = cache != null ? cache.sapiName : modelName;

				VoiceMetadata metadata =
					new VoiceMetadata("", Gender.MALE, new VoiceID("microsoft", modelName));
				VoiceListItem speakerItem = new VoiceListItem(this, textToSpeech, metadata);
				voiceListItems.add(speakerItem);
				sectionContent.add(speakerItem);
			});

		sectionListPanel.add(sapi4Segment);
		modelSections.put("microsoft", sapi4Segment);
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
		if (searchInput.isEmpty()) {
			for (VoiceListItem speakerItems : voiceListItems) {speakerItems.setVisible(true);}
			return;
		}

		// split search by space and comma
		Set<String> searchTerms = Arrays.stream(searchInput.toLowerCase().split("[,\\s]+"))
			.filter(s -> !s.isEmpty())
			.map(String::trim)
			.map(String::toLowerCase).collect(Collectors.toSet());

		Gender genderSearch = null;
		Iterator<String> iterator = searchTerms.iterator();
		while (iterator.hasNext()) {
			String searchTerm = iterator.next();

			if (List.of("m", "male", "guy").contains(searchTerm)) {
				genderSearch = Gender.MALE;
				iterator.remove();
			}
			else if (List.of("f", "female", "girl").contains(searchTerm)) {
				genderSearch = Gender.FEMALE;
				iterator.remove();
			}
		}

		searchInput = StringUtils.join(searchTerms, " ");

		for (VoiceListItem speakerItem : voiceListItems) {
			VoiceMetadata piperVoiceMetadata = speakerItem.getVoiceMetadata();

			boolean visible = genderSearch == null || genderSearch.equals(piperVoiceMetadata.getGender());

			// name search
			if (!searchInput.isEmpty()) {
				boolean term_matched = false;
				if (!searchTerms.isEmpty() && piperVoiceMetadata.getName().toLowerCase().contains(searchInput)) {
					term_matched = true;
				}

				if (!term_matched) visible = false;
			}
			speakerItem.setVisible(visible);
		}

		sectionListPanel.revalidate();
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
}
