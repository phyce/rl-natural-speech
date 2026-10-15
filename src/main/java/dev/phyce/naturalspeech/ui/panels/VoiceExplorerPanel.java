package dev.phyce.naturalspeech.ui.panels;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.ui.components.CollapsibleSection;
import dev.phyce.naturalspeech.ui.components.IconTextField;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

public class VoiceExplorerPanel extends PluginPanel {

	private static final ImmutableList<String> SEARCH_HINTS = ImmutableList.of("Male", "Female");
	private static final String SINGLE_SECTION_KEY = "voiceExplorerSingleSection";

	final ImageIcon speechTextIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "speechText.png"));
	private final ModelRepository modelRepository;
	private final TextToSpeech textToSpeech;
	private final ConfigManager configManager;
	@Getter
	private final IconTextField speechText;
	@Getter
	private final IconTextField searchBar;
	private final JCheckBox singleSectionBox;
	@Getter
	private final FixedWidthPanel sectionListPanel;
	@Getter
	private final JScrollPane speakerScrollPane;
	private final List<VoiceListItem> voiceListItems = new ArrayList<>();
	private final Map<CollapsibleSection, List<CollapsibleSection>> engineSections = new LinkedHashMap<>();
	private final Map<CollapsibleSection, List<VoiceListItem>> modelSections = new LinkedHashMap<>();
	private final Map<String, CollapsibleSection> sectionsByKey = new HashMap<>();
	private final Map<String, Boolean> expandedStates = new HashMap<>();
	private final ModelRepository.ModelRepositoryListener modelRepositoryListener;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final Runnable elevenLabsListener;

	@Inject
	public VoiceExplorerPanel(ModelRepository modelRepository, TextToSpeech textToSpeech,
							  ElevenLabsVoiceRepository elevenLabsVoiceRepository, ConfigManager configManager) {
		super(false);
		this.modelRepository = modelRepository;
		this.textToSpeech = textToSpeech;
		this.elevenLabsVoiceRepository = elevenLabsVoiceRepository;
		this.configManager = configManager;

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

		Boolean singleSection = configManager.getConfiguration(CONFIG_GROUP, SINGLE_SECTION_KEY, Boolean.class);
		singleSectionBox = new JCheckBox("Only one section open at a time", singleSection == null || singleSection);
		singleSectionBox.addActionListener(e -> {
			configManager.setConfiguration(CONFIG_GROUP, SINGLE_SECTION_KEY, singleSectionBox.isSelected());
			if (singleSectionBox.isSelected()) collapseExtraSections();
		});

		// Float Top/North Wrapper Panel, for search and speech text bar.
		JPanel topPanel = new JPanel();
		topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		topPanel.setLayout(new GridLayout(0, 1, 0, PluginPanel.BORDER_OFFSET));
		JLabel copyHint = new JLabel("Click a voice to copy the ID.", JLabel.CENTER);
		copyHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		topPanel.add(searchBar);
		topPanel.add(speechText);
		topPanel.add(singleSectionBox);
		topPanel.add(copyHint);
		this.add(topPanel, BorderLayout.NORTH);

		// Speakers panel containing individual speaker item panels
		sectionListPanel = new FixedWidthPanel();
		sectionListPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
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

		modelRepositoryListener = new ModelRepository.ModelRepositoryListener() {
			@Override
			public void onRepositoryChanged(String modelName) {
				SwingUtilities.invokeLater(() -> buildSpeakerList());
			}

			@Override
			public void onRefresh() {
				SwingUtilities.invokeLater(() -> buildSpeakerList());
			}
		};
		this.modelRepository.addRepositoryChangedListener(modelRepositoryListener);

		elevenLabsListener = () -> SwingUtilities.invokeLater(this::buildSpeakerList);
		this.elevenLabsVoiceRepository.addListener(elevenLabsListener);

		buildSpeakerList();
	}

	void buildSpeakerList() {
		voiceListItems.forEach(VoiceListItem::dispose);
		voiceListItems.clear();
		sectionsByKey.forEach((key, section) -> expandedStates.put(key, section.isExpanded()));
		sectionsByKey.clear();
		engineSections.clear();
		modelSections.clear();
		sectionListPanel.removeAll();

		CollapsibleSection piperSection = buildEngineSection("Piper", "Voices that run on your computer.", "piper", true);
		for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
			try {
				if (modelRepository.hasModelLocal(modelURL.getModelName())) {
					buildSpeakerSegmentForVoice(piperSection, modelURL);
				}
			} catch (IOException ignore) {
			}
		}

		List<ElevenLabsModel> elevenLabsModels = elevenLabsVoiceRepository.getEnabledModels();
		if (elevenLabsVoiceRepository.hasApiKey() && elevenLabsVoiceRepository.hasVoices() && !elevenLabsModels.isEmpty()) {
			CollapsibleSection elevenLabsSection = buildEngineSection("ElevenLabs",
				"Previews use your ElevenLabs credits.", "elevenlabs", false);
			List<ElevenLabsVoice> voices = elevenLabsVoiceRepository.getVoices().stream()
				.sorted(Comparator.comparing(voice -> voice.getName().toLowerCase()))
				.collect(Collectors.toList());

			for (ElevenLabsModel model : elevenLabsModels) {
				CollapsibleSection modelSection = buildModelSection(elevenLabsSection, model.toString(),
					model.getDescription(), "elevenlabs_" + model.getId());
				for (ElevenLabsVoice voice : voices) {
					addVoiceListItem(modelSection, VoiceListItem.forElevenLabsVoice(this, textToSpeech, voice, model));
				}
			}
		}

		if (singleSectionBox.isSelected()) collapseExtraSections();
		searchFilter(searchBar.getText());
		sectionListPanel.repaint();
	}

	private void addVoiceListItem(CollapsibleSection modelSection, VoiceListItem speakerItem) {
		voiceListItems.add(speakerItem);
		modelSections.get(modelSection).add(speakerItem);
		modelSection.getContent().add(speakerItem);
	}

	private void buildSpeakerSegmentForVoice(CollapsibleSection piperSection, ModelRepository.ModelURL modelURL) {
		String modelName = modelURL.getModelName();
		CollapsibleSection modelSection =
			buildModelSection(piperSection, modelName, modelURL.getDescription(), "piper_" + modelName);

		try {
			ModelRepository.ModelLocal modelLocal = modelRepository.loadModelLocal(modelName);

			Arrays.stream(modelLocal.getVoiceMetadata())
				.sorted(Comparator.comparing(a -> a.getName().toLowerCase()))
				.forEach(voiceMetadata -> addVoiceListItem(modelSection, VoiceListItem.forPiperVoice(this, textToSpeech, voiceMetadata)));

		} catch (IOException e) {throw new RuntimeException(e);}
	}

	private CollapsibleSection buildEngineSection(String name, String description, String stateKey,
												  boolean expandedByDefault) {
		CollapsibleSection section = new CollapsibleSection(name, description, new OnlyVisibleGridLayout(0, 1, 0, 5),
			expandedStates.getOrDefault(stateKey, expandedByDefault));
		section.getContent().setBorder(new EmptyBorder(0, 8, 0, 0));
		section.setOnExpanded(() -> collapseSiblings(section, engineSections.keySet()));

		engineSections.put(section, new ArrayList<>());
		sectionsByKey.put(stateKey, section);
		sectionListPanel.add(section);
		return section;
	}

	private CollapsibleSection buildModelSection(CollapsibleSection engineSection, String name, String description,
												 String stateKey) {
		List<CollapsibleSection> siblings = engineSections.get(engineSection);
		CollapsibleSection section = new CollapsibleSection(name, description, new OnlyVisibleGridLayout(0, 1, 0, 5),
			expandedStates.getOrDefault(stateKey, siblings.isEmpty()));
		section.setOnExpanded(() -> collapseSiblings(section, siblings));

		siblings.add(section);
		modelSections.put(section, new ArrayList<>());
		sectionsByKey.put(stateKey, section);
		engineSection.getContent().add(section);
		return section;
	}

	private void collapseSiblings(CollapsibleSection expanded, Iterable<CollapsibleSection> siblings) {
		if (!singleSectionBox.isSelected()) return;

		for (CollapsibleSection sibling : siblings) {
			if (sibling != expanded && sibling.isExpanded()) sibling.setExpanded(false);
		}
	}

	private void collapseExtraSections() {
		collapseAllButFirst(engineSections.keySet());
		engineSections.values().forEach(this::collapseAllButFirst);
	}

	private void collapseAllButFirst(Iterable<CollapsibleSection> sections) {
		boolean foundExpanded = false;
		for (CollapsibleSection section : sections) {
			if (!section.isExpanded()) continue;

			if (foundExpanded) section.setExpanded(false);
			foundExpanded = true;
		}
	}

	void searchFilter(String searchInput) {
		if (searchInput.isEmpty()) {
			for (VoiceListItem speakerItems : voiceListItems) {speakerItems.setVisible(true);}
			updateSectionVisibility();
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
			boolean visible = genderSearch == null || genderSearch.equals(speakerItem.getGender());

			// name search
			if (!searchInput.isEmpty()) {
				boolean term_matched = false;
				if (!searchTerms.isEmpty() && speakerItem.getVoiceName().toLowerCase().contains(searchInput)) {
					term_matched = true;
				}

				if (!term_matched) visible = false;
			}
			speakerItem.setVisible(visible);
		}

		updateSectionVisibility();
	}

	private void updateSectionVisibility() {
		modelSections.forEach((section, items) -> section.setVisible(items.stream().anyMatch(Component::isVisible)));
		engineSections.forEach((section, models) -> section.setVisible(models.stream().anyMatch(Component::isVisible)));
		sectionListPanel.revalidate();
	}

	public void shutdown() {
		modelRepository.removeRepositoryChangedListener(modelRepositoryListener);
		elevenLabsVoiceRepository.removeListener(elevenLabsListener);
		voiceListItems.forEach(VoiceListItem::dispose);
		voiceListItems.clear();
		this.removeAll();
	}
}
