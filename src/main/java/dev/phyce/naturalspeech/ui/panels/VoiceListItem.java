package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.enums.ElevenLabsModel;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.tts.MagicUsernames;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.annotation.CheckForNull;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceListItem extends JPanel {

	private final TextToSpeech textToSpeech;
	private final TextToSpeech.TextToSpeechListener listener;
	@Getter
	private final String voiceName;
	@Getter
	private final Gender gender;

	private static final int COPIED_DISPLAY_MS = 1500;
	private static final ImageIcon PLAY_BUTTON;
	private static final ImageIcon PLAY_BUTTON_DISABLED;

	static {
		BufferedImage image = ImageUtil.loadImageResource(VoiceListItem.class, "start.png");
		PLAY_BUTTON = new ImageIcon(image.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
		PLAY_BUTTON_DISABLED = new ImageIcon(
			ImageUtil.luminanceScale(ImageUtil.grayscaleImage(image), 0.61f)
				.getScaledInstance(25, 25, Image.SCALE_SMOOTH));

	}


	public static VoiceListItem forPiperVoice(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		ModelRepository.VoiceMetadata voiceMetadata) {
		return new VoiceListItem(voiceExplorerPanel, textToSpeech, voiceMetadata.toVoiceID(),
			voiceMetadata.getName(), voiceMetadata.getGender(), String.format("ID%d", voiceMetadata.getPiperVoiceID()), null);
	}

	public static VoiceListItem forElevenLabsVoice(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		ElevenLabsVoice voice,
		ElevenLabsModel model) {
		VoiceID voiceID = voice.toVoiceID(model);
		return new VoiceListItem(voiceExplorerPanel, textToSpeech, voiceID,
			voice.getName(), voice.getGender(), "", voiceID.toVoiceIDString());
	}

	private VoiceListItem(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		VoiceID voiceID,
		String voiceName,
		Gender gender,
		String idLabelText,
		@CheckForNull String detailText) {
		this.textToSpeech = textToSpeech;
		this.voiceName = voiceName;
		this.gender = gender;

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		this.setToolTipText(String.format("<html>%s %s (%s)<br>Click to copy the voice ID</html>",
			voiceID, voiceName, gender));

		JPanel speakerPanel = new JPanel();
		speakerPanel.setOpaque(false);

		GroupLayout speakerLayout = new GroupLayout(speakerPanel);
		speakerPanel.setLayout(speakerLayout);


		JLabel nameLabel = new JLabel(voiceName);
		nameLabel.setForeground(Color.white);

		String genderString;
		if (gender == Gender.MALE) {
			genderString = "(M)";
		}
		else if (gender == Gender.FEMALE) {
			genderString = "(F)";
		}
		else {
			genderString = "(?)";
		}

		JLabel genderLabel = new JLabel(genderString);
		genderLabel.setForeground(Color.white);

		JLabel copiedLabel = new JLabel("Copied!");
		copiedLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		copiedLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
		copiedLabel.setVisible(false);

		Timer copiedTimer = new Timer(COPIED_DISPLAY_MS, event -> copiedLabel.setVisible(false));
		copiedTimer.setRepeats(false);
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new StringSelection(voiceID.toVoiceIDString()), null);
				copiedLabel.setVisible(true);
				copiedTimer.restart();
			}
		});

		JLabel piperIdLabel = new JLabel(idLabelText);
		int idLabelWidth = idLabelText.isEmpty() ? 0 : 35;

		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(copiedLabel)
			.addComponent(piperIdLabel, idLabelWidth, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(idLabelWidth == 0 ? 0 : 5)
			.addComponent(nameLabel)
			.addGap(5).addComponent(genderLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);

		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup()
			.addGap(5)
			.addComponent(piperIdLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(genderLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(copiedLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addGap(5));


		JButton playButton = new JButton(PLAY_BUTTON_DISABLED);
		SwingUtil.removeButtonDecorations(playButton);
		playButton.setPreferredSize(
			new Dimension(PLAY_BUTTON_DISABLED.getIconWidth(), PLAY_BUTTON_DISABLED.getIconHeight()));
		playButton.addActionListener(

			event -> {
				try {
					if (textToSpeech.isModelActive(voiceID.getModelName())) {
						textToSpeech.speak(
							voiceID,
							textToSpeech.expandShortenedPhrases(voiceExplorerPanel.getSpeechText().getText()),
							0,
							MagicUsernames.VOICE_EXPLORER);
					}
					else {
						log.info("Model {} is currently not running.", voiceID.getModelName());
					}
				} catch (ModelLocalUnavailableException e) {
					throw new RuntimeException(e);
				}
			});

		JPanel textPanel = speakerPanel;
		if (detailText != null) {
			JLabel detailLabel = new JLabel(detailText);
			detailLabel.setFont(FontManager.getRunescapeSmallFont());
			detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			detailLabel.setBorder(new EmptyBorder(0, 5, 4, 0));

			textPanel = new JPanel(new BorderLayout());
			textPanel.setOpaque(false);
			textPanel.add(speakerPanel, BorderLayout.CENTER);
			textPanel.add(detailLabel, BorderLayout.SOUTH);
		}

		BorderLayout rootLayout = new BorderLayout();
		this.setLayout(rootLayout);
		this.add(textPanel, BorderLayout.CENTER);
		this.add(playButton, BorderLayout.EAST);

		revalidate();

		Runnable refresh = () -> SwingUtilities.invokeLater(() -> {
			boolean active = textToSpeech.isModelActive(voiceID.getModelName());
			playButton.setIcon(active ? PLAY_BUTTON : PLAY_BUTTON_DISABLED);
			playButton.setEnabled(active);
		});
		refresh.run();

		listener = new TextToSpeech.TextToSpeechListener() {
			@Override
			public void onPiperStart(Piper piper) {
				refresh.run();
			}

			@Override
			public void onPiperExit(Piper piper) {
				refresh.run();
			}

			@Override
			public void onElevenLabsStart() {
				refresh.run();
			}

			@Override
			public void onElevenLabsStop() {
				refresh.run();
			}
		};
		textToSpeech.addTextToSpeechListener(listener);
	}

	public void dispose() {
		textToSpeech.removeTextToSpeechListener(listener);
	}
}
