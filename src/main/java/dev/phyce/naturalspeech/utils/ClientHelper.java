package dev.phyce.naturalspeech.utils;

import dev.phyce.naturalspeech.singleton.PluginSingleton;
import javax.inject.Inject;
import lombok.NonNull;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

// renamed: PlayerCommon
@PluginSingleton
public final class ClientHelper {
	private final Client client;

	@Inject
	public ClientHelper(Client client) {
		this.client = client;
	}

	public Player findPlayerWithUsername(@NonNull String username) {
		username = Text.standardize(username);
		for (Player player : client.getCachedPlayers()) {
			if (player != null && player.getName() != null && Text.standardize(player.getName()).equals(username)) {
				return player;
			}
		}
		return null;
	}

}
