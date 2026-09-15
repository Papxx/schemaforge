package dev.tore.schemaforge.core;

import java.util.function.IntSupplier;

/**
 * Limits packet-producing actions per tick (hard rule 7, P2-01). Every caller that sends a packet asks
 * {@link #tryConsume()} first; the owning module calls {@link #resetTick()} once at the start of each client tick.
 * The limit is read on every call, so a changed setting applies within the current tick; values below 0 count as 0.
 * Client thread only, not synchronized.
 */
public final class ActionBudget {
    private final IntSupplier limitPerTick;
    private int used;

    public ActionBudget(IntSupplier limitPerTick) {
        this.limitPerTick = limitPerTick;
    }

    /** Takes one action from this tick's budget; returns false (and takes nothing) once it is used up. */
    public boolean tryConsume() {
        if (used >= limitPerTick.getAsInt()) return false;
        used++;
        return true;
    }

    /** Starts a new tick with the full budget. */
    public void resetTick() {
        used = 0;
    }
}
