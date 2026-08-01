package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig.ConfigKeys;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeech;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeechEngine;
import dev.phyce.naturalspeech.utils.OSValidator;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
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


	private final ClientThread clientThread;
	private final FixedWidthPanel mainContentPanel;
	private final ModelRepository modelRepository;
	private final TextToSpeech textToSpeech;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final ConfigManager configManager;
	private final List<ModelRepository.ModelRepositoryListener> modelRepositoryListeners;

	@Inject
	public MainSettingsPanel(
		NaturalSpeechConfig config,
		ModelRepository modelRepository,
		ConfigManager configManager,
		Downloader downloader, ClientThread clientThread,
		TextToSpeech textToSpeech,
		NaturalSpeechRuntimeConfig runtimeConfig
	) {
		super(false);
		this.textToSpeech = textToSpeech;
		this.modelRepository = modelRepository;
		this.clientThread = clientThread;
		this.runtimeConfig = runtimeConfig;
		this.configManager = configManager;
		this.modelRepositoryListeners = new ArrayList<>();

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
		buildNativeSpeechSection();
		buildPiperStatusSection();
		buildVoiceRepositorySegment();
//		buildVoiceHistorySegment();

		this.revalidate();
	}

	private void buildVoiceHistorySegment() {
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

		final String name = "History";
		final String description = "The history of played voices played.";
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
					LinkBrowser.browse("https://github.com/phyce/rl-natural-speech/tree/master-1.2");
				} catch (Exception ex) {
					log.error("Error opening instruction link.", ex);
				}
			}
		});
		instructionsLink.setBorder(new EmptyBorder(0, 0, 5, 0));
		mainContentPanel.add(instructionsLink);

		// Discord Link
		JLabel discordLink =
			new JLabel("<html>Need help? Ask on our <a style=\"color:#7289da\" href='#'>Discord</a></html>", JLabel.CENTER);
		discordLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		discordLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					LinkBrowser.browse("https://discord.gg/smhQRcyXVU");
				} catch (Exception ex) {
					log.error("Error opening Discord link.", ex);
				}
			}
		});
		discordLink.setBorder(new EmptyBorder(0, 0, 5, 0));
		mainContentPanel.add(discordLink);

	}

	public void buildVoiceRepositorySegment() {
		final JPanel sectionContent =
			buildSection("Piper Voice Packs", "Download and manage your piper voice models.");

		List<ModelRepository.ModelURL> modelURLS = modelRepository.getModelURLS();
		for (ModelRepository.ModelURL modelUrl : modelURLS) {
			ModelListItem listItem = new ModelListItem(textToSpeech, modelRepository, modelUrl);
			sectionContent.add(listItem);

			ModelRepository.ModelRepositoryListener modelRepoListener = new ModelRepository.ModelRepositoryListener() {
				@Override
				public void onRepositoryChanged(String modelName) {
					SwingUtilities.invokeLater(() -> {
						log.debug("Repository change detected. Rebuilding {}", modelName);
						listItem.rebuild();
						revalidate();
					});
				}

				@Override
				public void onRefresh() {
					SwingUtilities.invokeLater(() -> {
						log.debug("Repository refresh. Rebuilding");
						listItem.rebuild();
						revalidate();
					});
				}
			};
			modelRepository.addRepositoryChangedListener(modelRepoListener);
			this.modelRepositoryListeners.add(modelRepoListener);
		}
	}

	private JPanel buildSection(String name, String description) {
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

		return sectionContent;
	}

	public void buildPiperStatusSection() {
		JPanel sectionContent = buildSection("Piper Status", "Manage your piper instances.");

		// Status Label with dynamic background color
		sectionContent.add(buildPiperStatusPanel());
		sectionContent.add(buildPiperFileChoose());
		sectionContent.add(buildPiperProcessMonitorPanel());
	}

	public void buildNativeSpeechSection() {
		JPanel sectionContent = buildSection("System Voices",
			"The voices built into your operating system. No piper install needed.");

		sectionContent.add(buildNativeSpeechStatusPanel());
		sectionContent.add(buildNativeVoicePackPanel());
	}

	private JPanel buildNativeVoicePackPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		JLabel nameLabel = new JLabel(OSValidator.IS_MAC ? "macOS voices" : "Windows voices");
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		panel.add(nameLabel, BorderLayout.WEST);

		JLabel countLabel = new JLabel();
		panel.add(countLabel, BorderLayout.EAST);

		Runnable refresh = () -> SwingUtilities.invokeLater(() -> {
			NativeSpeechEngine engine = textToSpeech.getNativeSpeechEngine();
			int voices = engine == null ? 0 : engine.getVoices().size();
			countLabel.setText(voices == 0 ? "-" : voices + " voices");
			panel.setToolTipText("Voices available on your system");
		});
		refresh.run();

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				@Override
				public void onNativeSpeechStart(NativeSpeechEngine engine) {
					refresh.run();
				}

				@Override
				public void onNativeSpeechExit(NativeSpeechEngine engine) {
					refresh.run();
				}
			}
		);

		return panel;
	}

	private JPanel buildNativeSpeechStatusPanel() {
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BorderLayout());
		statusPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		JLabel statusLabel = new JLabel("Not Running", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Sans", Font.BOLD, 20));
		statusLabel.setOpaque(true); // Needed to show background color
		statusLabel.setPreferredSize(new Dimension(statusLabel.getWidth(), 50));
		statusPanel.add(statusLabel, BorderLayout.NORTH);

		Runnable refresh = () -> SwingUtilities.invokeLater(() -> {
			NativeSpeechEngine engine = textToSpeech.getNativeSpeechEngine();

			if (engine != null && engine.isAlive()) {
				int voices = engine.getVoices().size();
				statusLabel.setText("Running");
				statusLabel.setBackground(Color.GREEN.darker());
				statusLabel.setForeground(Color.WHITE);
				statusPanel.setToolTipText(voices + " system voice(s) available.");
			}
			else if (!NativeSpeech.isSupported()) {
				statusLabel.setText("Not Supported");
				statusLabel.setBackground(Color.DARK_GRAY);
				statusLabel.setForeground(null);
				statusPanel.setToolTipText("This operating system has no built-in voices wired up yet.");
			}
			else {
				statusLabel.setText("Not Running");
				statusLabel.setBackground(Color.DARK_GRAY);
				statusLabel.setForeground(null);
				statusPanel.setToolTipText("Press start to use the voices built into your system.");
			}
		});
		refresh.run();

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				@Override
				public void onNativeSpeechStart(NativeSpeechEngine engine) {
					refresh.run();
				}

				@Override
				public void onNativeSpeechExit(NativeSpeechEngine engine) {
					refresh.run();
				}
			}
		);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

		JButton playButton = createButton("start.png", "Start");
		JButton stopButton = createButton("stop.png", "Stop");

		// writes the setting rather than starting directly, so the buttons and the checkbox agree
		playButton.addActionListener(e ->
			configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.NATIVE_SPEECH, true));
		stopButton.addActionListener(e ->
			configManager.setConfiguration(CONFIG_GROUP, ConfigKeys.NATIVE_SPEECH, false));

		buttonPanel.add(playButton);
		buttonPanel.add(stopButton);
		statusPanel.add(buttonPanel, BorderLayout.CENTER);
		return statusPanel;
	}

	private JPanel buildPiperProcessMonitorPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new DynamicGridLayout(0, 1, 0, 2));
		panel.setBorder(new EmptyBorder(5, 0, 5, 0));

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				private final Map<Piper, PiperListItem> piperItemList = new HashMap<>();

				@Override
				public void onPiperStart(Piper piper) {
					PiperListItem piperItem = new PiperListItem(piper);
					piperItemList.put(piper, piperItem);
					panel.add(piperItem);
					panel.revalidate();
				}

				@Override
				public void onPiperExit(Piper piper) {
					PiperListItem remove = piperItemList.remove(piper);
					if (remove != null) {
						panel.remove(remove);
						panel.revalidate();
					}
				}
			}
		);
		return panel;
	}

	private JPanel buildPiperStatusPanel() {
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BorderLayout());
		statusPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		JLabel statusLabel = new JLabel("Not Running", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Sans", Font.BOLD, 20));
		statusLabel.setOpaque(true); // Needed to show background color
		statusLabel.setPreferredSize(new Dimension(statusLabel.getWidth(), 50)); // Set preferred height
		statusLabel.setBackground(Color.DARK_GRAY);
		statusPanel.setToolTipText("Press start to begin text to speech.");

		statusPanel.add(statusLabel, BorderLayout.NORTH);

		textToSpeech.addTextToSpeechListener(
			new TextToSpeech.TextToSpeechListener() {
				@Override
				public void onPiperStart(Piper piper) {
					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
					// not just one starting piper
					statusLabel.setText("Running");
					statusLabel.setBackground(Color.GREEN.darker());
					statusLabel.setForeground(Color.WHITE);
					statusPanel.setToolTipText("Text to speech is running.");
				}

				@Override
				public void onPiperExit(Piper piper) {
					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
					// not just one starting piper
					if (textToSpeech.isStarted() && textToSpeech.activePiperProcessCount() == 0) {
						// Detect if this was an unintended exit, because the model would still be enabled
						if (textToSpeech.getModelConfig().isModelEnabled(piper.getModelLocal().getModelName())) {
							statusLabel.setText("Crashed (Contact Us)");
							statusLabel.setBackground(Color.RED.darker());
							statusLabel.setForeground(Color.WHITE);
							statusPanel.setToolTipText("Please contact the developers for support.");
						} else {
							statusLabel.setText("No Models Enabled");
							statusLabel.setBackground(Color.ORANGE.darker());
							statusLabel.setForeground(Color.WHITE);
							statusPanel.setToolTipText("Download and enable a model.");
						}
					}
				}

				@Override
				public void onPiperInvalid() {
					statusLabel.setText("Piper Path Invalid");
					statusLabel.setBackground(Color.RED.darker().darker().darker());
					statusLabel.setForeground(Color.WHITE);
					statusPanel.setToolTipText("Please contact the developers for support.");
				}

				@Override
				public void onStart() {
					// FIXME(Louis) Temporary just for testing. Should check if any pipers are running,
					// not just one starting piper
					if (textToSpeech.isStarted() &&
						textToSpeech.activePiperProcessCount() == 0) {
						statusLabel.setText("No Models Enabled");
						statusLabel.setBackground(Color.ORANGE.darker());
						statusLabel.setForeground(Color.WHITE);
						statusPanel.setToolTipText("Download and enable a model.");
					}

				}

				@Override
				public void onStop() {
					statusLabel.setText("Not running");
					statusLabel.setBackground(Color.DARK_GRAY);
					statusLabel.setForeground(null);
					statusPanel.setToolTipText("Press start to begin text to speech.");
				}
			}
		);

		// Button Panel
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Align buttons in the center

		// Initialize buttons with icons
		JButton playButton = createButton("start.png", "Start");
		JButton stopButton = createButton("stop.png", "Stop");

		playButton.addActionListener(e -> {
			clientThread.invokeLater(() -> {
				textToSpeech.start();
			});
		});
		stopButton.addActionListener(e -> {
			clientThread.invokeLater(() -> {
				textToSpeech.stopPiper();
			});
		});

		buttonPanel.add(playButton);
		buttonPanel.add(stopButton);
		statusPanel.add(buttonPanel, BorderLayout.CENTER);
		return statusPanel;
	}

	private JPanel buildPiperFileChoose() {
		JTextField filePathField = new JTextField(runtimeConfig.getPiperPath().toString());
		filePathField.setToolTipText("Piper binary file path");
		filePathField.setEditable(false);

		JButton browseButton = new JButton("Browse");
		browseButton.setToolTipText("Requires manual download, please read instructions.");
		browseButton.addActionListener(e -> {
			// open in drive top path
			JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			int returnValue = fileChooser.showOpenDialog(MainSettingsPanel.this);
			if (returnValue == JFileChooser.APPROVE_OPTION) {
				Path newPath = Path.of(fileChooser.getSelectedFile().getPath());

				// if the user accidentally set the piper folder and not the executable, automatically correct
				if (newPath.toFile().isDirectory()) {
					if (OSValidator.IS_WINDOWS) {
						newPath = newPath.resolve("piper.exe");
					} else { // assume unix based
						newPath = newPath.resolve("piper");
					}
				}

				filePathField.setText(newPath.toString());
				runtimeConfig.savePiperPath(newPath);
				modelRepository.refresh();

				// if text to speech is running, restart
				if (textToSpeech.isStarted()) {
					textToSpeech.stop();
				}

			}

		});

		JPanel fileBrowsePanel = new JPanel(new BorderLayout());
		fileBrowsePanel.setBorder(new EmptyBorder(0, 0, 5, 0));
		fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
		fileBrowsePanel.add(browseButton, BorderLayout.SOUTH);
		return fileBrowsePanel;
	}

	private void toggleSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState? SECTION_RETRACT_ICON: SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState? "Retract": "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	private JButton createButton(String iconPath, String toolTipText) {
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
		JButton button = new JButton(new ImageIcon(icon));
		button.setToolTipText(toolTipText);
		return button;
	}

	public void shutdown() {
		this.removeAll();
		for (ModelRepository.ModelRepositoryListener listener : this.modelRepositoryListeners) {
			modelRepository.removeRepositoryChangedListener(listener);
		}
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
