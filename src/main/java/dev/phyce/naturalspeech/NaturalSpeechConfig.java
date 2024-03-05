package dev.phyce.naturalspeech;

import net.runelite.client.config.*;

import static dev.phyce.naturalspeech.NaturalSpeechPlugin.NATURALSPEECH_CONFIG_GROUP;

@ConfigGroup(NATURALSPEECH_CONFIG_GROUP)
public interface NaturalSpeechConfig extends Config {
	@ConfigSection(
			name = "General",
			description = "General settings",
			position = 0
	)
	String generalOptionsSection = "generalOptionsSection";
	@ConfigSection(
			name = "Speech generation",
			description = "Settings to choose which messages should be played",
			position = 1
	)
	String ttsOptionsSection = "ttsOptionsSection";
	@ConfigSection(
			name = "Mute",
			description = "Change mute settings here",
			position = 2
	)
	String muteOptionsSection = "muteOptionsSection";
	@ConfigSection(
			name = "Other",
			description = "Other settings",
			position = 3
	)
	String otherOptionsSection = "otherOptionsSection";

	@ConfigItem(
			position = 1,
			keyName = "autoStart",
			name = "Autostart the TTS engine",
			description = "If executable and voice models available, autostart the TTS engine when the plugin loads.",
			section = generalOptionsSection
	)
	default boolean autoStart() {
		return true;
	}

	@ConfigItem(
			position = 2,
			keyName = "usePersonalVoice",
			name = "Select personal voice",
			description = "Lets you choose the voice of your character.",
			section = generalOptionsSection
	)
	default boolean usePersonalVoice() {
		return false;
	}

	@ConfigItem(
			position = 3,
			keyName = "personalVoice",
			name = "Personal voice ID",
			description = "Choose one of the 903 voices for your character, example: libritts:0",
			section = generalOptionsSection

	)
	default String personalVoiceID() {
		return "libritts:0";
	}

	@ConfigItem(
			position = 4,
			keyName = "distanceFade",
			name = "Fade distant sound",
			description = "Players standing further away will sound quieter.",
			section = generalOptionsSection

	)
	default boolean distanceFadeEnabled() {
		return true;
	}

	@ConfigItem(
			keyName = "publicChat",
			name = "Public messages",
			description = "Enable text-to-speech to the public chat messages.",
			section = ttsOptionsSection,
			position = 1
	)
	default boolean publicChatEnabled() {
		return true;
	}

	@ConfigItem(
			keyName = "privateChat",
			name = "Private received messages",
			description = "Enable text-to-speech to the received private chat messages.",
			section = ttsOptionsSection,
			position = 2
	)
	default boolean privateChatEnabled() {
		return false;
	}

	@ConfigItem(
			keyName = "privateOutChat",
			name = "Private sent out messages",
			description = "Enable text-to-speech to the sent out private chat messages.",
			section = ttsOptionsSection
			,
			position = 3
	)
	default boolean privateOutChatEnabled() {
		return false;
	}

	@ConfigItem(
			keyName = "friendsChat",
			name = "Friends chat",
			description = "Enable text-to-speech to friends chat messages.",
			section = ttsOptionsSection,
			position = 4
	)
	default boolean friendsChatEnabled() {
		return true;
	}

	@ConfigItem(
			keyName = "clanChat",
			name = "Clan chat",
			description = "Enable text-to-speech to the clan chat messages.",
			section = ttsOptionsSection,
			position = 5
	)
	default boolean clanChatEnabled() {
		return false;
	}

	@ConfigItem(
			keyName = "clanGuestChat",
			name = "Guest clan chat",
			description = "Enable text-to-speech to the guest clan chat messages.",
			section = ttsOptionsSection,
			position = 6
	)
	default boolean clanGuestChatEnabled() {
		return false;
	}

	@ConfigItem(
			keyName = "examineChat",
			name = "Examine text",
			description = "Enable text-to-speech to the 'Examine' messages.",
			section = ttsOptionsSection,
			position = 7
	)
	default boolean examineChatEnabled() {
		return true;
	}

	@ConfigItem(
			keyName = "dialog",
			name = "Dialogs",
			description = "Enable text-to-speech to dialog text.",
			section = ttsOptionsSection,
			position = 8
	)
	default boolean dialogEnabled() {
		return true;
	}

	@ConfigItem(
			position = 1,
			keyName = "muteGrandExchange",
			name = "Mute in Grand Exchange",
			description = "Disable text-to-speech in the grand exchange area.",
			section = muteOptionsSection
	)
	default boolean muteGrandExchange() {
		return true;
	}

	@ConfigItem(
			position = 4,
			keyName = "muteSelf",
			name = "Mute yourself",
			description = "Do not generate text-to-speech for messages that you send.",
			section = muteOptionsSection

	)
	default boolean muteSelf() {
		return false;
	}

	@ConfigItem(
			position = 5,
			keyName = "muteOthers",
			name = "Mute others",
			description = "Do not generate text-to-speech for messages from other players.",
			section = muteOptionsSection

	)
	default boolean muteOthers() {
		return false;
	}

	@ConfigItem(
			position = 6,
			keyName = "muteLevelThreshold",
			name = "Mute below level",
			description = "Do not generate text-to-speech for messages from players with levels lower than this value.",
			section = muteOptionsSection

	)
	@Range(min = 3, max = 126)
	default int muteLevelThreshold() {
		return 3;
	}

	@ConfigItem(
			position = 4,
			keyName = "shortenedPhrases",
			name = "Shortened phrases",
			description = "Replace commonly used shortened sentences with whole words",
			section = otherOptionsSection
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
				"ge=grand exchange\n";
	}
}