package dev.phyce.naturalspeech.utils;

import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.Player;

@Slf4j
@PluginSingleton
public final class ClientHelper {
	private final Client client;

	@Inject
	public ClientHelper(Client client) {
		this.client = client;
	}

	@CheckForNull
	public Player getPlayer(@NonNull EntityID eid) {

		if (eid.isUser()) {
			return client.getLocalPlayer();
		}

		return Arrays.stream(client.getCachedPlayers())
			.filter(Objects::nonNull)
			.filter(player -> player.getName() != null)
			.filter(eid::isPlayer)
			.findFirst()
			.orElse(null);
	}

	@NonNull
	public Gender getGender(@NonNull EntityID eid) {
		Player player = getPlayer(eid);
		if (player != null) {
			return Gender.fromPlayer(player);
		}
		else {
			// TODO(Louis): Figure out NPC gender using OSRS Wiki
			// randomize using entity id
			return Gender.fromInt(eid.hashCode());
		}
	}

	public boolean isFriend(@NonNull EntityID entityID) {
		return getFriends().contains(entityID);
	}

	public boolean isLocalPlayer(@NonNull EntityID entityID) {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;
		if (localPlayer.getName() == null) return false;

		return entityID.isName(localPlayer.getName());
	}

	public boolean isFriend(@NonNull String username) {
		EntityID entityID = EntityID.name(username);

		return getFriends().contains(entityID);
	}

	public int getLevel(@NonNull EntityID eid) {
		Player targetPlayer = getPlayer(eid);

		if (targetPlayer == null) return Integer.MAX_VALUE;

		return targetPlayer.getCombatLevel();
	}

	@NonNull
	public List<EntityID> getFriends() {
		Friend[] members = client.getFriendContainer().getMembers();
		if (members == null) {
			log.warn("client.getFriendContainer().getMembers() returned null");
			return new ArrayList<>();
		}

		return Arrays.stream(members)
			.filter(Objects::nonNull)
			.map(Friend::getName)
			.filter(Objects::nonNull)
			.map(EntityID::name)
			.collect(Collectors.toList());
	}

	public int widgetModelIdToNpcId(int modelId) {
		int[] configs = client.getNpcDefinition(modelId).getConfigs();
		if (configs == null) {
			return modelId;
		} else {
			return Arrays.stream(configs)
				.filter(id -> id != -1)
				.findFirst()
				.orElse(modelId);
		}
	}
}
