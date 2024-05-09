package dev.phyce.naturalspeech.userinterface.components;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.SpeechManagerConfig;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Engine;
import java.awt.Dimension;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.LayoutStyle;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.SwingUtil;

public class SAPI5ListItem extends JPanel {

	private static final int BOTTOM_LINE_HEIGHT = 16;
	private final SpeechManagerConfig speechManagerConfig;
	private final SAPI5Engine engine;
	private final SpeechManager speechManager;

	@Inject
	public SAPI5ListItem(SpeechManagerConfig speechManagerConfig, SAPI5Engine engine, SpeechManager speechManager) {
		this.speechManagerConfig = speechManagerConfig;
		this.engine = engine;
		this.speechManager = speechManager;
		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setBorder(new EmptyBorder(5, 5, 5, 5));

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setBorder(new EmptyBorder(5, 5, 5, 5));

		rebuild();
	}

	private void rebuild() {

		this.removeAll();
		//		this.removeMouseListener(this.contextMenuMouseListener);

		GroupLayout layout = new GroupLayout(this);
		this.setLayout(layout);

		JLabel name = new JLabel("Microsoft Speech 5");
		name.setFont(FontManager.getRunescapeBoldFont());

		JLabel description = new JLabel(String.format("<html><p>%s</p></html>", "Microsoft Speech 5, built-in"));
		description.setVerticalAlignment(JLabel.TOP);

		JLabel memorySize = new JLabel("Windows built-In");
		memorySize.setFont(FontManager.getRunescapeSmallFont());

		JToggleButton toggleButton = new JToggleButton();
		SwingUtil.removeButtonDecorations(toggleButton);
		toggleButton.setIcon(PluginResources.OFF_SWITCHER);
		toggleButton.setSelectedIcon(PluginResources.ON_SWITCHER);
		toggleButton.setPreferredSize(new Dimension(25, 0));
		toggleButton.setSelected(speechManagerConfig.isEnabled(engine));
		toggleButton.addActionListener(
			l -> {
				speechManagerConfig.setEnable(engine, toggleButton.isSelected());
				if (speechManager.isStarted()) {
					if (toggleButton.isSelected()) {
						speechManager.startEngine(engine);
					}
					else {
						speechManager.stopEngine(engine);
					}
				}
			});

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
				//				.addComponent(download, 0, 77, GroupLayout.PREFERRED_SIZE)
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
				//				.addComponent(download, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
			)
			.addGap(2)
		);

	}

}
