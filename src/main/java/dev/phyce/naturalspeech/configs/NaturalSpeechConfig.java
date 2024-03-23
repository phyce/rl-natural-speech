package dev.phyce.naturalspeech.configs;

import static dev.phyce.naturalspeech.configs.NaturalSpeechConfig.CONFIG_GROUP;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CONFIG_GROUP)
public interface NaturalSpeechConfig extends Config {

	String CONFIG_GROUP = "NaturalSpeech";

	final class ConfigKeys {
		public static final String PERSONAL_VOICE = "personalVoice";
		public static final String GLOBAL_NPC_VOICE = "globalNpcVoice";
		public static final String SYSTEM_VOICE = "systemVoice";
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
		public static final String EXAMINE_CHAT = "examineChat";
		public static final String NPC_OVERHEAD = "npcOverhead";
		public static final String DIALOG = "dialog";
		public static final String REQUESTS = "requests";
		public static final String SYSTEM_MESSAGES = "systemMessages";
		public static final String MUTE_GRAND_EXCHANGE = "muteGrandExchange";
		public static final String MUTE_SELF = "muteSelf";
		public static final String MUTE_OTHERS = "muteOthers";
		public static final String MUTE_LEVEL_THRESHOLD = "muteLevelThreshold";
		public static final String MUTE_CROWDS = "muteCrowds";
		public static final String SHORTENED_PHRASES = "shortenedPhrases";
		public static final String HOLD_SHIFT_RIGHT_CLICK_MENU = "holdShiftRightClickMenu";
		public static final String MUTE_GRAND_EXCHANGE_GUIDE_NPCS = "muteGrandExchangeGuideNpcs";
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
		keyName=ConfigKeys.AUTO_START,
		name="Autostart the TTS engine",
		description="If executable and voice models available, autostart the TTS engine when the plugin loads.",
		section=generalSettingsSection
	)
	default boolean autoStart() {return true;}

	@ConfigItem(
		position=5,
		keyName=ConfigKeys.DISTANCE_FADE,
		name="Fade distant sound",
		description="Players standing further away will sound quieter.",
		section=generalSettingsSection

	)
	default boolean distanceFadeEnabled() {
		return true;
	}

	@ConfigItem(
		position=6,
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
		position=7,
		keyName=ConfigKeys.HOLD_SHIFT_RIGHT_CLICK_MENU,
		name="Hold shift for right-click menu",
		description="Only show the right-click menu when holding shift.",
		section=generalSettingsSection
	)
	default boolean holdShiftRightClickMenu() {
		return false;
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
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.DIALOG,
		name="Dialogs",
		description="Enable text-to-speech to dialog text.",
		section=ttsOptionsSection,
		position=10
	)
	default boolean dialogEnabled() {
		return true;
	}

	@ConfigItem(
		keyName=ConfigKeys.REQUESTS,
		name="Trade/Challenge requests",
		description="Enable text-to-speech to trade and challenge requests.",
		section=ttsOptionsSection,
		position=11
	)
	default boolean requestsEnabled() {
		return false;
	}

	@ConfigItem(
		keyName=ConfigKeys.SYSTEM_MESSAGES,
		name="System messages",
		description="Generate text-to-speech to game's messages",
		section=ttsOptionsSection,
		position=12
	)
	default boolean systemMesagesEnabled() {
		return true;
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

	//	@ConfigItem(
	//		position = 2,
	//		keyName=ConfigKeys.MUTE_GRAND_EXCHANGE_GUIDE_NPCS,
	//	)


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
		position=4,
		keyName=ConfigKeys.SHORTENED_PHRASES,
		name="Shortened phrases",
		description="Replace commonly used shortened sentences with whole words",
		section=otherOptionsSection
	)
	default String shortenedPhrases() {
		return "ags=armadyl godsword\n" +
			"ags2=ancient godsword\n" +
			"bgs=bandos godsword\n" +
			"idk=i don't know\n" +
			"imo=in my opinion\n" +
			"afaik=as far as i know\n" +
			"rly=really\n" +
			"tbow=twisted bow\n" +
			"tbows=twisted bows\n" +
			"p2p=pay to play\n" +
			"f2p=free to play\n" +
			"ty=thank you\n" +
			"tysm=thank you so much\n" +
			"tyvm=thank you very much\n" +
			"tyty=thank you thank you\n" +
			"im=i'm\n" +
			"np=no problem\n" +
			"acc=account\n" +
			"irl=in real life\n" +
			"wtf=what the fuck\n" +
			"jk=just kidding\n" +
			"gl=good luck\n" +
			"pls=please\n" +
			"plz=please\n" +
			"osrs=oldschool runescape\n" +
			"rs3=runescape 3\n" +
			"lvl=level\n" +
			"ffs=for fuck's sake\n" +
			"af=as fuck\n" +
			"smh=shake my head\n" +
			"wby=what about you\n" +
			"brb=be right back\n" +
			"ik=i know\n" +
			"<3=heart\n" +
			"fcape=fire cape\n" +
			"xp=experience\n" +
			"nty=no thank you\n" +
			"dhide=dragonhide\n" +
			"pvp=player versus player\n" +
			"wyd=what you doing\n" +
			"bc=because\n" +
			"afk=away from keyboard\n" +
			"tts=text to speech\n" +
			"ea=each\n" +
			"bbq=barbeque\n" +
			"thx=thanks\n" +
			"lmk=let me know\n" +
			"gg=good game\n" +
			"wp=well played\n" +
			"ggwp=good game well played\n" +
			"rn=right now\n" +
			"fr=for real\n" +
			"nmz=nightmare zone\n" +
			"ge=grand exchange\n" +
			"ppl=people\n" +
			"gtfo=get the fuck out\n" +
			"wb=welcome back\n" +
			"ikr=i know right\n" +
			"og=original gangster\n" +
			"cc=clan chat\n" +
			"pk=player killing\n" +
			"pker=player killer\n" +
			"pking=player killing\n" +
			"poh=player owned home\n" +
			"gz=congratulations\n" +
			"tbh=to be honest\n";
	}
	//</editor-fold>
}