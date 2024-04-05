package dev.phyce.naturalspeech.tts;

public final class AudioLineNames {
	public static final String SYSTEM = "&system";
	public static final String GLOBAL_NPC = "&globalnpc";
	public static final String LOCAL_USER = "&localuser";
	public static final String DIALOG = "&dialog";
	public static final String VOICE_EXPLORER = "&VoiceExplorer";

	public static String Username(String username) {
		return "&user_" + username;
	}

	public static String NPCName(String npcName) {
		return "&npc_" + npcName;
	}

}
