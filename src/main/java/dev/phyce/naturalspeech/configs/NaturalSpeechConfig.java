package dev.phyce.naturalspeech.configs;

import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CONFIG_GROUP)
public interface NaturalSpeechConfig extends Config {

	String CONFIG_GROUP = "Natural Speech";

	final class ConfigKeys {
		public static final String PERSONAL_VOICE = "personalVoice";
		public static final String GLOBAL_NPC_VOICE = "globalNpcVoice";
		public static final String SYSTEM_VOICE = "systemVoice";
		public static final String TWITCH_VOICE = "twitchVoice";
		public static final String AUTO_START = "autoStart";
		public static final String DISTANCE_FADE = "distanceFade";
		public static final String MASTER_VOLUME = "masterVolume";
		public static final String SPEECH_GENERATION = "Speech generation";
		public static final String PUBLIC_CHAT = "publicChat";
		public static final String PRIVATE_CHAT = "privateChat";
		public static final String PRIVATE_OUT_CHAT = "privateOutChat";
		public static final String FRIENDS_CHAT = "friendsChat";
		public static final String CLAN_CHAT = "clanChat";
		public static final String CLAN_GUEST_CHAT = "clanGuestChat";
		public static final String FRIENDS_ONLY_MODE = "friendsOnlyMode";
		public static final String EXAMINE_CHAT = "examineChat";
		public static final String NPC_OVERHEAD = "npcOverhead";
		public static final String DIALOG = "dialog";
		public static final String PLAYER_DIALOG = "playerDialog";
		public static final String REQUESTS = "requests";
		public static final String SYSTEM_MESSAGES = "systemMessages";
		public static final String TWITCH_CHAT = "twitchChat";
		public static final String MUTE_GRAND_EXCHANGE = "muteGrandExchange";
		public static final String MUTE_SELF = "muteSelf";
		public static final String MUTE_OTHERS = "muteOthers";
		public static final String MUTE_LEVEL_THRESHOLD = "muteLevelThreshold";
		public static final String MUTE_CROWDS = "muteCrowds";
		public static final String SHORTENED_PHRASES = "shortenedPhrases";
		public static final String COMMON_ABBREVIATIONS = "commonAbbreviations";
		public static final String DIALOG_TEXT_REPLACEMENTS = "dialogTextReplacements";
		public static final String HOLD_SHIFT_RIGHT_CLICK_MENU = "holdShiftRightClickMenu";
		public static final String MUTE_GRAND_EXCHANGE_NPC_SPAM = "muteGrandExchangeNpcSpam";
		public static final String FRIENDS_VOLUME_BOOST = "friendsVolumeBoost";
		public static final String CUT_OFF_DIALOG_ON_SKIP = "cutOffDialogOnSkip";
		public static final String MESSAGE_DUPLICATE_SUPPRESSOR = "messageDuplicateSuppressor";
	}

