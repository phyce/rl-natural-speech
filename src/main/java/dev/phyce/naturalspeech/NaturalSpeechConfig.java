package dev.phyce.naturalspeech;

import net.runelite.client.config.*;

@ConfigGroup("naturalSpeech")
public interface NaturalSpeechConfig extends Config
{
	String CONFIG_GROUP = "naturalSpeech";
	int MAX_VOICES = 903;
	@ConfigSection(
			name = "General",
			description = "General settings",
			position = 0
	)
	String generalOptionsSection = "generalOptionsSection";

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
			position = 1,
			keyName = "muteGrandExchange",
			name = "Mute in Grand Exchange",
			description = "Disable text-to-speech in the grand exchange area.",
			section = generalOptionsSection
	)
	default boolean muteGrandExchange() {
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
			description = "Choose one of the 903 voices for your character",
			section = generalOptionsSection

	)
	@Range(min = 0, max = MAX_VOICES)
	default int personalVoice() { return 0; }
	@ConfigItem(
			position = 4,
			keyName = "distanceFade",
			name = "Fade distant sound",
			description = "Players standing further away will sound quieter.",
			section = generalOptionsSection

	)
	default boolean distanceFade() { return true; }

	@ConfigSection(
			name = "Speech generation",
			description = "Settings to choose which messages should be played",
			position = 1
	)
	String ttsOptionsSection = "ttsOptionsSection";
	@ConfigItem(
			keyName = "publicChat",
			name = "Public messages",
			description = "Enable text-to-speech to the public chat messages.",
			section = ttsOptionsSection,
			position = 1
	)
	default boolean publicChat() {
		return true;
	}
	@ConfigItem(
			keyName = "privateChat",
			name = "Private received messages",
			description = "Enable text-to-speech to the received private chat messages.",
			section = ttsOptionsSection,
			position = 2
	)
	default boolean privateChat() {
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
	default boolean privateOutChat() {
		return false;
	}
	@ConfigItem(
			keyName = "friendsChat",
			name = "Friends chat",
			description = "Enable text-to-speech to friends chat messages.",
			section = ttsOptionsSection,
			position = 4
	)
	default boolean friendsChat() {
		return true;
	}
	@ConfigItem(
			keyName = "clanChat",
			name = "Clan chat",
			description = "Enable text-to-speech to the clan chat messages.",
			section = ttsOptionsSection,
			position = 5
	)
	default boolean clanChat() {
		return false;
	}
	@ConfigItem(
			keyName = "clanGuestChat",
			name = "Guest clan chat",
			description = "Enable text-to-speech to the guest clan chat messages.",
			section = ttsOptionsSection,
			position = 6
	)
	default boolean clanGuestChat() {
		return false;
	}
	@ConfigItem(
			keyName = "examineChat",
			name = "Examine text",
			description = "Enable text-to-speech to the 'Examine' messages.",
			section = ttsOptionsSection,
			position = 7
	)
	default boolean examineChat() {
		return true;
	}

	@ConfigItem(
			keyName = "dialog",
			name = "Dialogs",
			description = "Enable text-to-speech to dialog text.",
			section = ttsOptionsSection,
			position = 8
	)
	default boolean dialog() {
		return true;
	}

	@ConfigSection(
			name = "Other",
			description = "Other settings",
			position = 2
	)
	String otherOptionsSection = "otherOptionsSection";

	@ConfigItem(
			position = 1,
			keyName = "ttsEngine",
			name = "TTS Engine",
			description = "Full path to the binary of the TTS engine. Currently only Piper is supported.",
			section = otherOptionsSection,
			warning = "You will need to reload the plugin to apply the changes."
	)
	default String ttsEngine()  {
		// make sure to use the write path separators depending on operating system
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			return "C:\\piper\\piper.exe";
		} else {
			// assume in user folder
			return System.getProperty("user.home") + "/piper/piper";
		}
	}
	@ConfigItem(
			position = 4,
			keyName = "shortenedPhrases",
			name = "Shortened phrases",
			description = "Replace commonly used shortened sentences with whole words",
			section = otherOptionsSection,
			warning = "You will need to reload the plugin to apply the changes."

	)
	default String shortenedPhrases()  {
		return  "ags=armadyl godsword\n" +
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
				"ea=each\n";
	}
}
