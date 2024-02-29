package dev.phyce.naturalspeech.common;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.util.Text;

public class Player
{
	@Inject
	private static Client client;
	public static net.runelite.api.Player getFromUsername(String username) {
		String sanitized = Text.sanitize(username);
		for (net.runelite.api.Player player : client.getCachedPlayers()) {
			if (player != null && player.getName() != null && Text.sanitize(player.getName()).equals(sanitized)) return player;
		}
		return null;
	}
}
