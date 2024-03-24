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

	@Getter
	private static final Set<String> allowList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	@Getter
	private static final Set<String> blockList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

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
		if (username.equals(getLocalPlayerUsername()))return true;
		for(Friend friend: getFriends()){
			if(friend.getName().equals(username))return true;
		}
		return false;
	}

//	public static boolean isBeingListened(@NonNull String username) {
//		if (allowList.isEmpty() && blockList.isEmpty()) return true;
//		if (!allowList.isEmpty() && allowList.contains(username)) return true;
//		return !blockList.isEmpty() && !blockList.contains(username);
//	}
//
//	public static void listen(@NonNull String username) {
//		blockList.clear();
//		if (allowList.contains(username)) return;
//		allowList.add(username);
//	}
//
//	public static void unlisten(@NonNull String username) {
//		if (allowList.isEmpty()) return;
//		allowList.remove(username);
//	}
//
//	public static void mute(@NonNull String username) {
//		allowList.clear();
//		if (blockList.contains(username)) return;
//		blockList.add(username);
//	}
//
//	public static void unmute(@NonNull String username) {
//		if (blockList.isEmpty()) return;
//		blockList.remove(username);
//	}

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

//	public static boolean checkMuteAllowAndBlockList(@NonNull ChatMessage message) {
//		switch (message.getType()) {
//			case PUBLICCHAT:
//			case PRIVATECHAT:
//			case PRIVATECHATOUT:
//			case FRIENDSCHAT:
//			case CLAN_CHAT:
//			case CLAN_GUEST_CHAT:
//				if (isBlockedOrNotAllowed(message.getName())) return true;
//		}
//		return false;
//	}

//	public static boolean isBlockedOrNotAllowed(String name) {
//		if (!PluginHelper.getAllowList().isEmpty() &&
//			!PluginHelper.getAllowList().contains(name)) {return true;}
//		if (!PluginHelper.getBlockList().isEmpty() && PluginHelper.getBlockList().contains(name)) {
//			return true;
//		}
//		return false;
//	}
}
