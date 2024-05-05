package dev.phyce.naturalspeech.utils;

import dev.phyce.naturalspeech.singleton.PluginSingleton;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import lombok.NonNull;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.Player;

// renamed: PlayerCommon
@PluginSingleton
public final class ClientHelper {
	private final Client client;

	@Inject
	public ClientHelper(Client client) {
		this.client = client;
	}

	@CheckForNull
	public Player findPlayer(Standardize.SID sid) {
		for (Player player : client.getCachedPlayers()) {
			if (Standardize.equals(player, sid)) {
				return player;
			}
		}
		return null;
	}

	public boolean isLocalPlayer(Standardize.SID sid) {
		return Standardize.equals(sid, client.getLocalPlayer());
	}

	public boolean isFriend(Standardize.SID sid) {
		if (Standardize.equals(sid, client.getLocalPlayer())) {
			return true;
		}

		for (Friend friend : getFriends()) {
			if (Standardize.equals(friend, sid)) {
				return true;
			}
		}
		return false;
	}

	public Player findPlayerWithUsername(@NonNull String username) {
		for (Player player : client.getCachedPlayers()) {
			if (Standardize.equals(player, username)) {
				return player;
			}
		}
		return null;
	}

	public boolean isFriend(String username) {
		if (Standardize.equals(username, client.getLocalPlayer())) {
			return true;
		}

		for (Friend friend : getFriends()) {
			if (Standardize.equals(friend.getName(), username)) {
				return true;
			}
		}
		return false;
	}

	public int getLevel(@NonNull String username) {
		Player targetPlayer = findPlayerWithUsername(username);

		if (targetPlayer == null) return -1;

		return targetPlayer.getCombatLevel();
	}

	public Friend[] getFriends() {
		return client.getFriendContainer().getMembers();
	}

}
