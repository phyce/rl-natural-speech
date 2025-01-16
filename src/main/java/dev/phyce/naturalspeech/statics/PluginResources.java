package dev.phyce.naturalspeech.statics;

import com.google.common.io.Resources;
import dev.phyce.naturalspeech.NaturalSpeechPlugin;
import dev.phyce.naturalspeech.configs.VoiceSettings;
import dev.phyce.naturalspeech.configs.ReplacementsJSON;
import dev.phyce.naturalspeech.texttospeech.engine.windows.speechapi5.SAPI5Process;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.swing.ImageIcon;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
public final class PluginResources {

	public static final ReplacementsJSON[] BUILT_IN_REPLACEMENTS;

	static {
		String json;
		try {
			URL resource = Resources.getResource(NaturalSpeechPlugin.class, "builtin_replacements.json");
			json = Resources.toString(resource, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error("Failed to load built-in abbreviations from Resources.", e);
			json = "[]";
		}

		BUILT_IN_REPLACEMENTS = RuneLiteAPI.GSON.fromJson(json, ReplacementsJSON[].class);
	}

	@NonNull
	public static final String DEFAULT_VOICE_CONFIG_JSON;

	static {
		String result;
		try {
			result = Resources.toString(Resources.getResource(VoiceSettings.class, "default_speaker_config.json"),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error("Default voice config file failed to load from resources. " +
					"Either file path is incorrect, or the file doesn't exist.", e);

			result = "{}";
		}
		DEFAULT_VOICE_CONFIG_JSON = result;
	}


	@NonNull
	public static final URL WSAPI5_CSHARP_RUNTIME = Resources.getResource(SAPI5Process.class, "WSAPI5.cs");
	@NonNull
	public static final URL MODEL_REPO = Resources.getResource(NaturalSpeechPlugin.class, "model_repository.json");

	@NonNull
	public static final BufferedImage NATURAL_SPEECH_ICON =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "icon.png");
	@NonNull
	public static final BufferedImage INGAME_NATURAL_SPEECH_SMALL_ICON =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "small_icon.png");
	public static final BufferedImage INGAME_NATURAL_SPEECH_SMALL_ICON_DISABLED =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "small_icon_disabled.png");
	@NonNull
	public static final BufferedImage INGAME_MUTE_ICON =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "mute.png");
	@NonNull
	public static final BufferedImage INGAME_UNMUTE_ICON =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "unmute.png");
	@NonNull
	public static final BufferedImage INGAME_CHECKBOX =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "checkbox.png");
	@NonNull
	public static final BufferedImage INGAME_CHECKBOX_CHECKED =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "checkbox_checked.png");
	@NonNull
	public static final BufferedImage INGAME_CHECKMARK =
			ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "checkmark.png");
	@NonNull
	public static final BufferedImage INGAME_XMARK = ImageUtil.loadImageResource(NaturalSpeechPlugin.class, "xmark.png");

	@NonNull
	public static final ImageIcon MAIN_SETTINGS_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "icon.png"));
	@NonNull
	public static final ImageIcon VOICE_EXPLORER_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "profile_icon.png"));
	@NonNull
	public static final ImageIcon VOICE_HUB_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "voice_hub_icon.png"));
	@NonNull
	public static final ImageIcon SPEECH_TEXT_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "speechText.png"));
	@NonNull
	public static final ImageIcon START_TEXT_TO_SPEECH_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "start.png"));
	@NonNull
	public static final ImageIcon STOP_TEXT_TO_SPEECH_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "stop.png"));

	@NonNull
	public static final ImageIcon ON_SWITCHER;
	@NonNull
	public static final ImageIcon OFF_SWITCHER;

	static {
		BufferedImage onSwitcher = ImageUtil.loadImageResource(PluginResources.class, "switcher_on.png");
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

	@NonNull
	public static final ImageIcon PLAY_BUTTON_ICON;
	@NonNull
	public static final ImageIcon PLAY_BUTTON_DISABLED_ICON;

	static {
		BufferedImage image = ImageUtil.loadImageResource(PluginResources.class, "start.png");
		PLAY_BUTTON_ICON = new ImageIcon(image.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
		PLAY_BUTTON_DISABLED_ICON = new ImageIcon(
				ImageUtil.luminanceScale(ImageUtil.grayscaleImage(image), 0.61f)
						.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
	}


	@NonNull
	public static final ImageIcon SECTION_EXPAND_ICON;
	@NonNull
	public static final ImageIcon SECTION_RETRACT_ICON;

	static {
		BufferedImage sectionRetractIcon =
				ImageUtil.loadImageResource(PluginResources.class, "arrow_right.png");
		sectionRetractIcon = ImageUtil.luminanceOffset(sectionRetractIcon, -121);
		SECTION_EXPAND_ICON = new ImageIcon(sectionRetractIcon);
		final BufferedImage sectionExpandIcon = ImageUtil.rotateImage(sectionRetractIcon, Math.PI / 2);
		SECTION_RETRACT_ICON = new ImageIcon(sectionExpandIcon);
	}

	@NonNull
	public static final ImageIcon SEARCH_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "search.png"));
	@NonNull
	public static final ImageIcon LOADING_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "loading_spinner.gif"));
	@NonNull
	public static final ImageIcon LOADING_DARKER_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "loading_spinner_darker.gif"));
	@NonNull
	public static final ImageIcon ERROR_ICON =
			new ImageIcon(ImageUtil.loadImageResource(PluginResources.class, "error.png"));

	public static final ImageIcon CONFIG_ICON =
			new ImageIcon(ImageUtil.resizeImage(ImageUtil.loadImageResource(PluginResources.class, "mdi_cog.png"), 14, 14));

	public static final ImageIcon CONFIG_ICON_HOVER =
			new ImageIcon(ImageUtil.luminanceOffset(CONFIG_ICON.getImage(), -100));

	public static final ImageIcon HELP_ICON =
			new ImageIcon(ImageUtil.resizeImage(ImageUtil.loadImageResource(PluginResources.class, "help_icon.png"), 14, 14));

	public static final ImageIcon HELP_ICON_HOVER =
			new ImageIcon(ImageUtil.luminanceOffset(HELP_ICON.getImage(), -100));
}
