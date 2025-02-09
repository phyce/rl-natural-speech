package dev.phyce.naturalspeech.userinterface.mainsettings;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.eventbus.PluginSubscribe;
import dev.phyce.naturalspeech.events.PiperPathChanged;
import dev.phyce.naturalspeech.events.PiperProcessEvent;
import dev.phyce.naturalspeech.events.PiperRepositoryChanged;
import dev.phyce.naturalspeech.events.SpeechEngineEvent;
import dev.phyce.naturalspeech.events.SpeechManagerEvent;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.engine.PiperEngine;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechEngine;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import dev.phyce.naturalspeech.userinterface.components.FixedWidthPanel;
import dev.phyce.naturalspeech.userinterface.layouts.OnlyVisibleGridLayout;
import dev.phyce.naturalspeech.utils.PlatformUtil;
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
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeListener;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;
import org.checkerframework.common.aliasing.qual.NonLeaked;

@Slf4j
public class MainSettingsPanel extends PluginPanel {
	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(0, 5, 0, 5);
	//	private static final Dimension OUTER_PREFERRED_SIZE = new Dimension(242, 0);

	private final SpeechManager speechManager;
	private final NaturalSpeechConfig config;
	private final FixedWidthPanel mainContentPanel;
	private final Set<Warning> warnings = new HashSet<>();
	private final RuntimePathConfig runtimeConfig;


	private JLabel statusLabel;
	private JPanel statusPanel;
	private JPanel piperMonitorPanel;
	private JPanel warningStopped;
	private JPanel warningNoEngine;
	private JPanel warningCrash;
	private JPanel warningMinimumMode;
	private JLabel crashLabel;
	private JLabel piperProcessDisplay;

	private ChangeListener volumeChangeListener;

	private JSlider volumeSlider;

	@Data
	private static class State {
		boolean anyNoRuntime = false;
		boolean anyNoModel = false;
		boolean anyDisabled = false;
		boolean anyCrashed = false;
		boolean anyExternal = false;
	}

	@NonNull
	@NonLeaked
	private State state = new State();

	@Inject
	public MainSettingsPanel(
		SpeechManager speechManager,
		PluginEventBus pluginEventBus,
		ConfigManager configManager,
		NaturalSpeechConfig config,
		RuntimePathConfig runtimeConfig
	) {
		super(false);
		this.config = config;
		this.speechManager = speechManager;
		this.runtimeConfig = runtimeConfig;

		piperProcessDisplay = new JLabel();


		volumeChangeListener = e -> {
			int newVolume = volumeSlider.getValue();
			configManager.setConfiguration(
				CONFIG_GROUP,
				ConfigKeys.MASTER_VOLUME,
				String.valueOf(newVolume)
			);
		};

		pluginEventBus.registerWeak(this);

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// This panel is where the actual content lives.
		mainContentPanel = new FixedWidthPanel();
		mainContentPanel.setBorder(BORDER_PADDING);
		mainContentPanel.setLayout(new OnlyVisibleGridLayout(0, 1, 0, 5));
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
		buildTextToSpeechStatusSegment();
		buildAdvancedSegment();

		this.revalidate();
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		switch (event.getKey()) {
			case ConfigKeys.MASTER_VOLUME:
				updateVolumeSlider(config.masterVolume());
				break;
		}
	}

	@PluginSubscribe
	public void on(SpeechEngineEvent event) {
		clearWarning();
		switch (event.getEvent()) {
			case STARTING:
				if (event.getSpeechEngine() instanceof PiperEngine) {
					piperMonitorPanel.setVisible(true);
				}
				break;

			case START_NO_RUNTIME:
				state.anyNoRuntime = true;
				break;

			case START_NO_MODEL:
				state.anyNoModel = true;
				break;

			case START_DISABLED:
				state.anyDisabled = true;
				break;

			case START_CRASHED:
				state.anyCrashed = true;
				break;

			case STARTED:
				if (event.getSpeechEngine().getEngineType() == SpeechEngine.EngineType.EXTERNAL_DEPENDENCY) {
					state.anyExternal = true;

				}
				break;

			case CRASHED:
				break;

			case STOPPED:
				if (event.getSpeechEngine() instanceof PiperEngine) {
					piperMonitorPanel.setVisible(false);
				}
				break;
		}
		updateActivePiperProcessCount();
		updateStatusUI();
		updateWarningsUI();
	}

