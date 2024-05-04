package dev.phyce.naturalspeech.utils;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum Locations {
	GRAND_EXCHANGE(new WorldPoint(3148, 3506, 0), new WorldPoint(3181, 3473, 0)),
	// You can add more locations here in a similar manner

	; // Don't forget this semicolon if you're adding more entries

	private final WorldPoint start;
	private final WorldPoint end;

	Locations(WorldPoint start, WorldPoint end) {
		this.start = start;
		this.end = end;
	}

	public boolean isWorldPointInside2D(WorldPoint worldPoint) {
		return isWorldPointInside2D(worldPoint, start, end);
	}

	public static boolean isWorldPointInside2D(WorldPoint position, WorldPoint start, WorldPoint end) {
		int minX = Math.min(start.getX(), end.getX());
		int maxX = Math.max(start.getX(), end.getX());
		int minY = Math.min(start.getY(), end.getY());
		int maxY = Math.max(start.getY(), end.getY());

		return position.getX() >= minX && position.getX() <= maxX
			&& position.getY() >= minY && position.getY() <= maxY;
	}

	public static boolean inGrandExchange(WorldPoint position) {
		return Locations.GRAND_EXCHANGE.isWorldPointInside2D(position);
	}
}