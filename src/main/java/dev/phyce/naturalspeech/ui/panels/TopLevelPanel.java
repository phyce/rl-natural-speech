package dev.phyce.naturalspeech.ui.panels;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.Getter;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TopLevelPanel extends PluginPanel {
	private final MaterialTabGroup tabGroup;
	private final CardLayout layout;
	private final JPanel content;

	@Getter
	private final MainSettingsPanel mainSettingsPanel;
	@Getter
	private final SpeakerExplorerPanel speakerExplorerPanel;
	@Getter
	private final EditorPanel editorPanel;

	private boolean active = false;
	private PluginPanel current;
	private boolean removeOnTabChange;

	private EventBus eventBus;

	@Inject
	TopLevelPanel(
			EventBus eventBus,
			MainSettingsPanel mainSettingsPanel,
			SpeakerExplorerPanel speakerExplorerPanel,
			EditorPanel editorPanel
	) {
		super(false);

		this.eventBus = eventBus;
		this.mainSettingsPanel = mainSettingsPanel;
		this.speakerExplorerPanel = speakerExplorerPanel;
		this.editorPanel = editorPanel;


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
		MaterialTab mainSettingsTab = addTab(this.mainSettingsPanel, "config_icon.png", "Natural Speech");
		tabGroup.select(mainSettingsTab);

		// Speaker Explorer Panel Tab
		addTab(speakerExplorerPanel, "profile_icon.png", "Speaker Explorer");

		// Editor Panel Tab
		addTab(editorPanel, "plugin_hub_icon.png", "Editor");

	}

	private MaterialTab addTab(PluginPanel panel, String image, String tooltip) {
		MaterialTab mt = new MaterialTab(
				new ImageIcon(ImageUtil.loadImageResource(TopLevelPanel.class, image)),
				tabGroup, null);
		mt.setToolTipText(tooltip);
		tabGroup.addTab(mt);

		content.add(image, panel.getWrappedPanel());
		eventBus.register(panel);

		mt.setOnSelectEvent(() ->
		{
			switchTo(image, panel, false);
			return true;
		});
		return mt;
	}

	private MaterialTab addTab(Provider<? extends PluginPanel> panelProvider, String image, String tooltip) {
		MaterialTab mt = new MaterialTab(
				new ImageIcon(ImageUtil.loadImageResource(TopLevelPanel.class, image)),
				tabGroup, null);
		mt.setToolTipText(tooltip);
		tabGroup.addTab(mt);

		mt.setOnSelectEvent(() ->
		{
			PluginPanel panel = panelProvider.get();
			content.add(image, panel.getWrappedPanel());
			eventBus.register(panel);
			switchTo(image, panel, true);
			return true;
		});
		return mt;
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
			eventBus.unregister(prevPanel);
		}

		content.revalidate();
	}

	@Override
	public void onActivate() {
		active = true;
		current.onActivate();
	}

	@Override
	public void onDeactivate() {
		active = false;
		current.onDeactivate();
	}

}
