package dev.phyce.naturalspeech.userinterface.voiceexplorer;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.statics.PluginResources;
import dev.phyce.naturalspeech.texttospeech.SpeechManager;
import dev.phyce.naturalspeech.texttospeech.VoiceManager;
import dev.phyce.naturalspeech.userinterface.components.IconTextField;
import dev.phyce.naturalspeech.utils.ChatHelper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class VoiceListItem extends JPanel {

	private final SpeechManager speechManager;
	private final ChatHelper chatHelper;
	@Getter
	private final VoiceMetadata voiceMetadata;
	private final VoiceManager voiceManager;
	private final IconTextField speakTextField;

	private final JPanel hintPanel;
	private final JPanel contentPanel;
	private final JLabel nameLabel;
	private final JLabel genderLabel;
	private final JLabel idLabel;

	public interface Factory {
		VoiceListItem create(IconTextField speakTextField, VoiceMetadata voiceMetadata);
	}

	@Inject
	public VoiceListItem(
		SpeechManager speechManager,
		ChatHelper chatHelper,
		VoiceManager voiceManager,
		@Assisted IconTextField speakTextField,
		@Assisted VoiceMetadata voiceMetadata
	) {
		this.speechManager = speechManager;
		this.chatHelper = chatHelper;
		this.voiceManager = voiceManager;
		this.voiceMetadata = voiceMetadata;
		this.speakTextField = speakTextField;


		JPanel speakerPanel = new JPanel();
		speakerPanel.setOpaque(false);

		GroupLayout speakerLayout = new GroupLayout(speakerPanel);
		speakerPanel.setLayout(speakerLayout);


		nameLabel = new JLabel("<html>" + voiceMetadata.getName() + "</html>");

		String genderString;
		switch (voiceMetadata.getGender()) {
			case MALE:
				genderString = "(M)";
				break;

			case FEMALE:
				genderString = "(F)";
				break;
			case OTHER:
			default:
				genderString = "(?)";
				break;
		}

		genderLabel = new JLabel(genderString);

		idLabel = new JLabel("<html>" + voiceMetadata.voiceId.id + "</html>");

		speakerLayout.setHorizontalGroup(speakerLayout
			.createSequentialGroup()
			.addGap(5)
			.addComponent(idLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
				GroupLayout.PREFERRED_SIZE)
			.addGap(5)
			.addComponent(nameLabel, 0, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
			.addGap(0, 5, 5)
			.addComponent(genderLabel));

		int lineHeight = (int) (nameLabel.getFontMetrics(nameLabel.getFont()).getHeight() * 1.5);

		speakerLayout.setVerticalGroup(speakerLayout.createParallelGroup()
			.addGap(5)
			.addComponent(idLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addComponent(nameLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight * 2)
			.addComponent(genderLabel, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight)
			.addGap(5));


		JButton playButton = new JButton(PluginResources.PLAY_BUTTON_ICON);
		SwingUtil.removeButtonDecorations(playButton);
		playButton.setPreferredSize(
			new Dimension(PluginResources.PLAY_BUTTON_DISABLED_ICON.getIconWidth(),
				PluginResources.PLAY_BUTTON_DISABLED_ICON.getIconHeight()));
		playButton.addActionListener(event -> {
				speechManager.silence((lineName) -> lineName.equals(Names.VOICE_EXPLORER));
				speechManager.speak(
					voiceMetadata.voiceId,
					chatHelper.renderReplacements(speakTextField.getText()),
					() -> 0f,
					Names.VOICE_EXPLORER
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
				if (e.getButton() == MouseEvent.BUTTON1) {
					hintPanel.setVisible(true);

					// copy voiceid to clipboard
					copyVoice(voiceMetadata);

					Timer timer = new Timer(1500, event -> {
						hintPanel.setVisible(false);
						revalidate();
					});
					timer.setRepeats(false);
					timer.start();

					revalidate();
				}
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



		JMenuItem blacklistMenu = new JMenuItem(getBlacklistText());
		blacklistMenu.addActionListener(e -> {
			toggleBlacklist();
			blacklistMenu.setText(getBlacklistText());
		});
		JMenuItem copyMenu = new JMenuItem("Copy Voice ID");
		copyMenu.addActionListener(e -> copyVoice(voiceMetadata));
		buildContextMenu(contentPanel, blacklistMenu, copyMenu);


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

		updateColors();
		revalidate();
	}

	private void toggleBlacklist() {
		if (voiceManager.isBlacklisted(voiceMetadata.voiceId)) {
			voiceManager.unblacklist(voiceMetadata.voiceId);
		} else {
			voiceManager.blacklist(voiceMetadata.voiceId);
		}

		updateColors();
	}

	private void updateColors() {
		if (voiceManager.isBlacklisted(voiceMetadata.voiceId)) {
			nameLabel.setForeground(Color.RED);
			genderLabel.setForeground(Color.RED);
			idLabel.setForeground(Color.RED);
		} else {
			nameLabel.setForeground(Color.WHITE);
			genderLabel.setForeground(Color.WHITE);
			idLabel.setForeground(null);
		}
	}

	private String getBlacklistText() {
		boolean isBlacklisted = voiceManager.isBlacklisted(voiceMetadata.voiceId);
		return isBlacklisted ? "Whitelist" : "Blacklist";
	}

	private static void copyVoice(VoiceMetadata voiceMetadata) {
		StringSelection contents = new StringSelection(voiceMetadata.voiceId.toVoiceIDString());
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);
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
