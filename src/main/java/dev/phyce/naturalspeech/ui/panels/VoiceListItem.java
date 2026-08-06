package dev.phyce.naturalspeech.ui.panels;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.enums.SpeechEngine;
import dev.phyce.naturalspeech.tts.ModelRepository;
import dev.phyce.naturalspeech.exceptions.ModelLocalUnavailableException;
import dev.phyce.naturalspeech.tts.piper.Piper;
import dev.phyce.naturalspeech.tts.TextToSpeech;
import dev.phyce.naturalspeech.tts.VoiceID;
import dev.phyce.naturalspeech.tts.elevenlabs.ElevenLabsVoice;
import dev.phyce.naturalspeech.tts.nativespeech.NativeSpeechEngine;
import dev.phyce.naturalspeech.tts.nativespeech.NativeVoice;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceListItem extends JPanel {

	@Getter
	private final String voiceName;
	@Getter
	private final Gender gender;
	@Getter
	private final VoiceID voiceID;
	@Getter
	private final SpeechEngine engine;

	private final TextToSpeech textToSpeech;
	private final TextToSpeech.TextToSpeechListener listener;

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
			voiceMetadata.getName(), voiceMetadata.getGender(),
			String.format("ID%d", voiceMetadata.getPiperVoiceID()));
	}

	public static VoiceListItem forSystemVoice(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		NativeVoice voice) {
		return new VoiceListItem(voiceExplorerPanel, textToSpeech, voice.toVoiceID(),
			voice.getSystemName(), voice.getGender(), voice.getId());
	}

	public static VoiceListItem forElevenLabsVoice(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		ElevenLabsVoice voice) {
		// ElevenLabs ids are 20 opaque characters and will not fit the id column; the full id is in
		// the tooltip, and clicking the row copies it.
		return new VoiceListItem(voiceExplorerPanel, textToSpeech, voice.toVoiceID(),
			voice.getName(), voice.getGender(), "EL");
	}

	private VoiceListItem(
		VoiceExplorerPanel voiceExplorerPanel,
		TextToSpeech textToSpeech,
		VoiceID voiceID,
		String voiceName,
		Gender gender,
		String idLabelText) {
		this.textToSpeech = textToSpeech;
		this.voiceID = voiceID;
		this.voiceName = voiceName;
		this.gender = gender;
		this.engine = TextToSpeech.engineOfModel(voiceID.getModelName());

		this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		this.setOpaque(true);
		this.setToolTipText(String.format("<html>%s (%s)<br>Click to copy the voice id</html>",
			voiceID.toVoiceIDString(), gender));

		// The voice settings and the Custom Characters tab all take an id string, and ElevenLabs ids
		// are impossible to retype from memory, so make the row hand it over.
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				copyVoiceIDToClipboard();
			}
		});

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

		JLabel idLabel = new JLabel(idLabelText);

		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(idLabel, 35, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(5)
			.addComponent(nameLabel)
			.addGap(5).addComponent(genderLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);

		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup()
			.addGap(5)
			.addComponent(idLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(genderLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addGap(5));

		JButton playButton = new JButton(PLAY_BUTTON_DISABLED);
		SwingUtil.removeButtonDecorations(playButton);
		playButton.setPreferredSize(
			new Dimension(PLAY_BUTTON_DISABLED.getIconWidth(), PLAY_BUTTON_DISABLED.getIconHeight()));
		playButton.addActionListener(
			event -> {
				if (!textToSpeech.isModelActive(voiceID.getModelName())) {
					log.info("{} is currently not running.", voiceID.getModelName());
					return;
				}

				try {
					textToSpeech.speak(
						voiceID,
						textToSpeech.expandShortenedPhrases(voiceExplorerPanel.getSpeechText().getText()),
						0,
						"&VoiceExplorer");
				} catch (ModelLocalUnavailableException e) {
					throw new RuntimeException(e);
				}
			});

		BorderLayout rootLayout = new BorderLayout();
		this.setLayout(rootLayout);
		this.add(speakerPanel, BorderLayout.CENTER);
		this.add(playButton, BorderLayout.EAST);

		revalidate();

		Runnable refresh = () -> SwingUtilities.invokeLater(() -> {
			boolean active = textToSpeech.isModelActive(voiceID.getModelName());
			playButton.setIcon(active? PLAY_BUTTON: PLAY_BUTTON_DISABLED);
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
			public void onNativeSpeechStart(NativeSpeechEngine speechEngine) {
				refresh.run();
			}

			@Override
			public void onNativeSpeechExit(NativeSpeechEngine speechEngine) {
				refresh.run();
			}

			@Override
			public void onStop() {
				refresh.run();
			}
		};
		textToSpeech.addTextToSpeechListener(listener);
	}

	private void copyVoiceIDToClipboard() {
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(voiceID.toVoiceIDString()), null);
			log.debug("Copied {} to the clipboard", voiceID);
		}
		catch (IllegalStateException | HeadlessException e) {
			log.debug("Could not access the clipboard", e);
		}
	}

	public void dispose() {
		textToSpeech.removeTextToSpeechListener(listener);
	}
}
