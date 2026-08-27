package dev.phyce.naturalspeech.enums;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

public enum BossRegions {
	ALCHEMICAL_HYDRA(Set.of(5536), Set.of(), true),
	CALVARION(Set.of(7604, 12601)),
	CERBERUS(Set.of(4883, 5139, 5140, 5395)),
	COLOSSEUM(Set.of(7216, 7316)),
	COMMANDER_ZILYANA(Set.of(11601, 11602, 11858)),
	COX(Set.of(
		13136, // End of floor
		13137, 13393, // Lobbies/Room transitions
		13138, 13394, // Vasa/Tekton/Vespula Lizardmen/Skeletal Mystics/Guardian
		13139, 13395, 13140, 13396, // Puzzle rooms/bosses
		13141, 13397, // Rest room
		13145, 13401, // New floor
		12889 // Olm
	)),
	CRAZY_ARCHAEOLOGIST(Set.of(11833)),
	DERANGED_ARCHAEOLOGIST(Set.of(14649, 14650)),
	DOOM_OF_MOKHAIOTL(Set.of(5268, 5269, 13668, 14180)),
	FIGHT_CAVE(Set.of(9551)),
	GENERAL_GRAARDOR(Set.of(11347)),
	INFERNO(Set.of(9043)),
	KALPHITE_QUEEN(Set.of(13972), Set.of(0), false),
	KREEARRA(Set.of(11346)),
	KRIL_TSUTSAROTH(Set.of(11603)),
	LEVIATHAN(Set.of(8291)),
	MAGGOT_KING(Set.of(11645, 11901)),
	MUSPAH(Set.of(11330, 11681)),
	NEX(Set.of(11344, 11345, 11600, 11601)),
	NIGHTMARE(Set.of(15515)),
	ROYAL_TITANS(Set.of(11669, 11925)),
	SUCELLUS(Set.of(12132)),
	TOA(Set.of(
		14160, // Nexus Lobby
		15698, // Crondis
		15700, // Zebak
		14162, // Scabaras
		14164, // Kephri
		15186, // Apmeken
		15188, // Ba-Ba
		14674, // Het
		14676, // Akkha
		15184, 15696 // Wardens
	)),
	TOB(Set.of(
		12613, // Maiden
		13125, // Bloat
		13122, // Nylocas
		13123, 13379, // Sotetseg/maze
		12612, // Xarpus
		12611 // Verzik
	)),
	VARDORVIS(Set.of(4405, 4661)),
	VORKATH(Set.of(9023)),
	WHISPERER(Set.of(10595)),
	YAMA(Set.of(5789, 6045)),
	;

	private final Set<Integer> regionIds;
	private final Set<Integer> planes;
	private final boolean onlyInInstance;

	BossRegions(Set<Integer> regionIds) {
		this(regionIds, Set.of(), false);
	}

	BossRegions(Set<Integer> regionIds, Set<Integer> planes, boolean onlyInInstance) {
		this.regionIds = regionIds;
		this.planes = planes;
		this.onlyInInstance = onlyInInstance;
	}

	public static boolean inBossRegion(Client client) {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;

		WorldView worldView = localPlayer.getWorldView();
		int regionId = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()).getRegionID();

		for (BossRegions bossRegion : values()) {
			if (bossRegion.onlyInInstance && !worldView.isInstance()) continue;
			if (!bossRegion.planes.isEmpty() && !bossRegion.planes.contains(worldView.getPlane())) continue;
			if (bossRegion.regionIds.contains(regionId)) return true;
		}
		return false;
	}
}
