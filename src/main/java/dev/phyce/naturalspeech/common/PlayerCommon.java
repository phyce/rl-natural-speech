package dev.phyce.naturalspeech.common;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

@Singleton
public class PlayerCommon
{
	@Inject
	private Client client;
	@Getter
	private static final Set<String> allowList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	@Getter
	private static final Set<String> blockList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private static PlayerCommon instance;

	@Inject
	public PlayerCommon() {
		if(instance != null) return;
		instance = this;
	}
	public static String getUsername() {
		return instance.client.getLocalPlayer().getName();
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
	public static boolean isBeingListened(String username) {
		if(allowList.isEmpty() && blockList.isEmpty())return true;
		if(!allowList.isEmpty() && allowList.contains(username))return true;
		return !blockList.isEmpty() && !blockList.contains(username);
	}
	public static void listen(String username) {
		blockList.clear();
		if(allowList.contains(username))return;
		allowList.add(username);
	}

	public static void unlisten(String username) {
		if(allowList.isEmpty())return;
		allowList.remove(username);
	}

	public static void mute(String username) {
		allowList.clear();
		if(blockList.contains(username))return;
		blockList.add(username);
	}

	public static void unmute(String username) {
		if(blockList.isEmpty())return;
		blockList.remove(username);
	}
}
