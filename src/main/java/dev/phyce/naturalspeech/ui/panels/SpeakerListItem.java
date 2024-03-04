package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.ModelRepository;
import dev.phyce.naturalspeech.tts.TTSItem;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class SpeakerListItem extends JPanel {

	private final SpeakerExplorerPanel speakerExplorerPanel;
	private final NaturalSpeechPlugin plugin;
	@Getter
	private final ModelRepository.Voice voice;


	public SpeakerListItem(SpeakerExplorerPanel speakerExplorerPanel, NaturalSpeechPlugin plugin, ModelRepository.Voice voice) {


		this.speakerExplorerPanel = speakerExplorerPanel;
		this.plugin = plugin;
		this.voice = voice;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setToolTipText(String.format("%s:%d %s (%s)", voice.getModelShortName(), voice.getVoiceID(), voice.getName(), voice.getGender()));

		JPanel speakerPanel = new JPanel();
		speakerPanel.setOpaque(false);

		GroupLayout speakerLayout = new GroupLayout(speakerPanel);
		speakerPanel.setLayout(speakerLayout);


		JLabel nameLabel = new JLabel(voice.getName());
		nameLabel.setForeground(Color.white);

		JLabel sexLabel = new JLabel(voice.getGender().replaceFirst("M", "(M)").replaceFirst("F", "(F)"));
		sexLabel.setForeground(Color.white);

		JLabel piperIdLabel = new JLabel(String.format("ID%d", voice.getVoiceID()));
		sexLabel.setForeground(Color.white);


		speakerLayout.setHorizontalGroup(speakerLayout.createSequentialGroup().addGap(5).addComponent(piperIdLabel, 35, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE).addGap(5).addComponent(nameLabel).addGap(5).addComponent(sexLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);
		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup().addGap(5).addComponent(piperIdLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight).addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight).addComponent(sexLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight).addGap(5));


        BufferedImage image = ImageUtil.loadImageResource(SpeakerListItem.class, "start.png");
        Image scaledImg = image.getScaledInstance(25, 25, Image.SCALE_SMOOTH);
        ImageIcon playIcon = new ImageIcon(scaledImg);
        JButton playButton = new JButton(playIcon);
        SwingUtil.removeButtonDecorations(playButton);
        playButton.setPreferredSize(new Dimension(playIcon.getIconWidth(), playIcon.getIconHeight()));
        playButton.addActionListener(event -> {
            if (plugin.getTts() != null && plugin.getTts().isActive()) {
                ChatMessage message = new ChatMessage();
                message.setMessage(speakerExplorerPanel.getSpeechText().getText());
                message.setType(ChatMessageType.DIALOG);
				message.setName("VoiceExplorer");

				TTSItem ttsItem = new TTSItem(message, 0, voice.getVoiceID());
				try {
					plugin.getTts().speak(ttsItem);
                } catch (IOException e) {
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
