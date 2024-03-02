package dev.phyce.naturalspeech.common;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

@Singleton
public class PlayerCommon
{
	@Inject
	private Client client;

	private static PlayerCommon instance;

	@Inject
	public PlayerCommon() {
		if(instance != null) return;
		instance = this;
	}
	public static Player getFromUsername(String username) {
		String sanitized = Text.sanitize(username);
		for (net.runelite.api.Player player : instance.client.getCachedPlayers()) {
			if (player != null && player.getName() != null && Text.sanitize(player.getName()).equals(sanitized)) return player;
		}
		return null;
	}

	public static int getLevel(String username) {
		Player targetPlayer = getFromUsername(username);

		if (targetPlayer == null) return 0;

		return targetPlayer.getCombatLevel();
	}

	public static int getDistance(String username) {
		Player localPlayer = instance.client.getLocalPlayer();
		Player targetPlayer = getFromUsername(username);

		if (localPlayer == null || targetPlayer == null) return 0;

		return localPlayer
			.getWorldLocation()
			.distanceTo(targetPlayer.getWorldLocation());
	}
}
