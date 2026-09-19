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

    /**
     * Swaps the stacks of inventory index {@code inventorySlot} (0–35) and hotbar index {@code hotbarSlot} (0–8) with one
     * click in the player inventory (P2-05).
     *
     * @return false if nothing was sent (for example another container screen is open)
     */
    boolean swapToHotbar(int inventorySlot, int hotbarSlot);

    /**
     * Selects {@code hotbarSlot}, looks at {@code plan.hitVec()} and uses the held item there - a bucket emptying into
     * the block in front of the clicked face (P5-03). The server raycasts from the rotation in the use packet.
     *
     * @return false if nothing was sent
     */
    boolean useBucket(PlacementPlan plan, int hotbarSlot);
}
