package dev.phyce.naturalspeech.userinterface;

import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.audio.AudioEngine;
import dev.phyce.naturalspeech.statics.PluginResources;
import static dev.phyce.naturalspeech.statics.PluginResources.INGAME_NATURAL_SPEECH_SMALL_ICON;
import dev.phyce.naturalspeech.userinterface.mainsettings.MainSettingsPanel;
import dev.phyce.naturalspeech.userinterface.voiceexplorer.VoiceExplorerPanel;
import dev.phyce.naturalspeech.userinterface.voicehub.VoiceHubPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

public class TopLevelPanel extends PluginPanel implements PluginModule {
	private final MaterialTabGroup tabGroup;
	private final CardLayout layout;
	private final JPanel content;
	private final ClientToolbar clientToolbar;
	private boolean active = false;
	private PluginPanel current;
	private boolean removeOnTabChange;
	private NavigationButton panelNavButton;

	@Inject
	TopLevelPanel(
			NaturalSpeechPlugin plugin,
			MainSettingsPanel mainSettingsPanel,
			VoiceExplorerPanel voiceExplorerPanel,
			VoiceHubPanel voiceHubPanel,
			ConfigManager configManager,
			ClientToolbar clientToolbar,
			AudioEngine audioEngine
	) {
		super(false);
		this.clientToolbar = clientToolbar;

		JPanel header = new JPanel();
		header.setLayout(new BorderLayout());
		header.setBorder(new EmptyBorder(5, 5, 5, 5));

		{
			JPanel headerNorth = new JPanel();
			headerNorth.setLayout(new OverlayLayout(headerNorth));

			{
				JButton helpButton = new JButton();
				SwingUtil.removeButtonDecorations(helpButton);
				helpButton.setPreferredSize(new Dimension(16, 16));
				helpButton.setSize(new Dimension(0, 0));
				helpButton.setIcon(PluginResources.HELP_ICON);
				helpButton.setRolloverIcon(PluginResources.HELP_ICON_HOVER);
				helpButton.setToolTipText("Visit Help Link");
				helpButton.addActionListener(ev -> LinkBrowser.browse("https://naturalspeech.dev"));

				JButton configButton = new JButton();
				SwingUtil.removeButtonDecorations(configButton);
				configButton.setPreferredSize(new Dimension(16, 16));
				configButton.setSize(new Dimension(0, 0));
				configButton.setIcon(PluginResources.CONFIG_ICON);
				configButton.setRolloverIcon(PluginResources.CONFIG_ICON_HOVER);
				configButton.setToolTipText("Open Plugin Config");
				configButton.addActionListener(ev -> plugin.openConfiguration());


				JPanel buttonsWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
				buttonsWrapper.setOpaque(false);

				buttonsWrapper.add(helpButton);
				buttonsWrapper.add(configButton);

				headerNorth.add(buttonsWrapper);
			}

			{
				JPanel titleWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
				JLabel label = new JLabel("Natural Speech", JLabel.CENTER);
				label.setFont(new Font("Sans", Font.BOLD, 12));
				label.setForeground(Color.WHITE);
				titleWrapper.add(label);

				JLabel version = new JLabel("v" + NaturalSpeechPlugin.VERSION);
				version.setFont(new Font("Sans", Font.PLAIN, 10));
				version.setForeground(Color.GRAY);
				titleWrapper.add(version);
				headerNorth.add(titleWrapper);
			}


			header.add(headerNorth, BorderLayout.NORTH);
		}


		tabGroup = new MaterialTabGroup();
		tabGroup.setLayout(new GridLayout(1, 0, 7, 7));
		tabGroup.setBorder(new EmptyBorder(5, 0, 5, 0));
		header.add(tabGroup, BorderLayout.CENTER);

		content = new JPanel();
		layout = new CardLayout();
		content.setLayout(layout);

		setLayout(new BorderLayout());
		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		addTab(voiceHubPanel, PluginResources.VOICE_HUB_ICON, "Voice Hub")
				.select();
		addTab(mainSettingsPanel, PluginResources.MAIN_SETTINGS_ICON, "Main Panel");
		addTab(voiceExplorerPanel, PluginResources.VOICE_EXPLORER_ICON, "Voice Explorer");

	}


	@Override
	public void startUp() {
		panelNavButton = NavigationButton.builder()
				.tooltip("Natural Speech")
				.icon(INGAME_NATURAL_SPEECH_SMALL_ICON)
				.priority(1)
				.panel(this)
				.build();
		clientToolbar.addNavigation(panelNavButton);

	}

	@Override
	public void shutDown() {
		clientToolbar.removeNavigation(panelNavButton);
	}

	private MaterialTab addTab(PluginPanel panel, ImageIcon icon, String tooltip) {
		MaterialTab materialTab = new MaterialTab(icon, tabGroup, null);
		materialTab.setToolTipText(tooltip);
		tabGroup.addTab(materialTab);

		content.add(panel.getClass().getSimpleName(), panel.getWrappedPanel());

		materialTab.setOnSelectEvent(() -> {
			switchTo(panel.getClass().getSimpleName(), panel, false);
			return true;
		});
		return materialTab;
	}

	//	private MaterialTab addTab(Provider<? extends PluginPanel> panelProvider, String image, String tooltip) {
	//		MaterialTab materialTab = new MaterialTab(
	//			new ImageIcon(ImageUtil.loadImageResource(TopLevelPanel.class, image)),
	//			tabGroup, null);
	//		materialTab.setToolTipText(tooltip);
	//		tabGroup.addTab(materialTab);
	//
	//		materialTab.setOnSelectEvent(() ->
	//		{
	//			PluginPanel panel = panelProvider.get();
	//			content.add(image, panel.getWrappedPanel());
	//			switchTo(image, panel, true);
	//			return true;
	//		});
	//		return materialTab;
	//	}

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

		if (doRemove) content.remove(prevPanel.getWrappedPanel());

		content.revalidate();
	}


	@Override
	public void onActivate() {
		active = true;
		//		current.onActivate();
	}

	@Override
	public void onDeactivate() {
		active = false;
		//		current.onDeactivate();
	}
}
