package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.enums.SpeechType;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import dev.phyce.naturalspeech.configs.NaturalSpeechRuntimeConfig;
import dev.phyce.naturalspeech.downloader.Downloader;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceManager;
import dev.phyce.naturalspeech.tts.VoiceSource;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoiceRepository;
import dev.phyce.naturalspeech.ui.components.CollapsibleSection;
import dev.phyce.naturalspeech.utils.OSValidator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
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

	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(6, 6, 6, 6);
	private static final ImageIcon START_ICON =
		new ImageIcon(ImageUtil.loadImageResource(MainSettingsPanel.class, "start.png"));
	private static final ImageIcon STOP_ICON =
		new ImageIcon(ImageUtil.loadImageResource(MainSettingsPanel.class, "stop.png"));
//	private static final Dimension OUTER_PREFERRED_SIZE = new Dimension(242, 0);


	private final ClientThread clientThread;
	private final NaturalSpeechConfig config;
	private final VoiceManager voiceManager;
	private final FixedWidthPanel mainContentPanel;
	private final ModelRepository modelRepository;
	private final TextToSpeech textToSpeech;
	private final NaturalSpeechRuntimeConfig runtimeConfig;
	private final ElevenLabsVoiceRepository elevenLabsVoiceRepository;
	private final List<ModelRepository.ModelRepositoryListener> modelRepositoryListeners;
	private final List<TextToSpeech.TextToSpeechListener> textToSpeechListeners = new ArrayList<>();
	private final Runnable statusListener = () -> SwingUtilities.invokeLater(this::refreshStatus);
	private final Runnable speechGenerationListener = () -> SwingUtilities.invokeLater(this::refreshSpeechGeneration);
	private final JPanel speechGenerationList = new JPanel(new DynamicGridLayout(0, 1, 0, 3));

	private final JLabel enginesStatusLabel = new JLabel("", SwingConstants.CENTER);
	private final JLabel piperStatusLabel = buildStatusLabel();
	private final JButton piperToggleButton = new JButton();
	private final JLabel elevenLabsStatusLabel = buildStatusLabel();
	private final JButton elevenLabsToggleButton = new JButton();
	private final JButton reloadVoicesButton = new JButton("Reload voices");
	private final JButton clearCacheButton = new JButton("Clear cache");
	private CollapsibleSection elevenLabsStatusSection;
	private CollapsibleSection elevenLabsVoicePacksSection;
	private volatile boolean piperPathInvalid = false;

	@Inject
	public MainSettingsPanel(
		NaturalSpeechConfig config,
		ModelRepository modelRepository,
		ConfigManager configManager,
		Downloader downloader, ClientThread clientThread,
		TextToSpeech textToSpeech,
		NaturalSpeechRuntimeConfig runtimeConfig,
		ElevenLabsVoiceRepository elevenLabsVoiceRepository,
		VoiceManager voiceManager
	) {
		super(false);
		this.textToSpeech = textToSpeech;
		this.modelRepository = modelRepository;
		this.clientThread = clientThread;
		this.config = config;
		this.voiceManager = voiceManager;
		this.runtimeConfig = runtimeConfig;
		this.elevenLabsVoiceRepository = elevenLabsVoiceRepository;
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
		buildEnginesSegment();
		buildPiperStatusSection();
		buildVoiceRepositorySegment();
		buildElevenLabsStatusSection();
		buildElevenLabsVoicePacksSection();
		buildSpeechGenerationSection();
//		buildVoiceHistorySegment();

		addTextToSpeechListener(new TextToSpeech.TextToSpeechListener() {
			@Override
			public void onPiperStart(Piper piper) {
				piperPathInvalid = false;
				statusListener.run();
			}

			@Override
			public void onPiperExit(Piper piper) {
				statusListener.run();
			}

			@Override
			public void onPiperInvalid() {
				piperPathInvalid = true;
				statusListener.run();
			}

			@Override
			public void onStart() {
				statusListener.run();
			}

			@Override
			public void onStop() {
				statusListener.run();
			}

			@Override
			public void onElevenLabsStart() {
				statusListener.run();
			}

			@Override
			public void onElevenLabsStop() {
				statusListener.run();
			}
		});
		elevenLabsVoiceRepository.addListener(statusListener);
		elevenLabsVoiceRepository.addListener(speechGenerationListener);

		refreshStatus();
		refreshSpeechGeneration();
		this.revalidate();
	}

	private void buildVoiceHistorySegment() {
		buildSection("History", "The history of played voices played.", true);
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

	private void buildEnginesSegment() {
		JButton startButton = createButton("start.png", "Start all engines");
		JButton stopButton = createButton("stop.png", "Stop all engines");
		startButton.addActionListener(e -> clientThread.invokeLater(textToSpeech::startAll));
		stopButton.addActionListener(e -> clientThread.invokeLater(textToSpeech::stopAll));

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		enginesStatusLabel.setBorder(new EmptyBorder(0, 0, 5, 0));

		JPanel enginesPanel = new JPanel(new BorderLayout());
		enginesPanel.add(buttonPanel, BorderLayout.CENTER);
		enginesPanel.add(enginesStatusLabel, BorderLayout.SOUTH);
		mainContentPanel.add(enginesPanel);
	}

	public void buildVoiceRepositorySegment() {
		CollapsibleSection section =
			buildSection("Piper Voice Packs", "Download and manage your piper voice models.", false);

		List<ModelRepository.ModelURL> modelURLS = modelRepository.getModelURLS();
		for (ModelRepository.ModelURL modelUrl : modelURLS) {
			ModelListItem listItem = new ModelListItem(textToSpeech, modelRepository, modelUrl);
			section.getContent().add(listItem);

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

		ModelRepository.ModelRepositoryListener speechGenerationRepoListener = new ModelRepository.ModelRepositoryListener() {
			@Override
			public void onRepositoryChanged(String modelName) {
				speechGenerationListener.run();
			}

			@Override
			public void onRefresh() {
				speechGenerationListener.run();
			}
		};
		modelRepository.addRepositoryChangedListener(speechGenerationRepoListener);
		this.modelRepositoryListeners.add(speechGenerationRepoListener);
	}

	public void buildPiperStatusSection() {
		CollapsibleSection section = buildSection("Piper Status", "Manage your piper instances.", true);

		piperToggleButton.addActionListener(e -> clientThread.invokeLater(() -> {
			if (textToSpeech.isPiperStarted()) {
				textToSpeech.stopPiper();
			}
			else {
				textToSpeech.startPiper();
			}
		}));
		section.getContent().add(buildStatusRow(piperStatusLabel, piperToggleButton));

		JPanel piperFileChoosePanel = buildPiperFileChoose();
		section.getContent().add(piperFileChoosePanel);

		JPanel piperProcessMonitorPanel = buildPiperProcessMonitorPanel();
		section.getContent().add(piperProcessMonitorPanel);
	}

	private void buildElevenLabsStatusSection() {
		elevenLabsStatusSection =
			buildSection("ElevenLabs Status", "Manage your ElevenLabs connection.", true);

		elevenLabsToggleButton.addActionListener(e -> clientThread.invokeLater(() -> {
			if (textToSpeech.isElevenLabsStarted()) {
				textToSpeech.stopElevenLabs();
			}
			else {
				textToSpeech.startElevenLabs();
			}
		}));
		elevenLabsStatusSection.getContent().add(buildStatusRow(elevenLabsStatusLabel, elevenLabsToggleButton));

		reloadVoicesButton.addActionListener(e -> textToSpeech.refreshElevenLabsVoices());

		clearCacheButton.setToolTipText("Delete all cached ElevenLabs audio");
		clearCacheButton.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(this, "Delete all cached ElevenLabs audio?", "Clear cache",
				JOptionPane.YES_NO_OPTION);
			if (result == JOptionPane.YES_OPTION) {
				textToSpeech.clearElevenLabsCache();
			}
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.add(reloadVoicesButton);
		buttonPanel.add(clearCacheButton);
		elevenLabsStatusSection.getContent().add(buttonPanel);
	}

	private void buildElevenLabsVoicePacksSection() {
		elevenLabsVoicePacksSection =
			buildSection("ElevenLabs Voice Packs", "Choose which ElevenLabs models to use.", false);

		for (ElevenLabsModel model : ElevenLabsModel.values()) {
			elevenLabsVoicePacksSection.getContent().add(buildElevenLabsModelItem(model));
		}
	}

	private void buildSpeechGenerationSection() {
		CollapsibleSection section =
			buildSection("Speech Generation", "Choose the engine or model that speaks each message type.", true);
		speechGenerationList.setOpaque(false);
		section.getContent().add(speechGenerationList);
	}

	private void refreshSpeechGeneration() {
		speechGenerationList.removeAll();
		List<VoiceSource> sources = availableSources();

		List<SpeechType> enabledTypes = Arrays.stream(SpeechType.values())
			.filter(speechType -> speechType.isEnabled(config))
			.collect(Collectors.toList());
		for (SpeechType speechType : enabledTypes) {
			speechGenerationList.add(new JLabel(speechType.toString()));

			JComboBox<VoiceSource> box = sourceBox(sources, voiceManager.getSource(speechType));
			box.addActionListener(event -> {
				VoiceSource source = (VoiceSource) box.getSelectedItem();
				if (source != null) voiceManager.setSource(speechType, source);
			});
			speechGenerationList.add(box);
		}

		speechGenerationList.revalidate();
		speechGenerationList.repaint();
	}

	private List<VoiceSource> availableSources() {
		List<VoiceSource> sources = new ArrayList<>();
		sources.add(VoiceSource.PIPER);
		for (ModelRepository.ModelURL modelURL : modelRepository.getModelURLS()) {
			try {
				if (modelRepository.hasModelLocal(modelURL.getModelName())) {
					sources.add(VoiceSource.ofModel(modelURL.getModelName()));
				}
			} catch (IOException ignored) {
			}
		}

		if (elevenLabsVoiceRepository.hasApiKey()) {
			sources.add(VoiceSource.ELEVENLABS);
			for (ElevenLabsModel model : elevenLabsVoiceRepository.getEnabledModels()) {
				sources.add(VoiceSource.ofModel(model.getId()));
			}
		}
		return sources;
	}

	private static JComboBox<VoiceSource> sourceBox(List<VoiceSource> sources, VoiceSource selected) {
		List<VoiceSource> options = new ArrayList<>(sources);
		if (!options.contains(selected)) options.add(selected);

		JComboBox<VoiceSource> box = new JComboBox<>(options.toArray(new VoiceSource[0]));
		box.setSelectedItem(selected);
		return box;
	}

	private JPanel buildElevenLabsModelItem(ElevenLabsModel model) {
		JLabel name = new JLabel(model.toString());
		name.setFont(FontManager.getRunescapeBoldFont());

		JLabel description = new JLabel(model.getDescription());

		JPanel textPanel = new JPanel(new GridLayout(0, 1));
		textPanel.setOpaque(false);
		textPanel.add(name);
		textPanel.add(description);

		JToggleButton toggleButton = new JToggleButton();
		SwingUtil.removeButtonDecorations(toggleButton);
		toggleButton.setIcon(ModelListItem.OFF_SWITCHER);
		toggleButton.setSelectedIcon(ModelListItem.ON_SWITCHER);
		toggleButton.setPreferredSize(new Dimension(25, 0));
		toggleButton.setSelected(elevenLabsVoiceRepository.isModelEnabled(model));
		toggleButton.addActionListener(e -> elevenLabsVoiceRepository.setModelEnabled(model, toggleButton.isSelected()));

		JPanel item = new JPanel(new BorderLayout(5, 0));
		item.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		item.setBorder(new EmptyBorder(5, 5, 5, 5));
		item.add(textPanel, BorderLayout.CENTER);
		item.add(toggleButton, BorderLayout.EAST);
		return item;
	}

	private void refreshStatus() {
		refreshPiperStatus();
		refreshElevenLabsStatus();
		refreshEnginesStatus();
		mainContentPanel.revalidate();
		mainContentPanel.repaint();
	}

	private void refreshEnginesStatus() {
		List<SpeechEngine> engines = new ArrayList<>(List.of(SpeechEngine.PIPER));
		if (elevenLabsVoiceRepository.hasApiKey()) engines.add(SpeechEngine.ELEVENLABS);

		long running = engines.stream().filter(textToSpeech::isEngineRunning).count();

		String status;
		if (running == engines.size()) {
			status = "All engines running";
		}
		else if (running == 0) {
			status = "No engines running";
		}
		else {
			status = running + "/" + engines.size() + " engines running";
		}
		enginesStatusLabel.setText("<html><body style='width:150px'><center>" + status + "</center></body></html>");
	}

	private void refreshPiperStatus() {
		if (piperPathInvalid) {
			setStatus(piperStatusLabel, "Piper Path Invalid", Color.RED.darker().darker().darker(),
				"Please contact the developers for support.");
		}
		else if (textToSpeech.activePiperProcessCount() > 0) {
			setStatus(piperStatusLabel, "Running", Color.GREEN.darker(), "Text to speech is running.");
		}
		else if (!textToSpeech.isPiperStarted()) {
			setStatus(piperStatusLabel, "Not running", Color.DARK_GRAY, "Press start to begin text to speech.");
		}
		else if (isAnyPiperModelEnabled()) {
			setStatus(piperStatusLabel, "Crashed (Contact Us)", Color.RED.darker(),
				"Please contact the developers for support.");
		}
		else {
			setStatus(piperStatusLabel, "No Models Enabled", Color.ORANGE.darker(), "Download and enable a model.");
		}
		setToggle(piperToggleButton, textToSpeech.isPiperStarted(), "Piper");
	}

	private boolean isAnyPiperModelEnabled() {
		return modelRepository.getModelURLS().stream()
			.anyMatch(modelURL -> textToSpeech.getModelConfig().isModelEnabled(modelURL.getModelName()));
	}

	private void refreshElevenLabsStatus() {
		boolean hasApiKey = elevenLabsVoiceRepository.hasApiKey();
		elevenLabsStatusSection.setVisible(hasApiKey);
		elevenLabsVoicePacksSection.setVisible(hasApiKey);

		boolean started = textToSpeech.isElevenLabsStarted();
		boolean loading = elevenLabsVoiceRepository.isLoading();
		int voiceCount = elevenLabsVoiceRepository.getVoices().size();
		String error = elevenLabsVoiceRepository.getError();

		if (!started) {
			setStatus(elevenLabsStatusLabel, "Not started", Color.DARK_GRAY, "Press start to use ElevenLabs.");
		}
		else if (loading) {
			setStatus(elevenLabsStatusLabel, "Loading", Color.DARK_GRAY, "Loading your ElevenLabs voices.");
		}
		else if (error != null) {
			setStatus(elevenLabsStatusLabel, "Error", Color.RED.darker(), error);
		}
		else if (voiceCount == 0) {
			setStatus(elevenLabsStatusLabel, "No Voices", Color.ORANGE.darker(), "Add voices to your ElevenLabs library.");
		}
		else if (elevenLabsVoiceRepository.getEnabledModels().isEmpty()) {
			setStatus(elevenLabsStatusLabel, "No Models Enabled", Color.ORANGE.darker(),
				"Enable a model in ElevenLabs Voice Packs.");
		}
		else {
			setStatus(elevenLabsStatusLabel, "Ready", Color.GREEN.darker(),
				voiceCount + " ElevenLabs voices available.");
		}
		setToggle(elevenLabsToggleButton, started, "ElevenLabs");
		reloadVoicesButton.setEnabled(started && !loading);
		clearCacheButton.setEnabled(started);
	}

	private static JLabel buildStatusLabel() {
		JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Sans", Font.BOLD, 16));
		statusLabel.setOpaque(true);
		statusLabel.setPreferredSize(new Dimension(0, 50));
		return statusLabel;
	}

	private static JPanel buildStatusRow(JLabel statusLabel, JButton toggleButton) {
		JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
		statusPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
		statusPanel.add(statusLabel, BorderLayout.CENTER);
		statusPanel.add(toggleButton, BorderLayout.EAST);
		return statusPanel;
	}

	private static void setStatus(JLabel statusLabel, String text, Color background, String tooltip) {
		statusLabel.setText(text);
		statusLabel.setBackground(background);
		statusLabel.setForeground(background == Color.DARK_GRAY ? null : Color.WHITE);
		statusLabel.setToolTipText(tooltip);
	}

	private static void setToggle(JButton toggleButton, boolean started, String engineName) {
		toggleButton.setIcon(started ? STOP_ICON : START_ICON);
		toggleButton.setToolTipText((started ? "Stop " : "Start ") + engineName);
	}

	private CollapsibleSection buildSection(String name, String description, boolean expanded) {
		CollapsibleSection section =
			new CollapsibleSection(name, description, new DynamicGridLayout(0, 1, 0, 5), expanded);
		mainContentPanel.add(section);
		return section;
	}

	private JPanel buildPiperProcessMonitorPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new DynamicGridLayout(0, 1, 0, 2));
		panel.setBorder(new EmptyBorder(5, 0, 5, 0));

		addTextToSpeechListener(
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
				if (textToSpeech.isPiperStarted()) {
					textToSpeech.stopPiper();
				}

				piperPathInvalid = false;
				refreshStatus();
			}

		});

		JPanel fileBrowsePanel = new JPanel(new BorderLayout());
		fileBrowsePanel.setBorder(new EmptyBorder(0, 0, 5, 0));
		fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
		fileBrowsePanel.add(browseButton, BorderLayout.SOUTH);
		return fileBrowsePanel;
	}

	private JButton createButton(String iconPath, String toolTipText) {
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
		JButton button = new JButton(new ImageIcon(icon));
		button.setToolTipText(toolTipText);
		return button;
	}

	private void addTextToSpeechListener(TextToSpeech.TextToSpeechListener listener) {
		textToSpeech.addTextToSpeechListener(listener);
		textToSpeechListeners.add(listener);
	}

	public void shutdown() {
		this.removeAll();
		for (ModelRepository.ModelRepositoryListener listener : this.modelRepositoryListeners) {
			modelRepository.removeRepositoryChangedListener(listener);
		}
		for (TextToSpeech.TextToSpeechListener listener : textToSpeechListeners) {
			textToSpeech.removeTextToSpeechListener(listener);
		}
		elevenLabsVoiceRepository.removeListener(statusListener);
		elevenLabsVoiceRepository.removeListener(speechGenerationListener);
	}

	@Override
	public void onActivate() {
		super.onActivate();
		refreshSpeechGeneration();
	}
}
