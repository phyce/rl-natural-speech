package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;

@Slf4j
public class MainSettingsPanel extends PluginPanel {
	public static final ImageIcon SECTION_EXPAND_ICON;
	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(6, 6, 6, 6);
	private static final ImageIcon SECTION_RETRACT_ICON;
	private static final Dimension OUTER_PREFERRED_SIZE = new Dimension(242, 0);

	static {
		BufferedImage sectionRetractIcon = ImageUtil.loadImageResource(MainSettingsPanel.class, "section_icons/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
	}

	private final FixedWidthPanel mainContentPanel;
	private final JScrollPane scrollPane;
	private final ModelRepository modelRepository;
	private final NaturalSpeechConfig config;
	private final NaturalSpeechPlugin plugin;
	private final ConfigManager configManager;
	private final Downloader downloader;

	@Inject
	public MainSettingsPanel(
			NaturalSpeechConfig config,
			NaturalSpeechPlugin plugin,
			ConfigManager configManager,
			Downloader downloader,
			ModelRepository modelRepository
	) {
		super(false);
		this.config = config;
		this.plugin = plugin;
		this.configManager = configManager;
		this.downloader = downloader;
		this.modelRepository = modelRepository;

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		mainContentPanel = new FixedWidthPanel();
		mainContentPanel.setBorder(new EmptyBorder(8, 10, 10, 10));
		mainContentPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		mainContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel mainContentNorthWrapper = new FixedWidthPanel();
		mainContentNorthWrapper.setLayout(new BorderLayout());
		mainContentNorthWrapper.add(mainContentPanel, BorderLayout.NORTH);

		scrollPane = new JScrollPane(mainContentNorthWrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		this.add(scrollPane);

		buildHeaderSegment();
		buildPiperStatusSection();
		buildVoiceRepositorySegment();

		this.revalidate();
	}

	public void buildHeaderSegment() {
		JLabel titleLabel = new JLabel("NaturalSpeech", JLabel.CENTER);
		titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
		titleLabel.setBorder(new EmptyBorder(1, 0, 1, 0));
		mainContentPanel.add(titleLabel);


		// Instructions Link
		JLabel instructionsLink = new JLabel("<html>For instructions, click <a href='#'>here</a>.</html>", JLabel.CENTER);
		instructionsLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		instructionsLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					Desktop.getDesktop().browse(new URI("https://github.com/phyce/rl-natural-speech"));
				} catch (Exception ex) {
					log.error("Error opening instruction link.", ex);
				}
			}
		});
		instructionsLink.setBorder(new EmptyBorder(0, 0, 5, 0));
		mainContentPanel.add(instructionsLink);

	}

	public void buildVoiceRepositorySegment() {
		final JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

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

		final String name = "Voice Repository";
		final String description = "Manage your voice models.";
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

		mainContentPanel.add(section);
	}

	public void buildPiperStatusSection() {
		final JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setMinimumSize(new Dimension(PANEL_WIDTH, 0));

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

		final String name = "Piper Status";
		final String description = "Manage your piper instances.";
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

		mainContentPanel.add(section);
	}

	@Override
	public void onActivate() {
		super.onActivate();

		this.setVisible(true);
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		this.setVisible(false);
	}
}
