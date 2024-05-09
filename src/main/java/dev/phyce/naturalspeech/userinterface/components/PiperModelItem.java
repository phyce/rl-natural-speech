package dev.phyce.naturalspeech.userinterface.components;

import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.executor.PluginExecutorService;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperEngine;
import dev.phyce.naturalspeech.texttospeech.engine.piper.PiperRepository;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
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
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class PiperModelItem extends JPanel {

	private final SpeechManager speechManager;
	private final PiperEngine piperEngine;
	private final PiperRepository piperRepository;
	private final PiperRepository.ModelURL modelUrl;
	private final PluginExecutorService pluginExecutorService;

	private static final int BOTTOM_LINE_HEIGHT = 16;


	private MouseAdapter contextMenuMouseListener;

	public PiperModelItem(
		SpeechManager speechManager,
		PiperEngine piperEngine,
		PiperRepository piperRepository,
		PluginExecutorService pluginExecutorService,
		PiperRepository.ModelURL modelUrl
	) {
		this.speechManager = speechManager;
		this.piperEngine = piperEngine;
		this.piperRepository = piperRepository;
		this.modelUrl = modelUrl;
		this.pluginExecutorService = pluginExecutorService;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setBorder(new EmptyBorder(5, 5, 5, 5));

		rebuild();

	}

	public void rebuild() {

		this.removeAll();
		this.removeMouseListener(this.contextMenuMouseListener);

		GroupLayout layout = new GroupLayout(this);
		this.setLayout(layout);

		JLabel name = new JLabel(modelUrl.getModelName());
		name.setFont(FontManager.getRunescapeBoldFont());

		JLabel description = new JLabel(String.format("<html><p>%s</p></html>", modelUrl.getDescription()));
		description.setVerticalAlignment(JLabel.TOP);

		JLabel memorySize = new JLabel(modelUrl.getMemorySize());
		memorySize.setFont(FontManager.getRunescapeSmallFont());

		JToggleButton toggleButton = new JToggleButton();
		SwingUtil.removeButtonDecorations(toggleButton);
		toggleButton.setIcon(PluginResources.OFF_SWITCHER);
		toggleButton.setSelectedIcon(PluginResources.ON_SWITCHER);
		toggleButton.setPreferredSize(new Dimension(25, 0));
		toggleButton.setSelected(piperEngine.getPiperConfig().isModelEnabled(modelUrl.getModelName()));
		toggleButton.addActionListener(
			l -> {
				log.debug("Toggling {} into {}", modelUrl.getModelName(), toggleButton.isSelected());
				piperEngine.getPiperConfig().setModelEnabled(modelUrl.getModelName(), toggleButton.isSelected());

				if (!speechManager.isStarted()) return;

				PiperRepository.ModelLocal modelLocal;
				try {
					modelLocal = piperRepository.loadModelLocal(modelUrl.getModelName());
				} catch (IOException e) {
					log.error("Error loading model while toggling piper model.", e);
					return;
				}

				if (piperEngine.isStarted()) {
					if (toggleButton.isSelected()) {
						try {
							piperEngine.startModel(modelLocal);
						} catch (IOException e) {
							log.error("Failed to start model {}", modelLocal.getModelName(), e);
						}
					}
					else {
						piperEngine.stopModel(modelLocal);

						if (!piperEngine.isAlive()) {
							speechManager.stopEngine(piperEngine);
						}
					}
				}
				else {
					// otherwise, we start the engine first, then start the model
					speechManager.startEngine(piperEngine);
				}
			});

		JButton download = new JButton();
		download.setText("Download");
		if (!piperEngine.isPiperPathValid()) {
			download.setEnabled(false);
			download.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			download.setBorder(new LineBorder(download.getBackground().darker()));
			this.setVisible(false);
		}
		else {
			this.setVisible(true);
			download.setBackground(new Color(0x28BE28));
			download.setBorder(new LineBorder(download.getBackground().darker()));
			download.addActionListener(l -> {
				download.setText("Downloading");
				download.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
				download.setBorder(new LineBorder(download.getBackground().darker()));
				download.setEnabled(false);

				piperRepository.getExecutor().execute(() -> {
					try {
						// reset model configs, in case there are previous settings
						piperEngine.getPiperConfig().resetPiperConfig(modelUrl.getModelName());

						piperRepository.loadModelLocal(modelUrl.getModelName());

					} catch (IOException ignored) {
						SwingUtilities.invokeLater(this::rebuild);
					}
				});
			});
		}

		boolean hasLocal;
		hasLocal = piperRepository.hasModelLocal(modelUrl.getModelName());

		if (hasLocal) {
			download.setVisible(false);
		}
		else {
			toggleButton.setVisible(false);
		}

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
				.addComponent(download, 0, 77, GroupLayout.PREFERRED_SIZE)
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
				.addComponent(download, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
			)
			.addGap(2)
		);

		if (hasLocal) {
			this.setToolTipText("Right click for other settings.");
			JMenuItem remove = new JMenuItem("Remove");
			remove.addActionListener(ev -> {
				try {
					PiperRepository.ModelLocal modelLocal = piperRepository.loadModelLocal(modelUrl.getModelName());

					// stop the piper
					if (piperEngine.isStarted() && piperEngine.isModelActive(modelLocal.getModelName())) {
						piperEngine.stopModel(modelLocal);
					}

					// reset the model configs
					piperEngine.getPiperConfig().resetPiperConfig(modelUrl.getModelName());

					// delete the files
					piperRepository.deleteModelLocal(modelLocal);

				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			JMenuItem setProcessCount = new JMenuItem("Set Process Count");
			setProcessCount.addActionListener(ev -> {
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
					piperEngine.getPiperConfig().getModelProcessCount(modelUrl.getModelName()));

				if (result != null) {
					log.debug("Option chose: " + result);
					piperEngine.getPiperConfig().setModelProcessCount(modelUrl.getModelName(), result);

					// TODO(Louis) lazy hack, just reboot all processes with new configuration
					if (speechManager.isStarted()) {
						speechManager.stop();
						speechManager.start(pluginExecutorService);
					}
				}
				else {
					log.debug("Cancelled!");
				}

			});
			this.contextMenuMouseListener = addPopupMenu(this, setProcessCount, remove);
		}
	}

	static MouseAdapter addPopupMenu(JPanel panel, JMenuItem... menuItems) {
		final JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		for (final JMenuItem menuItem : menuItems) {
			if (menuItem == null) {
				continue;
			}
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
