package net.runelite.client.plugins.naturalspeech.src.main.java.dev.phyce.naturalspeech.enums;

import net.runelite.api.coords.WorldPoint;

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

    public WorldPoint getStart() {
        return start;
    }

    public WorldPoint getEnd() {
        return end;
    }
}