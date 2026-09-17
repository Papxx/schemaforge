package dev.tore.schemaforge.core.view;

import dev.tore.schemaforge.core.PlacementPlan;

/**
 * The only way core logic sends packets (P2-03). Callers ask {@code ActionBudget.tryConsume()} before every call.
 * Production: {@code compat.McPrintActions}; tests use a recording fake.
 */
public interface PrintActions {
    /**
     * Selects {@code hotbarSlot}, looks at {@code plan.hitVec()} and clicks it (sneaking if {@code plan.sneak()}).
     * The click may run later in the same tick, after the rotation packet has been sent.
     *
     * @return false if nothing was sent (for example the player or the level is gone)
     */
    boolean place(PlacementPlan plan, int hotbarSlot);
}
