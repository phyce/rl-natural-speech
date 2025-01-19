package dev.phyce.naturalspeech.statics;

public interface ConfigKeys {

	// region Config Panel
	String PERSONAL_VOICE = "personalVoice";
	String GLOBAL_NPC_VOICE = "globalNpcVoice";
	String SYSTEM_VOICE = "systemVoice";
	String AUTO_START = "autoStart";
	String DISTANCE_FADE = "distanceFade";
	String MASTER_VOLUME = "masterVolume";
	String MASTER_MUTE = "masterMute";
	String SPEECH_GENERATION = "Speech generation";
	String PUBLIC_CHAT = "publicChat";
	String PRIVATE_CHAT = "privateChat";
	String PRIVATE_OUT_CHAT = "privateOutChat";
	//	String FRIENDS_CHAT = "friendsChat";
	String CLAN_CHAT = "clanChat";
	String CLAN_GUEST_CHAT = "clanGuestChat";
	String GIM_CHAT = "groupIronmanChat";
	String EXAMINE_CHAT = "examineChat";
	String NPC_OVERHEAD = "npcOverhead";
	String DIALOG = "dialog";
	String REQUESTS = "requests";
	String SYSTEM_MESSAGES = "systemMessages";
	String MUTE_GRAND_EXCHANGE = "muteGrandExchange";
	String MUTE_SELF = "muteSelf";
	String MUTE_OTHER_PLAYERS = "muteOthers";
	String MUTE_LEVEL_THRESHOLD = "muteLevelThreshold";
	String MUTE_CROWDS = "muteCrowds";
	String HOLD_SHIFT_RIGHT_CLICK_MENU = "holdShiftRightClickMenu";
	String FRIENDS_ONLY_MODE = "friendsOnlyMode";
	String FRIENDS_VOLUME_BOOST = "friendsVolumeBoost";
	String BUILTIN_REPLACEMENTS = "commonAbbreviations";
	String CUSTOM_TEXT_REPLACEMENTS = "customAbbreviations";
	String ENABLE_DIALOG_TEXT_REPLACE = "customAbbreviationsNpc";
	String OVERRIDE_CUSTOM_NPC_VOICES = "overrideCustomNpcVoices";
	// endregion

	// region Development
	String DEVELOPER_SIMULATE_NO_TTS = "simulateNoTTS";
	String DEVELOPER_MINIMUM_MODE = "simulateMinimumMode";
	// endregion

	// named ttsEngine because it was the only engine supported at the time
	String DEPRECATED_PIPER_PATH = "ttsEngine";

	// Voice
	String VOICE_CONFIG_KEY = "speaker_config.json";
	String VOICE_BLACKLIST_KEY = "voiceBlacklist";

	// Hint Memory
	String DEVELOPER_RESET_HINTS = "developerResetHints";

	interface Hints {
		String HINTED_INGAME_VOLUME_MENU_TO_MUTE = "hintedInGameMenuVolumeMute";
		String HINTED_INGAME_ENTITY_MENU_TO_MUTE = "hintedInGameEntityVoiceMute";
		String HINTED_INGAME_ENTITY_MENU_SET_VOICE = "hintedInGameEntityVoiceSet";
		String HINTED_DIALOG_BUTTON = "hintedDialogButton";

		String[] ALL = {
				HINTED_INGAME_VOLUME_MENU_TO_MUTE,
				HINTED_INGAME_ENTITY_MENU_TO_MUTE,
				HINTED_INGAME_ENTITY_MENU_SET_VOICE,
				HINTED_DIALOG_BUTTON
		};
	}

	// region Mute and listen
	String LISTEN_MODE = "listenMode";
	String LISTEN_LIST = "listenList";
	String MUTE_LIST = "muteList";

	@Deprecated(since="1.3 Migrated to LISTEN_LIST using EntityID")
	String DEPRECATED_USERNAME_LISTEN_LIST = "usernameListenList";
	@Deprecated(since="1.3 Migrated to LISTEN_LIST using EntityID")
	String DEPRECATED_USERNAME_MUTE_LIST = "usernameMuteList";
	@Deprecated(since="1.3 Migrated to LISTEN_LIST using EntityID")
	String DEPRECATED_NPC_ID_LISTEN_LIST = "npcIdListenList";
	@Deprecated(since="1.3 Migrated to LISTEN_LIST using EntityID")
	String DEPRECATED_NPC_ID_MUTE_LIST = "npcIdMuteList";

	// endregion
}
