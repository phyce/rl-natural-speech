package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.AudioLineNames;
import dev.phyce.naturalspeech.tts.engine.TextToSpeech;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceListItem extends JPanel {

	private final TextToSpeech textToSpeech;
	private final VoiceExplorerPanel voiceExplorerPanel;
	@Getter
	private final VoiceMetadata voiceMetadata;

	private static final ImageIcon PLAY_BUTTON;
	private static final ImageIcon PLAY_BUTTON_DISABLED;

	static {
		BufferedImage image = ImageUtil.loadImageResource(VoiceListItem.class, "start.png");
		PLAY_BUTTON = new ImageIcon(image.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
		PLAY_BUTTON_DISABLED = new ImageIcon(
			ImageUtil.luminanceScale(ImageUtil.grayscaleImage(image), 0.61f)
				.getScaledInstance(25, 25, Image.SCALE_SMOOTH));

	}

	private final JPanel hintPanel;
	private final JPanel contentPanel;


	public VoiceListItem(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		VoiceMetadata voiceMetadata
	) {
		this.textToSpeech = textToSpeech;
		this.voiceExplorerPanel = voiceExplorerPanel;
		this.voiceMetadata = voiceMetadata;


		JPanel speakerPanel = new JPanel();
		speakerPanel.setOpaque(false);

		GroupLayout speakerLayout = new GroupLayout(speakerPanel);
		speakerPanel.setLayout(speakerLayout);


		JLabel nameLabel = new JLabel("<html>" + voiceMetadata.getName() + "</html>");
		nameLabel.setForeground(Color.white);

		String genderString;
		if (voiceMetadata.getGender() == Gender.MALE) {
			genderString = "(M)";
		}
		else if (voiceMetadata.getGender() == Gender.FEMALE) {
			genderString = "(F)";
		}
		else {
			genderString = "(?)";
		}

		JLabel genderLabel = new JLabel(genderString);
		genderLabel.setForeground(Color.white);

		JLabel piperIdLabel = new JLabel("<html>" + voiceMetadata.voiceId.id + "</html>");

		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(piperIdLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(5)
			.addComponent(nameLabel, 0, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(0,5,5)
			.addComponent(genderLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);

		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup()
			.addGap(5)
			.addComponent(piperIdLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight * 2)
			.addComponent(genderLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addGap(5));


		JButton playButton = new JButton(PLAY_BUTTON);
		SwingUtil.removeButtonDecorations(playButton);
		playButton.setPreferredSize(
			new Dimension(PLAY_BUTTON_DISABLED.getIconWidth(), PLAY_BUTTON_DISABLED.getIconHeight()));
		playButton.addActionListener(event -> {
				textToSpeech.silence((lineName) -> lineName.equals(AudioLineNames.VOICE_EXPLORER));
				textToSpeech.speak(
					voiceMetadata.voiceId,
					textToSpeech.expandAbbreviations(voiceExplorerPanel.getSpeechText().getText()),
					() -> 0f,
					AudioLineNames.VOICE_EXPLORER
				);
			}
		);

		JPanel overlayWrapper = new JPanel();
		overlayWrapper.setLayout(new OverlayLayout(overlayWrapper));


		contentPanel = new JPanel();
		contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		contentPanel.setOpaque(true);
		contentPanel.setToolTipText(voiceMetadata.voiceId.toVoiceIDString());
		contentPanel.setLayout(new BorderLayout());
		contentPanel.add(speakerPanel, BorderLayout.CENTER);
		contentPanel.add(playButton, BorderLayout.EAST);

		contentPanel.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
				hintPanel.setVisible(true);

				// copy voiceid to clipboard
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
					new StringSelection(voiceMetadata.voiceId.toVoiceIDString()), null
				);

				Timer timer = new Timer(1500, event -> {
					hintPanel.setVisible(false);
					revalidate();
				});
				timer.setRepeats(false);
				timer.start();

				revalidate();
			}

			@Override
			public void mousePressed(MouseEvent e) {
				contentPanel.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
				revalidate();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				revalidate();
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				contentPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				revalidate();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				revalidate();
			}
		});

		hintPanel = new JPanel();
		hintPanel.setBackground(ColorScheme.BRAND_ORANGE_TRANSPARENT);
		JLabel copiedHint = new JLabel("copied!");
		copiedHint.setForeground(Color.WHITE);
		copiedHint.setFont(FontManager.getRunescapeFont());
		hintPanel.add(copiedHint);
		hintPanel.setEnabled(false);
		hintPanel.setVisible(false);

		overlayWrapper.add(hintPanel);
		overlayWrapper.add(contentPanel);

		this.setLayout(new BorderLayout());
		this.add(overlayWrapper);

		revalidate();
	}
}
