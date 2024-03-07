package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VoiceListItem extends JPanel {

	private final VoiceExplorerPanel voiceExplorerPanel;
	private final NaturalSpeechPlugin plugin;
	@Getter
	private final ModelRepository.VoiceMetadata voiceMetadata;


	public VoiceListItem(VoiceExplorerPanel voiceExplorerPanel, NaturalSpeechPlugin plugin, ModelRepository.VoiceMetadata voiceMetadata) {
		this.voiceExplorerPanel = voiceExplorerPanel;
		this.plugin = plugin;
		this.voiceMetadata = voiceMetadata;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setToolTipText(String.format("%s %s (%s)", voiceMetadata.getPiperVoiceID(), voiceMetadata.getName(), voiceMetadata.getGender()));

		JPanel speakerPanel = new JPanel();
		speakerPanel.setOpaque(false);

		GroupLayout speakerLayout = new GroupLayout(speakerPanel);
		speakerPanel.setLayout(speakerLayout);


		JLabel nameLabel = new JLabel(voiceMetadata.getName());
		nameLabel.setForeground(Color.white);

		JLabel sexLabel = new JLabel(voiceMetadata.getGender().replaceFirst("M", "(M)").replaceFirst("F", "(F)"));
		sexLabel.setForeground(Color.white);

		JLabel piperIdLabel = new JLabel(String.format("ID%d", voiceMetadata.getPiperVoiceID()));
		sexLabel.setForeground(Color.white);


		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(piperIdLabel, 35, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(5)
			.addComponent(nameLabel)
			.addGap(5).addComponent(sexLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);

		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup()
			.addGap(5)
			.addComponent(piperIdLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(sexLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addGap(5));


		BufferedImage image = ImageUtil.loadImageResource(VoiceListItem.class, "start.png");
		Image scaledImg = image.getScaledInstance(25, 25, Image.SCALE_SMOOTH);
		ImageIcon playIcon = new ImageIcon(scaledImg);
		JButton playButton = new JButton(playIcon);
		SwingUtil.removeButtonDecorations(playButton);
		playButton.setPreferredSize(new Dimension(playIcon.getIconWidth(), playIcon.getIconHeight()));
		playButton.addActionListener(event -> {
			if (plugin.getTextToSpeech() != null && plugin.getTextToSpeech().activePiperInstanceCount() > 0) {
				try {
					plugin.getTextToSpeech().speak(
						voiceMetadata.toVoiceID(),
						plugin.expandShortenedPhrases(voiceExplorerPanel.getSpeechText().getText()),
						0,
						"&VoiceExplorer");
				} catch (ModelLocalUnavailableException e) {
					throw new RuntimeException(e);
				}
			}
		});

		BorderLayout rootLayout = new BorderLayout();
		this.setLayout(rootLayout);
		this.add(speakerPanel, BorderLayout.CENTER);
		this.add(playButton, BorderLayout.EAST);

		revalidate();
	}
}
