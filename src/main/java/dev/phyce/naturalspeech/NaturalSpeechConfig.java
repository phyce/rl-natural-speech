package dev.phyce.naturalspeech;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.CONFIG_GROUP;
import dev.phyce.naturalspeech.statics.ConfigKeys;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@SuppressWarnings({"SameReturnValue", "BooleanMethodIsAlwaysInverted"})
@ConfigGroup(CONFIG_GROUP)
public interface NaturalSpeechConfig extends Config {

	@ConfigSection(
		name="Voice Setting",
		description="",
		position=-1
	)
	String voiceSettingsSection = "voiceSettingsSection";

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.PERSONAL_VOICE,
		name="Personal voice ID",
		description="Choose one of the voices for your character, example: libritts:0",
		section=voiceSettingsSection

	)
	default String personalVoiceID() {
		return "";
	}

	@ConfigItem(
		position=2,
		keyName=ConfigKeys.GLOBAL_NPC_VOICE,
		name="Global NPC voice",
		description="Choose one voice for all NPCs, example: libritts:0",
		section=voiceSettingsSection
	)
	default String globalNpcVoice() {
		return "";
	}

	@ConfigItem(
		position=3,
		keyName=ConfigKeys.SYSTEM_VOICE,
		name="System message voice",
		description="Choose one of the voices for system messages, example: libritts:0",
		section=voiceSettingsSection

	)
	default String systemVoice() {
		return "libritts:1";
	}

	// region General Settings
	@ConfigSection(
		name="General",
		description="General settings",
		position=0
	)
	String generalSettingsSection = "generalSettingsSection";

	@ConfigItem(
			position=4,
			keyName=ConfigKeys.MASTER_MUTE,
			name="Mute Natural Speech",
			description="Entirely Mute all of Natural Speech",
			section=generalSettingsSection,
			hidden = true
	)
	default boolean masterMute() {
		return false;
	}

	@ConfigItem(
		position=4,
		keyName=ConfigKeys.MASTER_VOLUME,
		name="Volume",
		description="Volume percentage",
		section=generalSettingsSection

	)
	@Range(max=100)
	@Units(Units.PERCENT)
	default int masterVolume() {
		return 100;
	}

	@ConfigItem(
		position=5,
		keyName=ConfigKeys.FRIENDS_VOLUME_BOOST,
		name="Friends Volume Boost",
		description="Volume Percentage",
		section=generalSettingsSection

	)
	@Range(max=100)
	@Units(Units.PERCENT)
	default int friendsVolumeBoost() {
		return 0;
	}

	@ConfigItem(
		position=6,
		keyName=ConfigKeys.AUTO_START,
		name="Autostart the TTS engine",
		description="If executable and voice models available, autostart the TTS engine when the plugin loads",
		section=generalSettingsSection
	)
	default boolean autoStart() {return true;}

	@ConfigItem(
		position=7,
		keyName=ConfigKeys.DISTANCE_FADE,
		name="Fade distant sound",
		description="Players standing further away will sound quieter",
		section=generalSettingsSection

	)
	default boolean distanceFadeEnabled() {
		return true;
	}

	@ConfigItem(
		position=8,
		keyName=ConfigKeys.HOLD_SHIFT_RIGHT_CLICK_MENU,
		name="Hold shift for right-click menu",
		description="Only show the right-click menu when holding shift.",
		section=generalSettingsSection
	)
	default boolean holdShiftRightClickMenu() {
		return false;
	}


	// endregion

	// region Speech Generation Settings
	@ConfigSection(
		name=ConfigKeys.SPEECH_GENERATION,
		description="Settings to choose which messages should be played",
		position=1
	)
	String ttsOptionsSection = "ttsOptionsSection";

	@ConfigItem(
		keyName=ConfigKeys.PUBLIC_CHAT,
		name="Public messages",
		description="Enable text-to-speech to the public chat messages",
		section=ttsOptionsSection,
		position=0
	)
	default boolean publicChatEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.PRIVATE_CHAT,
		name="Private received messages",
		description="Enable text-to-speech to the received private chat messages",
		section=ttsOptionsSection,
		position=10
	)
	default boolean privateChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.PRIVATE_OUT_CHAT,
		name="Private sent out messages",
		description="Enable text-to-speech to the sent out private chat messages",
		section=ttsOptionsSection
		,
		position=20
	)
	default boolean privateOutChatEnabled() {
		return false;
	}

	//	@ConfigItem(
	//		keyName=ConfigKeys.FRIENDS_CHAT,
	//		name="Friends chat",
	//		description="Enable text-to-speech to friends chat messages",
	//		section=ttsOptionsSection,
	//		position=30
	//	)
	//	default boolean friendsChatEnabled() {
	//		return true;
	//	}

	@ConfigItem(
		keyName=ConfigKeys.CLAN_CHAT,
		name="Clan chat",
		description="Enable text-to-speech to the clan chat messages",
		section=ttsOptionsSection,
		position=40
	)
	default boolean clanChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.CLAN_GUEST_CHAT,
		name="Guest clan chat",
		description="Enable text-to-speech to the guest clan chat messages",
		section=ttsOptionsSection,
		position=50
	)
	default boolean clanGuestChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.GIM_CHAT,
		name="Group Ironman Chat",
		description="Enable text-to-speech to the group ironman chat messages",
		section=ttsOptionsSection,
		position=51
	)
	default boolean groupIronmanChatEnabled() {
		return false;
	}


	@ConfigItem(
		keyName=ConfigKeys.EXAMINE_CHAT,
		name="Examine text",
		description="Enable text-to-speech to the 'Examine' messages",
		section=ttsOptionsSection,
		position=60
	)
	default boolean examineChatEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.NPC_OVERHEAD,
		name="NPC overhead dialog",
		description="Enable text-to-speech to the overhead dialog for NPCs",
		section=ttsOptionsSection,
		position=70
	)
	default boolean npcOverheadEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.DIALOG,
		name="Dialogs",
		description="Enable text-to-speech to dialog text",
		section=ttsOptionsSection,
		position=80
	)
	default boolean dialogEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.REQUESTS,
		name="Trade/Challenge requests",
		description="Enable text-to-speech to trade and challenge requests",
		section=ttsOptionsSection,
		position=90
	)
	default boolean requestsEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.SYSTEM_MESSAGES,
		name="System messages",
		description="Generate text-to-speech to game's messages",
		section=ttsOptionsSection,
		position=100
	)
	default boolean systemMesagesEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.TWITCH_CHAT,
		name="Twitch chat plugin",
		description="Generate text-to-speech of the messages received from twitch viewers",
		section=ttsOptionsSection,
		position=100
	)
	default boolean twitchChatEnabled() {
		return false;
	}
	// endregion

	// region Mute Options
	@ConfigSection(
		name="Mute",
		description="Change mute settings here",
		position=2
	)
	String muteOptionsSection = "muteOptionsSection";

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.FRIENDS_ONLY_MODE,
		name="Friends only mode",
		description="Only generate text-to-speech for friends.",
		section=muteOptionsSection
	)
	default boolean friendsOnlyMode() {return false;}

	@ConfigItem(
		position=2,
		keyName=ConfigKeys.MUTE_OTHER_PLAYERS,
		name="Other Players",
		description="Do not generate text-to-speech for messages from other players",
		section=muteOptionsSection

	)
	default boolean muteOtherPlayers() {
		return false;
	}

	@ConfigItem(
		position=3,
		keyName=ConfigKeys.MUTE_SELF,
		name="Yourself",
		description="Do not generate text-to-speech for messages that you send",
		section=muteOptionsSection

	)
	default boolean muteSelf() {
		return false;
	}

	@ConfigItem(
		position=4,
		keyName=ConfigKeys.MUTE_GRAND_EXCHANGE,
		name="Grand Exchange",
		description="Disable text-to-speech in the grand exchange area",
		section=muteOptionsSection
	)
	default boolean muteGrandExchange() {
		return true;
	}


	@ConfigItem(
		position=6,
		keyName=ConfigKeys.MUTE_LEVEL_THRESHOLD,
		name="Below level",
		description="Do not generate text-to-speech for messages from players with levels lower than this value",
		section=muteOptionsSection
	)
	@Range(min=3, max=126)
	default int muteLevelThreshold() {
		return 3;
	}

	@ConfigItem(
		position=7,
		keyName=ConfigKeys.MUTE_CROWDS,
		name="Crowds larger than",
		description="When there are more players than the specified number around you, TTS will not trigger. 0 for no limit",
		section=muteOptionsSection
	)
	default int muteCrowds() {
		return 0;
	}

	// endregion

	// region Other Settings
	@ConfigSection(
		name="Other",
		description="Other settings",
		position=3
	)
	String otherOptionsSection = "otherOptionsSection";

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.BUILTIN_REPLACEMENTS,
		name="Use common abbreviations",
		description="Enable commonly used abbreviations",
		section=otherOptionsSection
	)
	default boolean useBuiltInReplacements() {return true;}

	@ConfigItem(
		position=2,
		keyName=ConfigKeys.ENABLE_DIALOG_TEXT_REPLACE,
		name="Use for dialogs",
		description="Enable abbreviations for in-game dialogs",
		section=otherOptionsSection
	)
	default boolean enableDialogTextReplacements() {return true;}

	@ConfigItem(
		position=3,
		keyName=ConfigKeys.CUSTOM_TEXT_REPLACEMENTS,
		name="Custom abbreviations",
		description="One per line. Example: wth=what the hell",
		section=otherOptionsSection
	)
	default String customTextReplacements() {
		return "\n";
	}

	@ConfigSection(
		closedByDefault=true,
		name="Developer Tools",
		description="Used for development, ignore.",
		position=999
	)
	String developerSection = "developerSection";

	@ConfigItem(
		section=developerSection,
		name="Simulate No TTS Engine",
		warning="Text To Speech start will intentionally fail.",
		keyName=ConfigKeys.DEVELOPER_SIMULATE_NO_TTS,
		description="")
	default boolean simulateNoEngine() {
		return false;
	}

	@ConfigItem(
		section=developerSection,
		name="Simulate Minimum Mode",
		warning="External Text To Speech start will intentionally fail.",
		keyName=ConfigKeys.DEVELOPER_MINIMUM_MODE,
		description="")
	default boolean simulateMinimumMode() {
		return false;
	}

	@ConfigItem(
			section = developerSection,
			name = "Reset Tutorial Hints",
			warning="Resets Menu Hints/Tutorial",
			keyName = ConfigKeys.DEVELOPER_RESET_HINTS,
			description = "Resets Menu Hints/Tutorial"
	)
	default boolean developerResetHints() {
		return false;
	}
	// endregion
}