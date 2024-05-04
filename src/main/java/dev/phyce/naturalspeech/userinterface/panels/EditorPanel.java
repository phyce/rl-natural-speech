package dev.phyce.naturalspeech.userinterface.panels;

import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

public class EditorPanel extends PluginPanel {
	public EditorPanel() {
		super(false);
		JLabel titleLabel = new JLabel("Editor", JLabel.CENTER);
		titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
		titleLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
		add(titleLabel);
	}

	@Override
	public void onActivate() {
		super.onActivate();

		setVisible(true);
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		setVisible(false);
	}
}
