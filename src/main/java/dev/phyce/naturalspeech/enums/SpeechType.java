package dev.phyce.naturalspeech.enums;

import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import java.util.function.Predicate;

public enum SpeechType {
	PUBLIC_CHAT("Public messages", NaturalSpeechConfig::publicChatEnabled),
	PRIVATE_CHAT("Private received messages", NaturalSpeechConfig::privateChatEnabled),
	PRIVATE_OUT_CHAT("Private sent out messages", NaturalSpeechConfig::privateOutChatEnabled),
	FRIENDS_CHAT("Friends chat", NaturalSpeechConfig::friendsChatEnabled),
	CLAN_CHAT("Clan chat", NaturalSpeechConfig::clanChatEnabled),
	CLAN_GUEST_CHAT("Guest clan chat", NaturalSpeechConfig::clanGuestChatEnabled),
	GROUP_IRONMAN_CHAT("Group Ironman chat", NaturalSpeechConfig::groupIronmanChatEnabled),
	EXAMINE("Examine text", NaturalSpeechConfig::examineChatEnabled),
	NPC_OVERHEAD("NPC overhead dialog", NaturalSpeechConfig::npcOverheadEnabled),
	NPC_DIALOG("NPC dialogs", NaturalSpeechConfig::npcDialogEnabled),
	PLAYER_DIALOG("Player dialogs", NaturalSpeechConfig::playerDialogEnabled),
	REQUESTS("Trade/Challenge requests", NaturalSpeechConfig::requestsEnabled),
	SYSTEM_MESSAGES("System messages", NaturalSpeechConfig::systemMesagesEnabled),
	LOGIN_LOGOUT("Login/logout notifications", NaturalSpeechConfig::loginLogoutEnabled),
	TWITCH_CHAT("Twitch chat plugin", NaturalSpeechConfig::twitchChatEnabled);

	private final String label;
	private final Predicate<NaturalSpeechConfig> enabled;

	SpeechType(String label, Predicate<NaturalSpeechConfig> enabled) {
		this.label = label;
		this.enabled = enabled;
	}

	public boolean isEnabled(NaturalSpeechConfig config) {
		return enabled.test(config);
	}

	@Override
	public String toString() {
		return label;
	}
}