	//<editor-fold desc="> General Settings">
	@ConfigSection(
		name="General",
		description="General settings",
		position=0
	)
	String generalSettingsSection = "generalSettingsSection";

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.PERSONAL_VOICE,
		name="Personal voice ID",
		description="Choose one of the voices for your character, example: libritts:0",
		section=generalSettingsSection

	)
	default String personalVoiceID() {
		return "";
	}

	@ConfigItem(
		position=2,
		keyName=ConfigKeys.GLOBAL_NPC_VOICE,
		name="Global default NPC voice",
		description="Choose one of the voices for all NPCs, example: libritts:0",
		section=generalSettingsSection
	)
	default String globalNpcVoice() {
		return "";
	}

	@ConfigItem(
		position=3,
		keyName=ConfigKeys.SYSTEM_VOICE,
		name="System message voice",
		description="Choose one of the voices for system messages, example: libritts:0",
		section=generalSettingsSection

	)
	default String systemVoice() {
		return "libritts:1";
	}

	@ConfigItem(
		position=4,
		keyName=ConfigKeys.TWITCH_VOICE,
		name="Twitch chat voice",
		description="Choose one of the voices for Twitch chat, example: libritts:0",
		section=generalSettingsSection
	)
	default String twitchVoice() {
		return "";
	}

	@ConfigItem(
		position=5,
		keyName=ConfigKeys.AUTO_START,
		name="Autostart the TTS engine",
		description="If executable and voice models available, autostart the TTS engine when the plugin loads.",
		section=generalSettingsSection
	)
	default boolean autoStart() {return true;}

	@ConfigItem(
		position=6,
		keyName=ConfigKeys.DISTANCE_FADE,
		name="Fade distant sound",
		description="Players standing further away will sound quieter.",
		section=generalSettingsSection

	)
	default boolean distanceFadeEnabled() {
		return true;
	}

	@ConfigItem(
		position=7,
		keyName=ConfigKeys.MASTER_VOLUME,
		name="Master volume control",
		description="Volume percentage",
		section=generalSettingsSection

	)
	@Range(min=0, max=100)
	default int masterVolume() {
		return 100;
	}

	@ConfigItem(
		position=8,
		keyName=ConfigKeys.FRIENDS_VOLUME_BOOST,
		name="Friends volume boost",
		description="Volume boost percentage",
		section=generalSettingsSection
	)
	@Range(min=0, max=100)
	default int friendsVolumeBoost() {
		return 0;
	}

	@ConfigItem(
		position=9,
		keyName=ConfigKeys.HOLD_SHIFT_RIGHT_CLICK_MENU,
		name="Hold shift for right-click menu",
		description="Only show the right-click menu when holding shift.",
		section=generalSettingsSection
	)
	default boolean holdShiftRightClickMenu() {
		return false;
	}

	@ConfigItem(
		position=10,
		keyName=ConfigKeys.CUT_OFF_DIALOG_ON_SKIP,
		name="Interrupt dialog when skipping",
		description="Immediately play the next audio clip upon dialog skip",
		section=generalSettingsSection
	)
	default boolean cutOffDialogOnSkip() {
		return false;
	}

	@ConfigItem(
		position=11,
		keyName=ConfigKeys.MESSAGE_DUPLICATE_SUPPRESSOR,
		name="Silence duplicate messages",
		description="Prevents the same message from being constantly spammed",
		section=generalSettingsSection
	)
	default boolean messageDuplicateSuppressorEnabled() {
		return true;
	}

	//	@ConfigItem(
	//		position=5,
	//		keyName="playbackSpeed",
	//		name="Playback speed",
	//		description="The speed at which to play audio",
	//		section=generalSettingsSection
	//
	//	)
	//	@Range(min = 1, max = 500)
	//	default int playbackSpeed() {
	//		return 100;
	//	}

	//</editor-fold>

	//<editor-fold desc="> Speech Generation Settings">
	@ConfigSection(
		name=ConfigKeys.SPEECH_GENERATION,
		description="Settings to choose which messages should be played",
		position=1
	)
	String ttsOptionsSection = "ttsOptionsSection";

	@ConfigItem(
		keyName=ConfigKeys.PUBLIC_CHAT,
		name="Public messages",
		description="Enable text-to-speech to the public chat messages.",
		section=ttsOptionsSection,
		position=1
	)
	default boolean publicChatEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.PRIVATE_CHAT,
		name="Private received messages",
		description="Enable text-to-speech to the received private chat messages.",
		section=ttsOptionsSection,
		position=2
	)
	default boolean privateChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.PRIVATE_OUT_CHAT,
		name="Private sent out messages",
		description="Enable text-to-speech to the sent out private chat messages.",
		section=ttsOptionsSection
		,
		position=3
	)
	default boolean privateOutChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.FRIENDS_CHAT,
		name="Friends chat",
		description="Enable text-to-speech to friends chat messages.",
		section=ttsOptionsSection,
		position=4
	)
	default boolean friendsChatEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.CLAN_CHAT,
		name="Clan chat",
		description="Enable text-to-speech to the clan chat messages.",
		section=ttsOptionsSection,
		position=5
	)
	default boolean clanChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.CLAN_GUEST_CHAT,
		name="Guest clan chat",
		description="Enable text-to-speech to the guest clan chat messages.",
		section=ttsOptionsSection,
		position=6
	)
	default boolean clanGuestChatEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.EXAMINE_CHAT,
		name="Examine text",
		description="Enable text-to-speech to the 'Examine' messages.",
		section=ttsOptionsSection,
		position=7
	)
	default boolean examineChatEnabled() {
		return true;
	}

	//	@ConfigItem(
	//		keyName="playerOverhead",
	//		name="Player overhead dialog",
	//		description="Enable text-to-speech to overhead text that is not a message.",
	//		section=ttsOptionsSection,
	//		position=8
	//	)
	//	default boolean playerOverheadEnabled() {
	//		return true;
	//	}

	@ConfigItem(
		keyName=ConfigKeys.NPC_OVERHEAD,
		name="NPC overhead dialog",
		description="Enable text-to-speech to the overhead dialog for NPCs.",
		section=ttsOptionsSection,
		position=9
	)
	default boolean npcOverheadEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.DIALOG,
		name="NPC dialogs",
		description="Enable text-to-speech for NPC dialog text.",
		section=ttsOptionsSection,
		position=10
	)
	default boolean npcDialogEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.PLAYER_DIALOG,
		name="Player dialogs",
		description="Enable text-to-speech for your own dialog lines.",
		section=ttsOptionsSection,
		position=11
	)
	default boolean playerDialogEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.REQUESTS,
		name="Trade/Challenge requests",
		description="Enable text-to-speech to trade and challenge requests.",
		section=ttsOptionsSection,
		position=12
	)
	default boolean requestsEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.SYSTEM_MESSAGES,
		name="System messages",
		description="Generate text-to-speech to game's messages",
		section=ttsOptionsSection,
		position=13
	)
	default boolean systemMesagesEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.TWITCH_CHAT,
		name="Twitch chat plugin",
		description="Generate text-to-speech of messages received via the RuneLite Twitch plugin.",
		section=ttsOptionsSection,
		position=13
	)
	default boolean twitchChatEnabled() {
		return false;
	}
	//</editor-fold>

	//<editor-fold desc="> Mute Options">
	@ConfigSection(
		name="Mute",
		description="Change mute settings here",
		position=2
	)
	String muteOptionsSection = "muteOptionsSection";

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.MUTE_OTHERS,
		name="Others",
		description="Do not generate text-to-speech for messages from other players.",
		section=muteOptionsSection

	)
	default boolean muteOthers() {
		return false;
	}

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.FRIENDS_ONLY_MODE,
		name="Friends only mode",
		description="Only generate text-to-speech for friends.",
		section=muteOptionsSection
	)
	default boolean friendsOnlyMode() {
		return false;
	}

	@ConfigItem(
		position=2,
		keyName=ConfigKeys.MUTE_SELF,
		name="Yourself",
		description="Do not generate text-to-speech for messages that you send.",
		section=muteOptionsSection

	)
	default boolean muteSelf() {
		return false;
	}

	@ConfigItem(
		position=3,
		keyName=ConfigKeys.MUTE_GRAND_EXCHANGE,
		name="Grand Exchange",
		description="Disable text-to-speech in the grand exchange area.",
		section=muteOptionsSection
	)
	default boolean muteGrandExchange() {
		return true;
	}



	@ConfigItem(
		position=6,
		keyName=ConfigKeys.MUTE_LEVEL_THRESHOLD,
		name="Below level",
		description="Do not generate text-to-speech for messages from players with levels lower than this value.",
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
		description="When there are more players than the specified number around you, TTS will not trigger. 0 for no limit.",
		section=muteOptionsSection
	)
	default int muteCrowds() {
		return 0;
	}

	//</editor-fold>

	//<editor-fold desc="> Other Settings">
	@ConfigSection(
		name="Other",
		description="Other settings",
		position=3
	)
	String otherOptionsSection = "otherOptionsSection";

	@ConfigItem(
		position=1,
		keyName=ConfigKeys.COMMON_ABBREVIATIONS,
		name="Use common abbreviations",
		description="Enable commonly used abbreviations",
		section=otherOptionsSection
	)
	default boolean useCommonAbbreviations() {
		return true;
	}

	@ConfigItem(
		position=2,
		keyName=ConfigKeys.DIALOG_TEXT_REPLACEMENTS,
		name="Use for dialogs",
		description="Enable abbreviations for in-game dialogs",
		section=otherOptionsSection
	)
	default boolean dialogTextReplacementsEnabled() {
		return true;
	}

	@ConfigItem(
		position=3,
		keyName=ConfigKeys.SHORTENED_PHRASES,
		name="Custom abbreviations",
		description="One per line. Example: wth=what the hell",
		section=otherOptionsSection
	)
	default String shortenedPhrases() {
		return "wth=what the hell\n" +
			"iknami=my friend\n" +
			"kuani=excellent\n" +
			"nilsal=hello\n" +
			"tetamo=oh no\n" +
			"timoiva=goodbye\n";
	}
	//</editor-fold>
}
