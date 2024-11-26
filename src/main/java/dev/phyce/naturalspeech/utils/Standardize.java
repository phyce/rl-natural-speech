package dev.phyce.naturalspeech.utils;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Friend;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.client.util.Text;

@Slf4j
public final class Standardize {

	@NonNull
	public static String standardName(@NonNull String name) {
		return TextUtil.removeLevelFromTargetName(Text.standardize(Text.removeTags(name)));
	}

	public static String standardNPCName(@NonNull NPC npc) {
		NPCComposition composition = npc.getTransformedComposition();
		if (composition != null) {
			Preconditions.checkNotNull(composition.getName());
			return standardName(composition.getName());
		}
		else {
			Preconditions.checkNotNull(npc.getName());
			return standardName(npc.getName());
		}
	}

	public static String standardPlayerName(@NonNull Player player) {
		Preconditions.checkNotNull(player.getName());

		return standardName(player.getName());
	}

	public static String standardFriendName(@NonNull Friend friend) {
		Preconditions.checkNotNull(friend.getName());

		return standardName(friend.getName());
	}

	public static int standardNpcID(@NonNull NPC actor) {
		NPCComposition composition = actor.getTransformedComposition();
		if (composition != null) {
			return composition.getId();
		}
		else {
			return actor.getId();
		}
	}
}
