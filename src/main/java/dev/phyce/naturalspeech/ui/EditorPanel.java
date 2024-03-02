package dev.phyce.naturalspeech.ui;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class EditorPanel extends PluginPanel {

    public EditorPanel() {
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