	@PluginSubscribe
	public void on(SpeechManagerEvent event) {
		switch (event.getEvent()) {
			case STARTING:
				state = new State();

				if(speechManager.enabledVoicePackCount() < 1) {
					statusLabel.setText("No models enabled");
					statusLabel.setBackground(Color.GREEN.darker());
					statusLabel.setForeground(Color.WHITE);
					statusPanel.setToolTipText("Please enable at least one model in the voice hub.");
				} else {
					statusLabel.setText("Starting...");
					statusLabel.setBackground(Color.GREEN.darker().darker().darker());
					statusLabel.setForeground(Color.WHITE.darker());
					statusPanel.setToolTipText("Text to speech is running.");
				}

				clearWarning();
				revalidate();
				break;

			case STARTED:
				updateStatusUI();
				updateWarningsUI();
				break;

			case STOPPED:
				statusLabel.setText("Not running");
				statusLabel.setBackground(Color.DARK_GRAY);
				statusLabel.setForeground(null);
				statusPanel.setToolTipText("Press start to begin text to speech.");

				addWarning(Warning.STOPPED);
				updateWarningsUI();
				break;
		}
	}

	@PluginSubscribe
	public void on(PiperProcessEvent event) {
		if (event.getEvent() != PiperProcessEvent.Events.CRASHED) return;

		addWarning(Warning.CRASHED);
		crashLabel.setText(
			String.format("<html>Oh no! %s has crashed. (piper)</html>",
				event.getModelEngine().getModel().getModelName())
		);
		updateWarningsUI();
		piperMonitorPanel.revalidate();
	}


	@Deprecated(since="1.3.0 We have an installer which installs to a standard location, no more path changes.")
	@PluginSubscribe
	public void on(PiperPathChanged event) {
		updateWarningsUI();
	}

	@PluginSubscribe
	public void on(PiperRepositoryChanged event) {
		updateWarningsUI();
	}

	private void addWarning(Warning warning) {
		warnings.add(warning);
	}

	private void clearWarning() {
		warnings.clear();
	}

	private void updateWarningsUI() {
		if (!state.anyExternal) addWarning(Warning.MINIMUM_MODE);

		warningStopped.setVisible(!warnings.contains(Warning.NO_ENGINE) && !speechManager.isAlive());
		warningNoEngine.setVisible(warnings.contains(Warning.NO_ENGINE));
		warningCrash.setVisible(warnings.contains(Warning.CRASHED));
		warningMinimumMode.setVisible(warnings.contains(Warning.MINIMUM_MODE));

		mainContentPanel.revalidate();
	}

