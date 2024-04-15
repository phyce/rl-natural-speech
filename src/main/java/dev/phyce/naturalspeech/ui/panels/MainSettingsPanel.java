package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.PluginEventBus;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.events.SpeechEngineStarted;
import dev.phyce.naturalspeech.events.SpeechEngineStopped;
import dev.phyce.naturalspeech.events.TextToSpeechStarted;
import dev.phyce.naturalspeech.events.TextToSpeechStopped;
import dev.phyce.naturalspeech.events.piper.PiperModelStarted;
import dev.phyce.naturalspeech.events.piper.PiperModelStopped;
import dev.phyce.naturalspeech.events.piper.PiperPathChanged;
import dev.phyce.naturalspeech.events.piper.PiperRepositoryChanged;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.piper.PiperEngine;
import dev.phyce.naturalspeech.tts.piper.PiperModel;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import dev.phyce.naturalspeech.tts.wsapi4.SAPI4Repository;
import dev.phyce.naturalspeech.tts.wsapi5.SAPI5Engine;
import dev.phyce.naturalspeech.ui.layouts.OnlyVisibleGridLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class MainSettingsPanel extends PluginPanel {

	static {
		BufferedImage sectionRetractIcon =
			ImageUtil.loadImageResource(MainSettingsPanel.class, "section_icons/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
	}

	public static final ImageIcon SECTION_EXPAND_ICON;
	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(6, 6, 6, 6);
	private static final ImageIcon SECTION_RETRACT_ICON;
	//	private static final Dimension OUTER_PREFERRED_SIZE = new Dimension(242, 0);


	private final FixedWidthPanel mainContentPanel;
	private final PiperRepository piperRepository;
	private final SAPI4Repository sapi4Repository;
	private final SAPI5Engine sapi5Engine;
	private final TextToSpeech textToSpeech;
	private final PiperEngine piperEngine;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private JLabel statusLabel;
	private JPanel statusPanel;

	private JPanel piperMonitorPanel;
	private final Map<PiperModel, PiperModelMonitorItem> piperModelMonitorMap = new HashMap<>();
	private final Map<String, PiperModelItem> piperModelMap = new HashMap<>();

	@Inject
	public MainSettingsPanel(
		NaturalSpeechConfig config,
		PiperRepository piperRepository,
		ConfigManager configManager,
		Downloader downloader,
		SAPI4Repository sapi4Repository,
		TextToSpeech textToSpeech,
		PiperEngine piperEngine,
		NaturalSpeechRuntimeConfig runtimeConfig,
		PluginEventBus pluginEventBus,
		SAPI5Engine sapi5Engine
	) {
		super(false);
		this.sapi4Repository = sapi4Repository;
		this.textToSpeech = textToSpeech;
		this.piperRepository = piperRepository;
		this.piperEngine = piperEngine;
		this.runtimeConfig = runtimeConfig;
		this.sapi5Engine = sapi5Engine;

		pluginEventBus.register(this);

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// This panel is where the actual content lives.
		mainContentPanel = new FixedWidthPanel();
		mainContentPanel.setBorder(BORDER_PADDING);
		mainContentPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		mainContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// wrap for scrolling, fixed to NORTH in-order to grow southward
		JPanel mainContentNorthWrapper = new FixedWidthPanel();
		mainContentNorthWrapper.setLayout(new BorderLayout());
		mainContentNorthWrapper.add(mainContentPanel, BorderLayout.NORTH);

		// scroll pane
		JScrollPane scrollPane = new JScrollPane(mainContentNorthWrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		// Can't use Short.MAX_VALUE like the docs say because of JDK-8079640
		scrollPane.setPreferredSize(new Dimension(0x7000, 0x7000));

		this.add(scrollPane);

		buildHeaderSegment();
		buildPiperStatusSegment();
		buildVoiceRepositorySegment();
		buildAdvancedSegment();


		this.revalidate();
	}

	private void buildAdvancedSegment() {
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

		final String name = "Advanced";
		final String description = "";
		final JLabel sectionName = new JLabel(name);
		sectionName.setForeground(ColorScheme.BRAND_ORANGE);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		sectionName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		final JPanel sectionContent = new JPanel();
		sectionContent.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		sectionContent.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		section.add(sectionContent, BorderLayout.SOUTH);

		mainContentPanel.add(section);

		// Toggle section action listeners
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		toggleSection(sectionToggle, sectionContent);

//		JPanel piperFileChoosePanel = buildPiperFileChoose();
//		sectionContent.add(piperFileChoosePanel);

		JPanel piperProcessMonitorPanel = buildPiperProcessMonitorPanel();
		sectionContent.add(piperProcessMonitorPanel);
	}

	@Subscribe
	private void onTextToSpeechStarted(TextToSpeechStarted event) {
		if (!textToSpeech.canSpeakAny()) {
			statusLabel.setText("No Models Enabled");
			statusLabel.setBackground(Color.ORANGE.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Download and enable a model.");
		}
		else {
			statusLabel.setText("Running");
			statusLabel.setBackground(Color.GREEN.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Text to speech is running.");
		}
	}

	@Subscribe
	private void onTextToSpeechStopped(TextToSpeechStopped event) {
		statusLabel.setText("Not running");
		statusLabel.setBackground(Color.DARK_GRAY);
		statusLabel.setForeground(null);
		statusPanel.setToolTipText("Press start to begin text to speech.");
	}

	@Subscribe
	private void onSpeechEngineStarted(SpeechEngineStarted event) {
		if (event.getSpeechEngine() instanceof PiperEngine) {
			piperMonitorPanel.setVisible(true);
		}
	}

	@Subscribe
	private void onSpeechEngineStopped(SpeechEngineStopped event) {
		if (event.getSpeechEngine() instanceof PiperEngine) {
			piperMonitorPanel.setVisible(false);
		}
	}

	@Subscribe
	private void onPiperModelStarted(PiperModelStarted event) {
		PiperModelMonitorItem piperItem = new PiperModelMonitorItem(event.getPiper());
		piperModelMonitorMap.put(event.getPiper(), piperItem);
		piperMonitorPanel.add(piperItem);
		piperMonitorPanel.revalidate();
	}

	@Subscribe
	private void onPiperModelStopped(PiperModelStopped event) {
		PiperModelMonitorItem remove = piperModelMonitorMap.remove(event.getPiper());
		if (remove != null) {
			piperMonitorPanel.remove(remove);
			piperMonitorPanel.revalidate();
		}
	}

	@Subscribe
	private void onPiperPathChanged(PiperPathChanged event) {
		log.debug("Repository refresh. Rebuilding");
		for (PiperModelItem listItem : piperModelMap.values()) {
			listItem.rebuild();
		}
		SwingUtilities.invokeLater(this::revalidate);
	}

	@Subscribe
	private void onPiperRepositoryChanged(PiperRepositoryChanged event) {
		PiperModelItem modelItem = piperModelMap.get(event.getModelName());
		if (modelItem != null) {
			modelItem.rebuild();
		}
		else {
			log.error(
				"No UI item for {}, MainSettingsPanel currently assumes PiperRepository retain same ModelURLs during runtime.",
				event.getModelName());
		}
	}


	public void buildHeaderSegment() {
		JLabel titleLabel = new JLabel("Natural Speech", JLabel.CENTER);
		titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
		titleLabel.setBorder(new EmptyBorder(1, 0, 1, 0));
		mainContentPanel.add(titleLabel);

		// Instructions Link
		JLabel instructionsLink =
			new JLabel("<html>For instructions, click <a href='#'>here</a>.</html>", JLabel.CENTER);
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

		final String name = "Voice Packs";
		final String description = "Download and manage your voice models.";
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

		// Toggle section action listeners
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		for (PiperRepository.ModelURL modelUrl : piperRepository.getModelURLS()) {

			PiperModelItem modelItem = new PiperModelItem(textToSpeech, piperEngine, piperRepository, modelUrl);
			piperModelMap.put(modelUrl.getModelName(), modelItem);
			sectionContent.add(modelItem);
		}

		// Sapi4 Model
		List<String> sapi4Models = sapi4Repository.getVoices();
		if (!sapi4Models.isEmpty()) {
			sectionContent.add(new SAPI4ListItem(), 0);
		}

		if (!sapi5Engine.getAvailableSAPI5s().isEmpty()) {
			sectionContent.add(new SAPI5ListItem(), 0);
		}

	}

	public void buildPiperStatusSegment() {
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

		final String name = "Status";
		final String description = "";
		final JLabel sectionName = new JLabel(name);
		sectionName.setForeground(ColorScheme.BRAND_ORANGE);
		sectionName.setFont(FontManager.getRunescapeBoldFont());
		sectionName.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		sectionHeader.add(sectionName, BorderLayout.CENTER);

		final JPanel sectionContent = new JPanel();
		sectionContent.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		sectionContent.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)
		));
		section.add(sectionContent, BorderLayout.SOUTH);
		mainContentPanel.add(section);

		// Toggle section action listeners
		final MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleSection(sectionToggle, sectionContent);
			}
		};
		sectionToggle.addActionListener(actionEvent -> toggleSection(sectionToggle, sectionContent));
		sectionName.addMouseListener(adapter);
		sectionHeader.addMouseListener(adapter);

		// Status Label with dynamic background color
		JPanel statusPanel = buildStatusPanel();
		sectionContent.add(statusPanel);

	}

	private JPanel buildPiperProcessMonitorPanel() {
		piperMonitorPanel = new JPanel();
		piperMonitorPanel.setLayout(new DynamicGridLayout(0, 1, 0, 2));
		piperMonitorPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
		piperMonitorPanel.setVisible(false);

		JLabel header = new JLabel("Piper Process Monitor");
		header.setForeground(Color.WHITE);

		piperMonitorPanel.add(header);

		return piperMonitorPanel;
	}

	private JPanel buildStatusPanel() {
		statusPanel = new JPanel();
		statusPanel.setLayout(new BorderLayout());
		statusPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		statusLabel = new JLabel("Not Running", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Sans", Font.BOLD, 20));
		statusLabel.setOpaque(true); // Needed to show background color
		statusLabel.setPreferredSize(new Dimension(statusLabel.getWidth(), 50)); // Set preferred height
		statusLabel.setBackground(Color.DARK_GRAY);
		statusPanel.setToolTipText("Press start to begin text to speech.");

		statusPanel.add(statusLabel, BorderLayout.NORTH);

		//		textToSpeech.addTextToSpeechListener(
		//			new TextToSpeech.TextToSpeechListener() {
		//				@Override
		//				public void onStart() {
		//					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
		//					// not just one starting piper
		//					if (textToSpeech.isStarted() && !textToSpeech.canSpeakAny()) {
		//						statusLabel.setText("No Models Enabled");
		//						statusLabel.setBackground(Color.ORANGE.darker());
		//						statusLabel.setForeground(Color.WHITE);
		//						statusPanel.setToolTipText("Download and enable a model.");
		//					} else {
		//						statusLabel.setText("Running");
		//						statusLabel.setBackground(Color.GREEN.darker());
		//						statusLabel.setForeground(Color.WHITE);
		//						statusPanel.setToolTipText("Text to speech is running.");
		//					}
		//
		//				}
		//
		//				@Override
		//				public void onStop() {
		//					statusLabel.setText("Not running");
		//					statusLabel.setBackground(Color.DARK_GRAY);
		//					statusLabel.setForeground(null);
		//					statusPanel.setToolTipText("Press start to begin text to speech.");
		//				}
		//
		//				@Override
		//				public void onPiperExit(PiperModel piper) {
		//					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
		//					// not just one starting piper
		//					if (textToSpeech.isStarted() && !textToSpeech.canSpeakAny()) {
		//						// Detect if this was an unintended exit, because the model would still be enabled
		//						if (textToSpeech.getModelConfig().isModelEnabled(piper.getModelLocal().getModelName())) {
		//							statusLabel.setText("Crashed (Contact Us)");
		//							statusLabel.setBackground(Color.RED.darker());
		//							statusLabel.setForeground(Color.WHITE);
		//							statusPanel.setToolTipText("Please contact the developers for support.");
		//						} else {
		//							statusLabel.setText("No Models Enabled");
		//							statusLabel.setBackground(Color.ORANGE.darker());
		//							statusLabel.setForeground(Color.WHITE);
		//							statusPanel.setToolTipText("Download and enable a model.");
		//						}
		//					}
		//				}
		//
		//				@Override
		//				public void onPiperInvalid() {
		//					statusLabel.setText("Piper Path Invalid");
		//					statusLabel.setBackground(Color.RED.darker().darker().darker());
		//					statusLabel.setForeground(Color.WHITE);
		//					statusPanel.setToolTipText("Please contact the developers for support.");
		//				}
		//			}
		//		);

		// Button Panel
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Align buttons in the center

		// Initialize buttons with icons
		JButton playButton = createButton("start.png", "Start");
		JButton stopButton = createButton("stop.png", "Stop");

		playButton.addActionListener(e -> {textToSpeech.start();});
		stopButton.addActionListener(e -> {textToSpeech.stop();});

		buttonPanel.add(playButton);
		buttonPanel.add(stopButton);
		statusPanel.add(buttonPanel, BorderLayout.CENTER);
		return statusPanel;
	}

//	private JPanel buildPiperFileChoose() {
//		JLabel header = new JLabel("Natural Speech Location");
//		header.setForeground(Color.WHITE);
//
//		JTextField filePathField = new JTextField(runtimeConfig.getPiperPath().toString());
//		filePathField.setToolTipText("Piper binary file path");
//		filePathField.setEditable(false);
//
//		JButton browseButton = new JButton("Browse");
//		browseButton.setToolTipText("Requires manual download, please read instructions.");
//		browseButton.addActionListener(e -> {
//			// open in drive top path
//			JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
//			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
//			int returnValue = fileChooser.showOpenDialog(MainSettingsPanel.this);
//			if (returnValue == JFileChooser.APPROVE_OPTION) {
//				Path newPath = Path.of(fileChooser.getSelectedFile().getPath());
//
//				// if the user accidentally set the piper folder and not the executable, automatically correct
//				if (newPath.toFile().isDirectory()) {
//					if (OSValidator.IS_WINDOWS) {
//						newPath = newPath.resolve("piper.exe");
//					}
//					else { // assume unix based
//						newPath = newPath.resolve("piper");
//					}
//				}
//
//				filePathField.setText(newPath.toString());
//				runtimeConfig.savePiperPath(newPath);
//
//				// if text to speech is running, restart
//				if (textToSpeech.isStarted()) {
//					textToSpeech.stop();
//				}
//
//			}
//
//		});
//
//		JPanel fileBrowsePanel = new JPanel(new BorderLayout());
//		fileBrowsePanel.setBorder(new EmptyBorder(5, 0, 0, 0));
//		fileBrowsePanel.add(header, BorderLayout.NORTH);
//		fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
//		fileBrowsePanel.add(browseButton, BorderLayout.SOUTH);
//		return fileBrowsePanel;
//	}

	private void toggleSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState ? "Retract" : "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	private JButton createButton(String iconPath, String toolTipText) {
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
		JButton button = new JButton(new ImageIcon(icon));
		button.setToolTipText(toolTipText);
		return button;
	}

	public void shutdown() {
		//		this.removeAll();
		//		for (PiperRepository.ModelRepositoryListener listener : this.modelRepositoryListeners) {
		//			piperRepository.removeRepositoryChangedListener(listener);
		//		}
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
