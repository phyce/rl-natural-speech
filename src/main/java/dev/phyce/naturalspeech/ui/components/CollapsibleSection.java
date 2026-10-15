package dev.phyce.naturalspeech.ui.components;

import dev.phyce.naturalspeech.ui.panels.MainSettingsPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

public class CollapsibleSection extends JPanel {
	private static final ImageIcon EXPAND_ICON;
	private static final ImageIcon RETRACT_ICON;

	static {
		BufferedImage sectionRetractIcon =
			ImageUtil.loadImageResource(MainSettingsPanel.class, "section_icons/arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2));
	}

	@Getter
	private final JPanel content;
	private final JButton toggleButton;
	@Setter
	private Runnable onExpanded;

	public CollapsibleSection(String name, String description, LayoutManager contentLayout, boolean expanded) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
		setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(PluginPanel.BORDER_OFFSET, 0, PluginPanel.BORDER_OFFSET, 0)
		));

		JPanel header = new JPanel(new BorderLayout());
		header.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		add(header);

		toggleButton = new JButton(RETRACT_ICON);
		toggleButton.setPreferredSize(new Dimension(18, 0));
		toggleButton.setBorder(new EmptyBorder(0, 0, 0, 5));
		SwingUtil.removeButtonDecorations(toggleButton);
		toggleButton.addActionListener(event -> toggle());
		header.add(toggleButton, BorderLayout.WEST);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setToolTipText("<html>" + name + ":<br>" + description + "</html>");
		header.add(nameLabel, BorderLayout.CENTER);

		content = new JPanel(contentLayout);
		content.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
		add(content);

		MouseAdapter toggleOnClick = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				toggle();
			}
		};
		nameLabel.addMouseListener(toggleOnClick);
		header.addMouseListener(toggleOnClick);

		setExpanded(expanded);
	}

	public boolean isExpanded() {
		return content.isVisible();
	}

	public void setExpanded(boolean expanded) {
		content.setVisible(expanded);
		toggleButton.setIcon(expanded ? RETRACT_ICON : EXPAND_ICON);
		toggleButton.setToolTipText(expanded ? "Retract" : "Expand");
		SwingUtilities.invokeLater(content::revalidate);
	}

	private void toggle() {
		setExpanded(!isExpanded());
		if (isExpanded() && onExpanded != null) onExpanded.run();
	}
}
