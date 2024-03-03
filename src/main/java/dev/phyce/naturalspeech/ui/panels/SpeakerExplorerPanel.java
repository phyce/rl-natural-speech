package dev.phyce.naturalspeech.ui.panels;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.VoiceRepository;
import dev.phyce.naturalspeech.ui.components.IconTextField;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class SpeakerExplorerPanel extends EditorPanel {

	private final VoiceRepository voiceRepository;
	private final NaturalSpeechPlugin plugin;

	@Getter
	private final IconTextField speechText;
	@Getter
	private final IconTextField searchBar;
	@Getter
	private final FixedWidthPanel speakerListPanel;
	@Getter
	private final JScrollPane speakerScrollPane;

	final ImageIcon speechTextIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "speechText.png"));

	private static final ImmutableList<String> SEARCH_HINTS = ImmutableList.of(
			"Male", // Special search term for disabled plugins
			"Female" // Special search term for pinned plugins
	);

	@Inject
	public SpeakerExplorerPanel(VoiceRepository voiceRepository, NaturalSpeechPlugin plugin) {
		this.voiceRepository = voiceRepository;
		this.plugin = plugin;

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
		// add search bar
		topPanel.add(searchBar);
		// add speech text bar
		topPanel.add(speechText);
		this.add(topPanel, BorderLayout.NORTH);

		// Speakers panel containing individual speaker item panels
		speakerListPanel = new FixedWidthPanel();
		speakerListPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
		speakerListPanel.setLayout(new OnlyVisibleGridLayout(0, 1, 0, 5));
		speakerListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// North panel wraps and fixes the speakerList north
		JPanel speakerListNorthWrapper = new FixedWidthPanel();
		speakerListNorthWrapper.setLayout(new BorderLayout());
		speakerListNorthWrapper.add(speakerListPanel, BorderLayout.NORTH);

		// A parent scroll view pane for speakerListPanel
		speakerScrollPane = new JScrollPane(speakerListNorthWrapper);
		speakerScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		this.add(speakerScrollPane);

		buildSpeakerList();
	}

	void buildSpeakerList() {

		try {
			// TODO(Louis) loadPiper actually downloads the voice if local files don't exist
			VoiceRepository.PiperVoice piperVoice = voiceRepository.loadPiperVoice("en_US-libritts-high");
			Arrays.stream(piperVoice.getSpeakers()).sorted(Comparator.comparing(a -> a.getName().toLowerCase())).forEach((speaker) -> {
				SpeakerListItem speakerItem = new SpeakerListItem(this, plugin, speaker);
				speakerListPanel.add(speakerItem);
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	void searchFilter(String name_search) {
		if (name_search.isEmpty()) {
			// enable all and return
			for (Component comp : speakerListPanel.getComponents()) {
				comp.setVisible(true);
			}
			return;
		}

		// split search by space and comma
		Set<String> searchTerms = Arrays.stream(name_search.toLowerCase().split("[,\\s]+"))
				// remove empty strings
				.filter(s -> !s.isEmpty())
				// truncate leading and trailing empty space
				.map(String::trim)
				// apply lower case
				.map(String::toLowerCase).collect(Collectors.toSet());

		String gender_search = null;
		Iterator<String> it = searchTerms.iterator();
		while (it.hasNext()) {
			String searchTerm = it.next();
			if (List.of("m", "male", "guy").contains(searchTerm)) {
				gender_search = "M";
				it.remove();
			} else if (List.of("f", "female", "girl").contains(searchTerm)) {
				gender_search = "F";
				it.remove();
			}
		}

		name_search = StringUtils.join(searchTerms, " ");

		for (Component comp : speakerListPanel.getComponents()) {

			VoiceRepository.Speaker speaker = ((SpeakerListItem) comp).getSpeaker();

			boolean visible = true;

			if (gender_search != null && !gender_search.equals(speaker.getGender())) {
				visible = false;
			}

			// name search
			if (!name_search.isEmpty()) {
				boolean term_matched = false;
				// split speaker name and check if any of the search terms are in the name
				if (!searchTerms.isEmpty()) {
					// if nameTerms contain any of the search terms, then term_matched = true
					if (speaker.getName().toLowerCase().contains(name_search)) {
						term_matched = true;
					}
				}

				if (!term_matched) {
					visible = false;
				}
			}
			comp.setVisible(visible);
		}

		speakerListPanel.revalidate();
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
