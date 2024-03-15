package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

@Singleton
public class TopLevelPanel extends PluginPanel {
	private final MaterialTabGroup tabGroup;
	private final CardLayout layout;
	private final JPanel content;

	@Getter
	private final MainSettingsPanel mainSettingsPanel;
	@Getter
	private final VoiceExplorerPanel voiceExplorerPanel;
	private final MaterialTab mainSettingsTab;
	//	@Getter
	//	private final EditorPanel editorPanel;

	private boolean active = false;
	private PluginPanel current;
	private boolean removeOnTabChange;

	@Inject
	TopLevelPanel(
		MainSettingsPanel mainSettingsPanel,
		VoiceExplorerPanel voiceExplorerPanel
		//			EditorPanel editorPanel
	) {
		super(false);

		this.mainSettingsPanel = mainSettingsPanel;
		this.voiceExplorerPanel = voiceExplorerPanel;
		//		this.editorPanel = editorPanel;

		tabGroup = new MaterialTabGroup();
		tabGroup.setLayout(new GridLayout(1, 0, 7, 7));
		tabGroup.setBorder(new EmptyBorder(10, 10, 0, 10));

		content = new JPanel();
		layout = new CardLayout();
		content.setLayout(layout);

		setLayout(new BorderLayout());
		add(tabGroup, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		// Main Settings Panel Tab
		//		MaterialTab mainSettingsTab = addTab(this.mainSettingsPanel, "config_icon.png", "Natural Speech");
		mainSettingsTab = addTab(this.mainSettingsPanel, "config_icon.png", "Natural Speech Settings");
		tabGroup.select(mainSettingsTab);

		// Speaker Explorer Panel Tab
		addTab(voiceExplorerPanel, "profile_icon.png", "Voice Explorer");

		// Editor Panel Tab
		//		addTab(editorPanel, "plugin_hub_icon.png", "Editor");

	}

	private MaterialTab addTab(PluginPanel panel, String image, String tooltip) {
		MaterialTab materialTab = new MaterialTab(
			new ImageIcon(ImageUtil.loadImageResource(TopLevelPanel.class, image)),
			tabGroup, null);
		materialTab.setToolTipText(tooltip);
		tabGroup.addTab(materialTab);

		content.add(image, panel.getWrappedPanel());

		materialTab.setOnSelectEvent(() -> {
			switchTo(image, panel, false);
			return true;
		});
		return materialTab;
	}

	private MaterialTab addTab(Provider<? extends PluginPanel> panelProvider, String image, String tooltip) {
		MaterialTab materialTab = new MaterialTab(
			new ImageIcon(ImageUtil.loadImageResource(TopLevelPanel.class, image)),
			tabGroup, null);
		materialTab.setToolTipText(tooltip);
		tabGroup.addTab(materialTab);

		materialTab.setOnSelectEvent(() ->
		{
			PluginPanel panel = panelProvider.get();
			content.add(image, panel.getWrappedPanel());
			switchTo(image, panel, true);
			return true;
		});
		return materialTab;
	}

	private void switchTo(String cardName, PluginPanel panel, boolean removeOnTabChange) {
		boolean doRemove = this.removeOnTabChange;
		PluginPanel prevPanel = current;
		if (active) {
			prevPanel.onDeactivate();
			panel.onActivate();
		}

		current = panel;
		this.removeOnTabChange = removeOnTabChange;

		layout.show(content, cardName);

		if (doRemove) {
			content.remove(prevPanel.getWrappedPanel());
		}

		content.revalidate();
	}

	@Override
	public void onActivate() {
		// FIXME(Louis) First tab always defaults to being visible after deactivate then reactivate
		// Despite the code running water tight, strange behavior.
		// Possible that super onActivate sets visibility
		active = true;
		current.onDeactivate();
		// FIXME(Louis): Temporary workaround the issue where the main tab rendering on top after toggling panel
		// Might as well just select it automatically so intended "correct" behavior for now.
		tabGroup.select(mainSettingsTab);
	}

	@Override
	public void onDeactivate() {
		active = false;
		current.onDeactivate();
	}
}
