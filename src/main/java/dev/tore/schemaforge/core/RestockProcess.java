package dev.tore.schemaforge.core;

import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * State machine that fetches missing materials from known containers.
 * Shell from P0-04; implemented in P4-04.
 */
public final class RestockProcess {
    public enum State { IDLE, PICK_SOURCE, TRAVEL, OPEN, WAIT_SCREEN, TAKE, CLOSE, RETURN, FAILED }

    /** Implemented in P4-04. */
    public void start(Map<Item, Integer> demand) {
        throw new UnsupportedOperationException("P4-04");
    }

    /** Implemented in P4-04. */
    public void tick() {
        throw new UnsupportedOperationException("P4-04");
    }

    /** Implemented in P4-04. */
    public State state() {
        throw new UnsupportedOperationException("P4-04");
    }
}
