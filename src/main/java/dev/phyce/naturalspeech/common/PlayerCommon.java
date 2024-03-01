package dev.phyce.naturalspeech.common;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

public class PlayerCommon
{
	@Inject
	private static Client client;
	public static Player getFromUsername(String username) {
		String sanitized = Text.sanitize(username);
		for (net.runelite.api.Player player : client.getCachedPlayers()) {
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
		Player localPlayer = client.getLocalPlayer();
		Player targetPlayer = getFromUsername(username);

		if (localPlayer == null || targetPlayer == null) return 0;

		return localPlayer
			.getWorldLocation()
			.distanceTo(targetPlayer.getWorldLocation());
	}
}
