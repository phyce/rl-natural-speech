package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("naturalSpeech")
public interface NaturalSpeechConfig extends Config
{
	String CONFIG_GROUP = "naturalSpeech";
	@ConfigItem(
		keyName = "muteGrandExchange",
		name = "Mute in Grand Exchange",
		description = "Disable text-to-speech in the grand exchange area."
	)
	default boolean muteGrandExchange() {
		return true;
	}

	@ConfigItem(
			keyName = "publicChat",
			name = "Public messages",
			description = "Enable text-to-speech to the public chat messages."
	)
	default boolean publicChat() {
		return true;
	}

	@ConfigItem(
			keyName = "privateChat",
			name = "Private received messages",
			description = "Enable text-to-speech to the received private chat messages."
	)
	default boolean privateChat() {
		return false;
	}

	@ConfigItem(
			keyName = "privateOutChat",
			name = "Private sent out messages",
			description = "Enable text-to-speech to the sent out private chat messages."
	)
	default boolean privateOutChat() {
		return false;
	}

	@ConfigItem(
			keyName = "friendsChat",
			name = "Friends chat",
			description = "Enable text-to-speech to friends chat messages."
	)
	default boolean friendsChat() {
		return true;
	}

	@ConfigItem(
			keyName = "examineChat",
			name = "Examine",
			description = "Enable text-to-speech to the 'Examine' messages."
	)
	default boolean examineChat() {
		return true;
	}

	@ConfigItem(
			keyName = "clanChat",
			name = "Clan chat",
			description = "Enable text-to-speech to the clan chat messages."
	)
	default boolean clanChat() {
		return false;
	}

	@ConfigItem(
			keyName = "clanGuestChat",
			name = "Guest clan chat",
			description = "Enable text-to-speech to the guest clan chat messages."
	)
	default boolean clanGuestChat() {
		return false;
	}

	@ConfigItem(
			keyName = "dialog",
			name = "Dialogs",
			description = "Enable text-to-speech to dialog text."
	)
	default boolean dialog() {
		return true;
	}

	@ConfigItem(
			keyName = "distanceFade",
			name = "Fade distant sound",
			description = "Players standing further away will sound quieter."
	)
	default boolean distanceFade() {return true;}

	@ConfigItem(
			position = 7,
			keyName = "shortenedPhrases",
			name = "Shortened phrases",
			description = "Replace commonly used shortened sentences with whole words"
	)
	default String getNpcToHighlight()
	{
		return "";
	}
}
