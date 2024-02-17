package dev.phyce.naturalspeech;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Color;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;

import static javax.swing.text.html.CSS.Attribute.BACKGROUND_COLOR;

public class NaturalSpeechPanel extends PluginPanel {
    public NaturalSpeechPanel() {
        super();
        setLayout(new GridLayout(0, 1)); // Use GridLayout for simplicity

        // Title
        JLabel titleLabel = new JLabel("Natural Speech", JLabel.CENTER);
        titleLabel.setFont(new Font("Sans", Font.BOLD, 24));
        titleLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(titleLabel);

        // Instructions Link
        JLabel instructionsLink = new JLabel("<html>For instructions, click <a href='#'>here</a>.</html>", JLabel.CENTER);
        instructionsLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        instructionsLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://github.com/phyce/rl-natural-speech"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        add(instructionsLink);

        // TTS Engine Select Box
        JComboBox<String> ttsEngineSelect = new JComboBox<>(new String[]{"Piper"});
        ttsEngineSelect.setToolTipText("At the moment, only one TTS engine is supported.");
        ttsEngineSelect.setEnabled(false); // Disabled select box
        add(ttsEngineSelect);

        // Status
        JLabel statusLabel = new JLabel("Status: Stopped");
        statusLabel.setBorder(new EmptyBorder(0, 5, 0, 0));;
        statusLabel.setToolTipText("TTS engine status");
        add(statusLabel);

//        // File Browse Input (For simplicity, using a text field to paste location)
//        JTextField filePathField = new JTextField("Path to TTS engine...");
//        JButton launchButton = new JButton(new ImageIcon(new ImageIcon("path/to/green_triangle_icon.png").getImage().getScaledInstance(20, 20, Image.SCALE_DEFAULT))); // Replace "path/to/green_triangle_icon.png" with your actual path
//        launchButton.addActionListener(e -> {
//            // Launch action
//        });
//        JPanel fileLaunchPanel = new JPanel(new BorderLayout());
//        fileLaunchPanel.add(filePathField, BorderLayout.CENTER);
//        fileLaunchPanel.add(launchButton, BorderLayout.EAST);
//        add(fileLaunchPanel);
        JTextField filePathField = new JTextField("Select a TTS engine path...");
        filePathField.setToolTipText("TTS engine binary file path");
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                int returnValue = fileChooser.showOpenDialog(NaturalSpeechPanel.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    filePathField.setText(fileChooser.getSelectedFile().getPath());
                }
            }
        });

        JPanel fileBrowsePanel = new JPanel(new BorderLayout(5, 0));
        fileBrowsePanel.setBorder(new EmptyBorder(0, 0, 15, 0));
        fileBrowsePanel.add(filePathField, BorderLayout.CENTER);
        fileBrowsePanel.add(browseButton, BorderLayout.EAST);

        // Adding the file browse panel to the main panel
        add(fileBrowsePanel);

        // Libritts.onnx Input Field
        JTextField librittsField = new JTextField("libritts.onnx");
        librittsField.setEditable(false); // Make the field not editable
        librittsField.setToolTipText("Currently only one voice model available"); // Tooltip for additional info
//        librittsField.setBackground(Color.WHITE); // Optional: set background color to indicate it's not editable

        // Download Button
        JButton downloadButton = new JButton("Download");
        downloadButton.setToolTipText("Download the libritts.onnx TTS engine file"); // Tooltip for the download button
        downloadButton.addActionListener(e -> {
            // Placeholder for future download functionality
            System.out.println("Download action will be implemented here.");
        });

        JPanel downloadPanel = new JPanel(new BorderLayout(5, 0));
        downloadPanel.setBorder(new EmptyBorder(0, 0, 15, 0));;
        downloadPanel.add(librittsField, BorderLayout.CENTER);
        downloadPanel.add(downloadButton, BorderLayout.EAST);
        add(downloadPanel);
    }
}
