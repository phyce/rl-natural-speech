package dev.phyce.naturalspeech.userinterface.voicehub;

import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.eventbus.PluginEventBus;
import dev.phyce.naturalspeech.eventbus.PluginSubscribe;
import dev.phyce.naturalspeech.events.PiperPathChanged;
import dev.phyce.naturalspeech.events.PiperRepositoryChanged;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.engine.MacSpeechEngine;
import dev.phyce.naturalspeech.texttospeech.engine.SAPI5Engine;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi4.SAPI4Repository;
import dev.phyce.naturalspeech.userinterface.components.FixedWidthPanel;
import dev.phyce.naturalspeech.userinterface.layouts.OnlyVisibleGridLayout;
import dev.phyce.naturalspeech.utils.PlatformUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceHubPanel extends PluginPanel {
	private final PiperRepository piperRepository;
	private final SAPI4Repository sapi4Repository;
	private final SAPI5Engine sapi5Engine;
	private final MacSpeechEngine macSpeechEngine;
	private final SpeechManager speechManager;
	private final RuntimePathConfig runtimeConfig;

	private final FixedWidthPanel mainContentPanel;
	private static final EmptyBorder BORDER_PADDING = new EmptyBorder(0, 5, 0, 5);

	private final Map<String, PiperModelItem> piperModelMap = new HashMap<>();
	private final Provider<SAPI4ModelItem> sapi4ListItemProvider;
	private final Provider<SAPI5ModelItem> sapi5ListItemProvider;
	private final Provider<MacModelItem> macListItemProvider;
	private final Provider<PiperModelItem.Factory> piperListItemBuilder;


	@Inject
	public VoiceHubPanel(
		PiperRepository piperRepository,
		SAPI4Repository sapi4Repository,
		SAPI5Engine sapi5Engine,
		MacSpeechEngine macSpeechEngine,
		SpeechManager speechManager,
		RuntimePathConfig runtimeConfig,
		Provider<SAPI4ModelItem> sapi4ListItemProvider,
		Provider<SAPI5ModelItem> sapi5ListItemProvider,
		Provider<MacModelItem> macListItemProvider,
		PluginEventBus pluginEventBus,
		Provider<PiperModelItem.Factory> piperListItemBuilder
	) {
		super(false);
		this.piperRepository = piperRepository;
		this.sapi4Repository = sapi4Repository;
		this.sapi5Engine = sapi5Engine;
		this.macSpeechEngine = macSpeechEngine;
		this.speechManager = speechManager;
		this.runtimeConfig = runtimeConfig;
		this.sapi4ListItemProvider = sapi4ListItemProvider;
		this.sapi5ListItemProvider = sapi5ListItemProvider;
		this.macListItemProvider = macListItemProvider;
		this.piperListItemBuilder = piperListItemBuilder;


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

		buildVoiceRepositorySegment();
		buildAdvancedSegment();

		this.revalidate();
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

		final JButton sectionToggle = new JButton(PluginResources.SECTION_RETRACT_ICON);
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

		// Piper Model
		piperRepository.getUrls().forEach((modelUrl) -> {
			PiperModelItem modelItem = piperListItemBuilder.get().create(modelUrl);
			piperModelMap.put(modelUrl.getModelName(), modelItem);
			sectionContent.add(modelItem);
		});

		if (!macSpeechEngine.getNativeVoices().isEmpty()) {
			sectionContent.add(macListItemProvider.get(), 0);
		}

		// Sapi5 Model
		if (!sapi5Engine.getNativeVoices().isEmpty()) {
			sectionContent.add(sapi5ListItemProvider.get(), 0);
		}

		// Sapi4 Model
		List<String> sapi4Models = sapi4Repository.getVoices();
		if (!sapi4Models.isEmpty()) {
			sectionContent.add(sapi4ListItemProvider.get(), 0);
		}
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

//		JPanel piperProcessMonitorPanel = buildPiperProcessMonitorPanel();
//		sectionContent.add(piperProcessMonitorPanel);
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

	@Deprecated(since="1.3.0 We have an installer which installs to a standard location, no more path changes.")
	@PluginSubscribe
	public void on(PiperPathChanged event) {
		log.debug("Repository refresh. Rebuilding");
		for (PiperModelItem listItem : piperModelMap.values()) {
			listItem.rebuild();
		}
		SwingUtilities.invokeLater(this::revalidate);
	}

	@PluginSubscribe
	public void on(PiperRepositoryChanged event) {
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

	private void toggleSection(JButton toggleButton, JPanel sectionContent) {
		boolean newState = !sectionContent.isVisible();
		sectionContent.setVisible(newState);
		toggleButton.setIcon(newState ? PluginResources.SECTION_RETRACT_ICON : PluginResources.SECTION_EXPAND_ICON);
		toggleButton.setToolTipText(newState ? "Retract" : "Expand");
		SwingUtilities.invokeLater(sectionContent::revalidate);
	}

	@Override
	public void onActivate() {
		this.setVisible(true);
	}

	@Override
	public void onDeactivate() {
		this.setVisible(false);
	}
}
