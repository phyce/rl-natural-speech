package dev.phyce.naturalspeech.ui.panels;

import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import net.runelite.client.util.ImageUtil;

public final class StaticImages {

	public static final ImageIcon ON_SWITCHER;
	public static final ImageIcon OFF_SWITCHER;

	static {
		BufferedImage onSwitcher = ImageUtil.loadImageResource(MainSettingsPanel.class, "switcher_on.png");
		ON_SWITCHER = new ImageIcon(onSwitcher);
		OFF_SWITCHER = new ImageIcon(ImageUtil.flipImage(
			ImageUtil.luminanceScale(
				ImageUtil.grayscaleImage(onSwitcher),
				0.61f
			),
			true,
			false
		));
	}
}
