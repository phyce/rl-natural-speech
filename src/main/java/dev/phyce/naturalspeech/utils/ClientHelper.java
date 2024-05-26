package dev.phyce.naturalspeech.utils;

import com.google.common.base.Optional;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.enums.Gender;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.NPC;
import net.runelite.api.Player;

@Slf4j
@PluginSingleton
public final class ClientHelper {
	private final Client client;

	@Inject
	public ClientHelper(Client client) {
		this.client = client;
	}

	public Optional<Player> getPlayer(@NonNull EntityID eid) {

		if (eid.isUser()) return Optional.of(client.getLocalPlayer());

		return Optional.fromJavaUtil(Arrays.stream(client.getCachedPlayers())
			.filter(Objects::nonNull)
			.filter(player -> player.getName() != null)
			.filter(eid::isPlayer)
			.findFirst());
	}

	@NonNull
	public Gender getGender(@NonNull EntityID eid) {
		Optional<Player> player = getPlayer(eid);
		if (player.isPresent()) {
			return Gender.fromPlayer(player.get());
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
		Optional<Player> targetPlayer = getPlayer(eid);

		if (!targetPlayer.isPresent()) return Integer.MAX_VALUE;

		return targetPlayer.get().getCombatLevel();
	}

	public Optional<NPC> getNPC(@NonNull EntityID entityID) {
		return Optional.fromJavaUtil(client.getNpcs().stream()
			.filter(entityID::isNPC)
			.findFirst());
	}

	public Optional<Actor> getActor(@NonNull EntityID eid) {
		Optional<Player> player = getPlayer(eid);
		if (player.isPresent()) return player.transform(p -> p);

		Optional<NPC> npc = getNPC(eid);
		if (npc.isPresent()) return npc.transform(n -> n);

		return Optional.absent();
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

		if (configs == null) return modelId;
		else return Arrays.stream(configs)
			.filter(id -> id != -1)
			.findFirst()
			.orElse(modelId);
	}
}
