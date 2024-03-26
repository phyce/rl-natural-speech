package dev.phyce.naturalspeech.helpers;

import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.configs.NaturalSpeechConfig;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.NonNull;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

// renamed: PlayerCommon
@Singleton
public final class PluginHelper {
	private static PluginHelper instance;
	@Inject
	private NaturalSpeechConfig config;
	@Inject
	private Client client;

	public PluginHelper() {
		// single guarantees one instance, no checks needed
		instance = this;
	}

	public static NaturalSpeechConfig getConfig() {
		return instance.config;
	}

	@CheckForNull
	public static String getLocalPlayerUsername() {
		if (instance.client.getLocalPlayer() == null || instance.client.getLocalPlayer().getName() == null) {
			return null;
		}

		String username = instance.client.getLocalPlayer().getName();
		username = Text.standardize(username);
		return username;
	}

	@CheckForNull
	public static Gender getLocalPlayerGender() {
		return Gender.parseInt(instance.client.getLocalPlayer().getPlayerComposition().getGender());
	}

	public static Player findPlayerWithUsername(@NonNull String username) {
		username = Text.standardize(username);
		for (Player player : instance.client.getCachedPlayers()) {
			if (player != null && player.getName() != null && Text.standardize(player.getName()).equals(username)) {
				return player;
			}
		}
		return null;
	}

	public static int getLevel(@NonNull String username) {
		Player targetPlayer = findPlayerWithUsername(username);

		if (targetPlayer == null) return 0;

		return targetPlayer.getCombatLevel();
	}

	public static int getDistance(@NonNull String username) {
		// For local player distance is 0
		if (Objects.equals(getLocalPlayerUsername(), username)) return 0;

		Player localPlayer = instance.client.getLocalPlayer();
		Player targetPlayer = findPlayerWithUsername(username);

		if (localPlayer == null || targetPlayer == null) return 0;

		return localPlayer
			.getWorldLocation()
			.distanceTo(targetPlayer.getWorldLocation());
	}

	public static int getNPCDistance(@NonNull NPC npc) {
		Player localPlayer = instance.client.getLocalPlayer();

		if (localPlayer == null) return 0;

		int distance = localPlayer
			.getWorldLocation()
			.distanceTo(npc.getWorldLocation());

		// FIXME(Louis) Over 15 would play at max volume
		if (distance < 0 || 15 < distance) return 0;

		return distance;
	}

	public static int getActorDistance(@NonNull Actor actor) {
		Player localPlayer = instance.client.getLocalPlayer();

		if (localPlayer == null) return 0;

		int distance = localPlayer
			.getWorldLocation()
			.distanceTo(actor.getWorldLocation());

		// FIXME(Louis) Over 15 would play at max volume
		if (distance < 0 || 15 < distance) return 0;

		return distance;
	}

	public static Friend[] getFriends() {
		return instance.client.getFriendContainer().getMembers();
	}

	public static boolean isFriend(String username) {
		username = Text.sanitize(username);
//		System.out.println(username + "\n\n\n");
		if (username.equals(getLocalPlayerUsername()))return true;
		for(Friend friend: getFriends()){
			System.out.println(friend.getName());
			String friendName = Text.sanitize(friend.getName());
			if(friendName.equals(username))return true;
		}
		return false;
	}

	public static boolean isPlayerChatMessage(@NonNull ChatMessage message) {
		return !isNPCChatMessage(message);
	}

	public static boolean isNPCChatMessage(@NonNull ChatMessage message) {
		// From NPC
		switch (message.getType()) {
			case DIALOG:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
			case WELCOME:
			case GAMEMESSAGE:
			case CONSOLE:
				return true;
		}
		return false;
	}

}
