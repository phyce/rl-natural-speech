package dev.phyce.naturalspeech.statics;

public interface Names {
	String SYSTEM = "&system";
	String GLOBAL_NPC = "&globalnpc";
	String USER = "&localuser";
	String DIALOG = "&dialog";
	String VOICE_EXPLORER = "&voiceexplorer";

	static String Username(String username) {
		return "&user_" + username;
	}

	static String NPCName(String npcName) {
		return "&npc_" + npcName;
	}

}
