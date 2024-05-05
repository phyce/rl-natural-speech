package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.configs.TextToSpeechConfig;
import dev.phyce.naturalspeech.tts.engine.TextToSpeech;
import dev.phyce.naturalspeech.tts.engine.SAPI4Engine;
import static dev.phyce.naturalspeech.ui.panels.StaticImages.OFF_SWITCHER;
import static dev.phyce.naturalspeech.ui.panels.StaticImages.ON_SWITCHER;
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

public class SAPI4ListItem extends JPanel {

	private static final int BOTTOM_LINE_HEIGHT = 16;

	private final TextToSpeechConfig textToSpeechConfig;
	private final SAPI4Engine engine;
	private final TextToSpeech textToSpeech;

	@Inject
	public SAPI4ListItem(TextToSpeechConfig textToSpeechConfig, SAPI4Engine engine, TextToSpeech textToSpeech) {
		this.textToSpeechConfig = textToSpeechConfig;
		this.engine = engine;
		this.textToSpeech = textToSpeech;
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

		JLabel name = new JLabel("Microsoft Speech 4");
		name.setFont(FontManager.getRunescapeBoldFont());

		JLabel description = new JLabel(String.format("<html><p>%s</p></html>",
			"Microsoft Speech 4 from 1999. <span style=\"color:white\">microsoft:sam</span> is Gary Gilbert."));
		description.setVerticalAlignment(JLabel.TOP);

		JLabel memorySize = new JLabel("installed");
		memorySize.setFont(FontManager.getRunescapeSmallFont());

		JToggleButton toggleButton = new JToggleButton();
		SwingUtil.removeButtonDecorations(toggleButton);
		toggleButton.setIcon(OFF_SWITCHER);
		toggleButton.setSelectedIcon(ON_SWITCHER);
		toggleButton.setPreferredSize(new Dimension(25, 0));
		toggleButton.setSelected(textToSpeechConfig.isEnabled(engine));
		toggleButton.addActionListener(
			l -> {
				textToSpeechConfig.setEnable(engine, toggleButton.isSelected());
				if (textToSpeech.isStarted()) {
					if (toggleButton.isSelected()) {
						textToSpeech.startEngine(engine);
					} else {
						textToSpeech.stopEngine(engine);
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
