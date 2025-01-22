package dev.phyce.naturalspeech.userinterface.voicehub;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import dev.phyce.naturalspeech.configs.PiperConfig;
import dev.phyce.naturalspeech.configs.RuntimePathConfig;
import dev.phyce.naturalspeech.texttospeech.engine.PiperEngine;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.engine.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository.PiperModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class PiperModelItem extends JPanel {

	private final PluginExecutorService pluginExecutorService;
	private final SpeechManager speechManager;
	private final PiperRepository piperRepository;
	private final PiperConfig piperConfig;
	private final RuntimePathConfig runtimePathConfig;
	private final PiperEngine.Factory piperModelEngineFactory;

	private static final String[] DOWNLOAD_STATES = {".", "..", "...", "...."};
	private Timer downloadAnimationTimer;
	private int currentDownloadAnimationState = 0;

	private final PiperRepository.PiperModelURL modelUrl;

	private static final int BOTTOM_LINE_HEIGHT = 16;


	private MouseAdapter contextMenuMouseListener;

	public interface Factory {
		PiperModelItem create(PiperRepository.PiperModelURL modelUrl);
	}

	@Inject
	public PiperModelItem(
		PluginExecutorService pluginExecutorService,
		SpeechManager speechManager,
		PiperConfig piperConfig,
		PiperRepository piperRepository,
		RuntimePathConfig runtimePathConfig,
		PiperEngine.Factory piperModelEngineFactory,
		@Assisted PiperRepository.PiperModelURL modelUrl
	) {
		this.pluginExecutorService = pluginExecutorService;
		this.speechManager = speechManager;
		this.piperRepository = piperRepository;
		this.piperConfig = piperConfig;
		this.runtimePathConfig = runtimePathConfig;
		this.piperModelEngineFactory = piperModelEngineFactory;
		this.modelUrl = modelUrl;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setBorder(new EmptyBorder(5, 5, 5, 5));

		rebuild();
	}

	public void rebuild() {

		this.removeAll();
		this.removeMouseListener(this.contextMenuMouseListener);


		JLabel name = new JLabel(modelUrl.getModelName());
		name.setFont(FontManager.getRunescapeBoldFont());

		JLabel description = new JLabel(String.format("<html><p>%s</p></html>", modelUrl.getDescription()));
		description.setVerticalAlignment(JLabel.TOP);

		JLabel memorySize = new JLabel(modelUrl.getMemorySize());
		memorySize.setFont(FontManager.getRunescapeSmallFont());

		JToggleButton toggleButton = buildToggleButton();
		JButton downloadButton = buildDownloadButton();

		boolean hasLocal = piperRepository.isLocal(modelUrl);

		if (hasLocal) {downloadButton.setVisible(false);}
		else {toggleButton.setVisible(false);}


		if (hasLocal) {
			this.setToolTipText("Right click for other settings.");

			JMenuItem removeMenu = new JMenuItem("Remove");
			removeMenu.addActionListener(ev -> onRemoveButton());

			JMenuItem setProcessCountMenu = new JMenuItem("Set Process Count");
			setProcessCountMenu.addActionListener(ev -> onSetProcessCount());

			this.contextMenuMouseListener = buildContextMenu(this, setProcessCountMenu, removeMenu);
		}

		GroupLayout layout = buildLayout(name, description, memorySize, toggleButton, downloadButton);
		this.setLayout(layout);
	}

	private GroupLayout buildLayout(
		JLabel name,
		JLabel description,
		JLabel memorySize,
		JToggleButton toggleButton,
		JButton downloadButton
	) {
		GroupLayout layout;
		layout = new GroupLayout(this);

		layout.setHorizontalGroup(layout
			.createParallelGroup()
			.addGroup(layout.createSequentialGroup()
				.addComponent(name, 0, GroupLayout.PREFERRED_SIZE, 0x7000)
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, 0x7000)
			)
			.addComponent(description, 0, GroupLayout.PREFERRED_SIZE, 0x7000)
			.addGroup(layout.createSequentialGroup()
				.addComponent(memorySize)
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, 0x7000)
				.addComponent(toggleButton, 25, 25, 25)
				.addComponent(downloadButton, 0, 77, GroupLayout.PREFERRED_SIZE)
			)
		);

		int lineHeight = description.getFontMetrics(description.getFont()).getHeight();
		layout.setVerticalGroup(layout
			.createSequentialGroup()
			.addGap(2)
			.addGroup(layout.createParallelGroup()
				.addComponent(name)
			)
			.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, 100)
			.addComponent(description, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight * 4)
			.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, 100)
			.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
				.addComponent(memorySize, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
				.addComponent(toggleButton, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
				.addComponent(downloadButton, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
			)
			.addGap(2)
		);
		return layout;
	}

	private JButton buildDownloadButton() {
		JButton downloadButton;
		downloadButton = new JButton();
		downloadButton.setText("Download");
		if (!runtimePathConfig.isPiperPathValid()) {
			downloadButton.setEnabled(false);
			downloadButton.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			downloadButton.setBorder(new LineBorder(downloadButton.getBackground().darker()));
			this.setVisible(false);
		}
		else {
			this.setVisible(true);
			downloadButton.setBackground(new Color(0x28BE28));
			downloadButton.setBorder(new LineBorder(downloadButton.getBackground().darker()));
			downloadButton.addActionListener(l -> onDownloadButton(downloadButton));
		}
		return downloadButton;
	}

	private JToggleButton buildToggleButton() {
		JToggleButton toggleButton;
		toggleButton = new JToggleButton();
		SwingUtil.removeButtonDecorations(toggleButton);
		toggleButton.setIcon(PluginResources.OFF_SWITCHER);
		toggleButton.setSelectedIcon(PluginResources.ON_SWITCHER);
		toggleButton.setPreferredSize(new Dimension(25, 0));
		toggleButton.setSelected(piperConfig.isEnabled(modelUrl.getModelName()));
		toggleButton.addActionListener(l -> onToggle(toggleButton.isSelected()));
		return toggleButton;
	}

	private void onSetProcessCount() {
		JFrame alwaysOnTopFrame = new JFrame();
		alwaysOnTopFrame.setAlwaysOnTop(true);
		// open popup
		Integer result = (Integer) JOptionPane.showInputDialog(alwaysOnTopFrame,
			"<html><p>Text-to-speech will run faster with more processes.</p>" +
				"<p><strong>At the cost of memory</strong>, every process uses ~100MB of memory.</p></html>",
			"Set Number of Processes For This Model",
			JOptionPane.WARNING_MESSAGE,
			null,
			new Integer[] {1, 2, 3},
			piperConfig.getProcessCount(modelUrl.getModelName()));

		if (result != null) {
			log.debug("Option chose: " + result);
			piperConfig.setProcessCount(modelUrl.getModelName(), result);

			// TODO(Louis) lazy hack, just reboot all processes with new configuration
			if (speechManager.isAlive()) {
				speechManager.shutDown();
				speechManager.startUp();
			}
		}
		else {
			log.debug("Cancelled!");
		}
	}

	private void onRemoveButton() {
		try {
			PiperModel piperModel = piperRepository.get(modelUrl);

			Optional<PiperEngine> result = speechManager.getEngines()
				.filter(engine -> engine instanceof PiperEngine)
				.map(engine -> (PiperEngine) engine)
				.filter(engine -> Objects.equals(engine.getModel(), piperModel))
				.findFirst();

			Preconditions.checkState(result.isPresent(),
				"Removing Model Engine, but not found %s", modelUrl.getModelName());
			PiperEngine engine = result.get();

			speechManager.unloadEngine(engine);
			piperConfig.unset(piperModel.getModelName());
			piperRepository.delete(piperModel);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void onDownloadButton(JButton downloadButton) {
		downloadButton.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		downloadButton.setBorder(new LineBorder(downloadButton.getBackground().darker()));
		downloadButton.setEnabled(false);

		// Start the animation timer
		downloadAnimationTimer = new Timer(350, e -> {
			downloadButton.setText(DOWNLOAD_STATES[currentDownloadAnimationState]);
			currentDownloadAnimationState = (currentDownloadAnimationState + 1) % DOWNLOAD_STATES.length;
		});
		downloadAnimationTimer.start();

		// Create and execute SwingWorker
		SwingWorker<Void, Void> worker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() throws Exception {
				try {
					PiperModel model = piperRepository.get(modelUrl);
					piperConfig.unset(model.getModelName());
					piperConfig.setEnabled(model.getModelName(), true);
					PiperEngine engine = piperModelEngineFactory.create(model);
					speechManager.loadEngine(engine);
					speechManager.startupEngine(engine);
					return null;
				} catch (IOException e) {
					throw e;
				}
			}

			@Override
			protected void done() {
				downloadAnimationTimer.stop();
				try {
					get(); // This will throw the exception if one occurred
					downloadButton.setText("Downloaded");
				} catch (Exception e) {
					downloadButton.setText("Failed");
					SwingUtilities.invokeLater(() -> rebuild());
				} finally {
					downloadButton.setEnabled(true);
				}
			}
		};

		worker.execute();
	}

	// Don't forget to clean up the timer when the plugin is shut down
	private void cleanUp() {
		if (downloadAnimationTimer != null) {
			downloadAnimationTimer.stop();
			downloadAnimationTimer = null;
		}
	}

	private void onToggle(boolean selected) {
		log.debug("Toggling {} into {}", modelUrl.getModelName(), selected);
		piperConfig.setEnabled(modelUrl.getModelName(), selected);

		if (!speechManager.isAlive()) return;

		PiperModel piperModel;
		try {
			piperModel = piperRepository.get(modelUrl);
		} catch (IOException e) {
			log.error("ErrorResult loading {}.", modelUrl, e);
			return;
		}

		Optional<PiperEngine> result = speechManager.getEngines()
			.filter(engine -> engine instanceof PiperEngine)
			.map(engine -> (PiperEngine) engine)
			.filter(engine -> Objects.equals(engine.getModel(), piperModel))
			.findFirst();

		Preconditions.checkState(result.isPresent(),
			"Toggling Model Engine, but not found %s", modelUrl.getModelName());
		PiperEngine engine = result.get();

		if (selected) {
			// FIXME
			speechManager.startupEngine(engine);
		}
		else {
			speechManager.shutdownEngine(engine);
		}
	}

	static MouseAdapter buildContextMenu(JPanel panel, JMenuItem... menuItems) {
		final JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		for (final JMenuItem menuItem : menuItems) {
			if (menuItem == null) continue;
			menu.add(menuItem);
		}

		MouseAdapter listener = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent mouseEvent) {
				if (mouseEvent.getButton() == MouseEvent.BUTTON3) {
					Component source = (Component) mouseEvent.getSource();
					Point location = MouseInfo.getPointerInfo().getLocation();
					SwingUtilities.convertPointFromScreen(location, source);
					menu.show(source, location.x, location.y);
				}
			}
		};
		panel.addMouseListener(listener);
		return listener;
	}


}
