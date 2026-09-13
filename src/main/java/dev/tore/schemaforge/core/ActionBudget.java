package dev.tore.schemaforge.core;

import java.util.function.IntSupplier;

/**
 * Limits packet-producing actions per tick (hard rule 7).
 * Shell from P0-04; implemented in P2-01.
 */
public final class ActionBudget {
    public ActionBudget(IntSupplier limitPerTick) {
        throw new UnsupportedOperationException("P2-01");
    }

    /** Returns false once this tick's budget is used up. Implemented in P2-01. */
    public boolean tryConsume() {
        throw new UnsupportedOperationException("P2-01");
    }

    /** Implemented in P2-01. */
    public void resetTick() {
        throw new UnsupportedOperationException("P2-01");
    }
}
