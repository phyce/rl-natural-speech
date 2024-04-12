package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.piper.PiperEngine;
import dev.phyce.naturalspeech.tts.piper.PiperRepository;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
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
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class PiperModelItem extends JPanel {

	private final TextToSpeech textToSpeech;
	private final PiperEngine piperEngine;
	private final PiperRepository piperRepository;
	private final PiperRepository.ModelURL modelUrl;

	private static final int BOTTOM_LINE_HEIGHT = 16;
	private static final ImageIcon ON_SWITCHER;
	private static final ImageIcon OFF_SWITCHER;

	static {
		BufferedImage onSwitcher = ImageUtil.loadImageResource(MainSettingsPanel.class, "switcher_on.png");
		ON_SWITCHER = new ImageIcon(onSwitcher);
		OFF_SWITCHER = new ImageIcon(ImageUtil.flipImage(
			ImageUtil.luminanceScale(
				ImageUtil.grayscaleImage(onSwitcher),
				0.61f
			),
			true,
			false
		));
	}

	private MouseAdapter contextMenuMouseListener;

	public PiperModelItem(
		TextToSpeech textToSpeech,
		PiperEngine piperEngine,
		PiperRepository piperRepository,
		PiperRepository.ModelURL modelUrl
	) {
		this.textToSpeech = textToSpeech;
		this.piperEngine = piperEngine;
		this.piperRepository = piperRepository;
		this.modelUrl = modelUrl;

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
		toggleButton.setIcon(OFF_SWITCHER);
		toggleButton.setSelectedIcon(ON_SWITCHER);
		toggleButton.setPreferredSize(new Dimension(25, 0));
		toggleButton.setSelected(piperEngine.getModelConfig().isModelEnabled(modelUrl.getModelName()));
		toggleButton.addActionListener(
			l -> {
				log.debug("Toggling {} into {}", modelUrl.getModelName(), toggleButton.isSelected());
				piperEngine.getModelConfig().setModelEnabled(modelUrl.getModelName(), toggleButton.isSelected());
				try {
					PiperRepository.ModelLocal modelLocal = piperRepository.loadModelLocal(modelUrl.getModelName());
					if (textToSpeech.isStarted()) {
						if (toggleButton.isSelected()) {
							piperEngine.startModel(modelLocal);
						}
						else {
							piperEngine.stopModel(modelLocal);
						}
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});

		JButton download = new JButton();
		download.setText("Download");
		if (!piperEngine.isPiperPathValid()) {
			download.setEnabled(false);
			download.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			download.setBorder(new LineBorder(download.getBackground().darker()));
		}
		else {
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
						piperEngine.getModelConfig().resetPiperConfig(modelUrl.getModelName());

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
					piperEngine.getModelConfig().resetPiperConfig(modelUrl.getModelName());

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
					piperEngine.getModelConfig().getModelProcessCount(modelUrl.getModelName()));

				if (result != null) {
					log.debug("Option chose: " + result);
					piperEngine.getModelConfig().setModelProcessCount(modelUrl.getModelName(), result);

					// TODO(Louis) lazy hack, just reboot all processes with new configuration
					if (textToSpeech.isStarted()) {
						textToSpeech.stop();
						textToSpeech.start();
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
