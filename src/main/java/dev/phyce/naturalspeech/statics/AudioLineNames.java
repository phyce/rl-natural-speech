package dev.phyce.naturalspeech.statics;

public interface AudioLineNames {
	String SYSTEM = "&system";
	String GLOBAL_NPC = "&globalnpc";
	String LOCAL_USER = "&localuser";
	String DIALOG = "&dialog";
	String VOICE_EXPLORER = "&VoiceExplorer";

	static String Username(String username) {
		return "&user_" + username;
	}
	static String NPCName(String npcName) {
		return "&npc_" + npcName;
	}

}
