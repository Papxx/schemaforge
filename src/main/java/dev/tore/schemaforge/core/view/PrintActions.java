/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
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