	private void updateStatusUI() {
		if(speechManager.enabledVoicePackCount() < 1) {
			statusLabel.setText("No models enabled");
			statusLabel.setBackground(Color.RED.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Please enable at least one model in the voice hub.");
		} else if (speechManager.isAlive()) {
			statusLabel.setText("Running");
			statusLabel.setBackground(Color.GREEN.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Text to speech is running.");
		}
		else if (state.anyCrashed) {
			statusLabel.setText("Crashed");
			statusLabel.setBackground(Color.RED.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Text to speech is failed to start.");
		}
		else if (state.anyDisabled) {
			statusLabel.setText("Engines Disabled");
			statusLabel.setBackground(Color.RED.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Text to speech is failed to start.");
		}
		else if (state.anyNoModel) {
			// TODO Runtime installed but models missing, for example piper repo empty or Mac has no voices installed
			statusLabel.setText("Missing Models");
			statusLabel.setBackground(Color.RED.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Text to speech is failed to start.");
		}
		else if (state.anyNoRuntime) {
			statusLabel.setText("Not Installed");
			statusLabel.setBackground(Color.RED.darker());
			statusLabel.setForeground(Color.WHITE);
			statusPanel.setToolTipText("Text to speech is failed to start.");
		}
	}

	public void buildHeaderSegment() {
		JLabel titleLabel = new JLabel("Natural Speech", JLabel.CENTER);
		titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
		titleLabel.setBorder(new EmptyBorder(1, 0, 1, 0));
		mainContentPanel.add(titleLabel);

		// Instructions Link
		JLabel instructionsLink =
			new JLabel("<html>For instructions, click <a style=\"color:#dc8a00\"  href='#'>here</a></html>", JLabel.CENTER);

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

		// Discord Link
		JLabel discordLink =
			new JLabel("<html>Ask for help on our <a style=\"color:#7289da\" \" href='#'>Discord</a></html>", JLabel.CENTER);

		discordLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		discordLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					Desktop.getDesktop().browse(new URI("https://discord.gg/smhQRcyXVU"));
				} catch (Exception ex) {
					log.error("Error opening instruction link.", ex);
				}
			}
		});
		discordLink.setBorder(new EmptyBorder(0, 0, 5, 0));
		mainContentPanel.add(discordLink);
		mainContentPanel.add(instructionsLink);

		{
			warningStopped = new JPanel();
			warningStopped.setVisible(false);
			warningStopped.setLayout(new BorderLayout());

			JLabel label =
				new JLabel("<html>Natural Speech is not running</html>",
					SwingConstants.CENTER);
			label.setBorder(new EmptyBorder(5, 5, 5, 5));
			label.setFont(FontManager.getRunescapeFont());
			label.setForeground(Color.BLACK);
			label.setBackground(new Color(0xFFBB33));
			label.setOpaque(true);

			warningStopped.add(label, BorderLayout.CENTER);
		}

		JLabel explainLabel =
			new JLabel(
				"<html>Basic text-to-speech provided by your operating system is available out of the box; " +
					"however, there are additional voice options available.<br><br> " +
					"<a style=\"color:#dc8a00\" href='#'>Click here fore more information</a></html>" +
					"</html>",
				SwingConstants.CENTER);
		explainLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
		explainLabel.setFont(FontManager.getRunescapeFont());
		explainLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		explainLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
		explainLabel.setOpaque(false);
		explainLabel.setVisible(false);
		explainLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					Desktop.getDesktop().browse(new URI("https://github.com/phyce/rl-natural-speech/INSTALLING.md"));
				} catch (Exception ex) {
					log.error("ErrorResult opening website link.", ex);
				}
			}
		});

		{
			warningNoEngine = new JPanel();
			warningNoEngine.setVisible(false);
			warningNoEngine.setLayout(new BorderLayout());

			JLabel warningLabel =
				new JLabel("<html>There are no available voices installed &#9888;</html>", SwingConstants.CENTER);
			warningLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
			warningLabel.setFont(FontManager.getRunescapeFont());
			warningLabel.setForeground(Color.BLACK);
			warningLabel.setBackground(new Color(0xFFBB33));
			warningLabel.setOpaque(true);

			warningNoEngine.add(warningLabel, BorderLayout.NORTH);
			warningNoEngine.add(explainLabel, BorderLayout.CENTER);
		}

		{
			crashLabel = new JLabel("", SwingConstants.CENTER);
			crashLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
			crashLabel.setFont(FontManager.getRunescapeFont());
			crashLabel.setForeground(Color.WHITE);
			crashLabel.setBackground(Color.RED.darker().darker());
			crashLabel.setOpaque(true);

			warningCrash = new JPanel();
			warningCrash.setVisible(false);
			warningCrash.setLayout(new BorderLayout());
			warningCrash.add(crashLabel, BorderLayout.CENTER);
		}

		{
			warningMinimumMode = new JPanel();
			warningMinimumMode.setVisible(false);
			warningMinimumMode.setLayout(new BorderLayout());

			JLabel warningLabel =
				new JLabel("<html>Minimum Mode &#9888;</html>", SwingConstants.CENTER);
			warningLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
			warningLabel.setFont(FontManager.getRunescapeFont());
			warningLabel.setForeground(Color.BLACK);
			warningLabel.setBackground(new Color(0xFFBB33));
			warningLabel.setOpaque(true);

			warningMinimumMode.add(warningLabel, BorderLayout.NORTH);
			warningMinimumMode.add(explainLabel, BorderLayout.CENTER);

			warningLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
			warningLabel.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					explainLabel.setVisible(!explainLabel.isVisible());
				}

				@Override
				public void mouseEntered(MouseEvent e) {
					setBackground(new Color(0xFFBB33).brighter());
				}

				@Override
				public void mouseExited(MouseEvent e) {
					setBackground(new Color(0xFFBB33));
				}
			});
		}

		mainContentPanel.add(warningCrash);
		mainContentPanel.add(warningNoEngine);
		mainContentPanel.add(warningStopped);
		mainContentPanel.add(warningMinimumMode);

		updateWarningsUI();
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

		final JButton sectionToggle = new JButton(PluginResources.SECTION_RETRACT_ICON);
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

		JPanel piperFileChoosePanel = buildPiperFileChoose();
		sectionContent.add(piperFileChoosePanel);
	}

	@SuppressWarnings("deprecation")
	private JPanel buildPiperFileChoose() {
		JLabel header = new JLabel("Piper Location");
		header.setForeground(Color.WHITE);

		JTextField filePathField = new JTextField(runtimeConfig.getPiperPath().toString());
		filePathField.setToolTipText(
			"If you manually downloaded piper, you can set it's location here. Otherwise, use our installer!");
		filePathField.setEditable(false);

		JButton browseButton = new JButton("Browse");
		browseButton.setToolTipText("Requires manual download, please read instructions.");
		browseButton.addActionListener(e -> {
			JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			int returnValue = fileChooser.showOpenDialog(this);
			if (returnValue == JFileChooser.APPROVE_OPTION) {
				Path newPath = Path.of(fileChooser.getSelectedFile().getPath());

				// if the user accidentally set the piper folder and not the executable, automatically correct
				if (newPath.toFile().isDirectory()) {
					if (PlatformUtil.IS_WINDOWS) {
						newPath = newPath.resolve("piper.exe");
					}
					else { // assume unix based
						newPath = newPath.resolve("piper");
					}
				}

				filePathField.setText(newPath.toString());
				runtimeConfig.savePiperPath(newPath);

				// if text to speech is running, restart
				if (speechManager.isAlive()) speechManager.shutDown();
			}
		});

		JPanel fileBrowsePanel = new JPanel(new BorderLayout());
		fileBrowsePanel.setBorder(new EmptyBorder(5, 0, 0, 0));
		fileBrowsePanel.add(header, BorderLayout.NORTH);
		fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
		fileBrowsePanel.add(browseButton, BorderLayout.SOUTH);
		return fileBrowsePanel;
	}

	public void buildTextToSpeechStatusSegment() {
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

		final JButton sectionToggle = new JButton(PluginResources.SECTION_RETRACT_ICON);
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
		JPanel statusPanel = buildTextToSpeechControlsPanel();
		sectionContent.add(statusPanel);

		JPanel monitorPanel = buildPiperProcessMonitorPanel();
		sectionContent.add(monitorPanel);
	}

	private JPanel buildPiperProcessMonitorPanel() {
		piperMonitorPanel = new JPanel();
		piperMonitorPanel.setLayout(new DynamicGridLayout(0, 1, 0, 2));
		piperMonitorPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
		piperMonitorPanel.setVisible(false);

		JLabel header = new JLabel("Piper Process Monitor");
		header.setForeground(Color.WHITE);

		piperMonitorPanel.add(header, BorderLayout.CENTER);

		updateActivePiperProcessCount();
		piperMonitorPanel.add(piperProcessDisplay, BorderLayout.CENTER);

		return piperMonitorPanel;
	}

	private void updateActivePiperProcessCount() {
		Map<String, PiperEngine> instances = speechManager.getEngines()
			.filter(engine -> engine instanceof PiperEngine && ((PiperEngine) engine).processCount() > 0)
			.map(engine -> (PiperEngine) engine)
			.collect(Collectors.toMap(PiperEngine::getEngineName,
				Function.identity()
			));


		StringBuilder piperProcessList = new StringBuilder("<html>");
		for (PiperEngine engine : instances.values()) {
			piperProcessList.append(engine.toUIString()).append("<br/>");
		}
		piperProcessList.append("</html>");
		piperProcessDisplay.setText(piperProcessList.toString());
		piperProcessDisplay.revalidate();
	}

	private JPanel buildTextToSpeechControlsPanel() {
		statusPanel = new JPanel();
		statusPanel.setLayout(new BorderLayout());
		statusPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		statusLabel = new JLabel("Not Running", SwingConstants.CENTER);
		statusLabel.setFont(new Font("Sans", Font.BOLD, 20));
		statusLabel.setOpaque(true); // Needed to show background color
		statusLabel.setPreferredSize(new Dimension(statusLabel.getWidth(), 50));
		statusLabel.setBackground(Color.DARK_GRAY);
		statusPanel.setToolTipText("Press start to begin text to speech.");

		statusPanel.add(statusLabel, BorderLayout.NORTH);

		// Button Panel
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton playButton = createButton(PluginResources.START_TEXT_TO_SPEECH_ICON, "Start");
		JButton stopButton = createButton(PluginResources.STOP_TEXT_TO_SPEECH_ICON, "Stop");
		playButton.addActionListener(e -> speechManager.startUp());
		stopButton.addActionListener(e -> speechManager.shutDown());
		buttonPanel.add(playButton);
		buttonPanel.add(stopButton);

		// Volume Panel
		JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JLabel volumeLabel = new JLabel("Volume");
		int volume = config.masterVolume();

		//Volume Slider
		volumeSlider = new JSlider(0, 100, volume);
		volumeSlider.setMajorTickSpacing(10);
		volumeSlider.setPaintTicks(true);
		volumeSlider.setPaintLabels(true);
		volumeSlider.addChangeListener(volumeChangeListener);

		volumePanel.add(volumeLabel);
		volumePanel.add(volumeSlider);

		statusPanel.add(buttonPanel, BorderLayout.CENTER);
		statusPanel.add(volumePanel, BorderLayout.SOUTH);

		return statusPanel;
	}

	public void updateVolumeSlider(int newVolume) {
		volumeSlider.removeChangeListener(volumeChangeListener);
		volumeSlider.setValue(newVolume);
		volumeSlider.addChangeListener(volumeChangeListener);
	}

	private void toggleSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState ? PluginResources.SECTION_RETRACT_ICON : PluginResources.SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState ? "Retract" : "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	private JButton createButton(ImageIcon icon, String toolTipText) {
		JButton button = new JButton(icon);
		button.setToolTipText(toolTipText);
		return button;
	}

	@Override
	public void onActivate() {
		this.setVisible(true);
	}

	@Override
	public void onDeactivate() {
		this.setVisible(false);
	}

	private enum Warning {
		// Warnings is a 64 slot set implemented with bitmask
		NO_WARNINGS,
		NO_ENGINE,
		STOPPED,
		MINIMUM_MODE,
		CRASHED
	}
}
