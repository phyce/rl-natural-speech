package dev.phyce.naturalspeech.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.VoiceRepository;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class SpeakerExplorerPanel extends PluginPanel {

    private final VoiceRepository voiceRepository;
    private final NaturalSpeechPlugin plugin;

    @Getter
    private final IconTextField speechText;
    private final IconTextField searchBar;
    final ImageIcon speechTextIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "speechText.png"));
    private final FixedWidthPanel mainPanel;
    private final JScrollPane scrollPane;

    @Inject
    public SpeakerExplorerPanel(VoiceRepository voiceRepository, NaturalSpeechPlugin plugin) {
        super(false);
        this.voiceRepository = voiceRepository;
        this.plugin = plugin;


        this.setLayout(new BorderLayout());
        this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Search Text
        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);

        // Speech Text Bar
        speechText = new IconTextField();
        speechText.setIcon(speechTextIcon);
        speechText.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        speechText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        speechText.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        speechText.setText("Hello, Natural Speech");
        speechText.setToolTipText("Sentence to be spoken.");

        // Top most panel
        JPanel topPanel = new JPanel();
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        topPanel.setLayout(new GridLayout(0, 1, 0, PluginPanel.BORDER_OFFSET));
        // add search bar
        topPanel.add(searchBar);
        // add speech text bar
        topPanel.add(speechText);
        this.add(topPanel, BorderLayout.NORTH);

        // Speakers panel
        mainPanel = new FixedWidthPanel();
        mainPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
        mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel northPanel = new FixedWidthPanel();
        northPanel.setLayout(new BorderLayout());
        northPanel.add(mainPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(northPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.add(scrollPane);

        buildSpeakerList();
    }

    void buildSpeakerList() {

        try {
            VoiceRepository.PiperVoice piperVoice = voiceRepository.loadPiperVoice("en_US-libritts-high");
            Arrays.stream(piperVoice.getSpeakers())
                    .sorted(Comparator.comparing(VoiceRepository.Speaker::getName))
                    .forEach((speaker) -> {
                        SpeakerListItem speakerItem = new SpeakerListItem(this, plugin, speaker);
                        mainPanel.add(speakerItem);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();

        setVisible(true);
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();

        setVisible(false);
    }
}
