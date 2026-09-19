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
package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Temporary support blocks (P5-02): a block from the whitelist under (or next to) a target that has nothing to be
 * clicked against, removed again at the end of the cluster visit. Only used with additive-only off - SchemaPrinter
 * creates no instance otherwise, so the printer has no way to break anything.
 * Removal reuses {@link UndoSession}: only blocks that are still exactly what was placed are touched.
 */
public final class TempSupports {
    private final List<Block> whitelist;
    private final Predicate<BlockPos> reserved;
    private final UndoSession.Actions breaker;
    private final Consumer<String> notes;
    private final List<PlacementLog.Entry> placed = new ArrayList<>();

    private Optional<UndoSession> clearing = Optional.empty();
    private int placedTotal;
    private int removedTotal;

    /**
     * @param whitelist blocks that may serve as support, in order of preference
     * @param reserved  positions the schematic wants for itself; a support there would end up as a wrong block
     * @param breaker   one mining step on a block
     */
    public TempSupports(List<Block> whitelist, Predicate<BlockPos> reserved, UndoSession.Actions breaker,
                        Consumer<String> notes) {
        this.whitelist = List.copyOf(whitelist);
        this.reserved = reserved;
        this.breaker = breaker;
        this.notes = notes;
    }

    /**
     * Positions inside the schematic box that are not known to be air. Unknown positions (schematic chunk not loaded)
     * count as reserved: the schematic may want a block there.
     */
    public static Predicate<BlockPos> reservedBy(SchematicSnapshot snap) {
        return pos -> {
            boolean inside = pos.getX() >= snap.min().getX() && pos.getX() <= snap.max().getX()
                && pos.getY() >= snap.min().getY() && pos.getY() <= snap.max().getY()
                && pos.getZ() >= snap.min().getZ() && pos.getZ() <= snap.max().getZ();
            if (!inside) return false;
            BlockState wanted = snap.blocks().get(pos.asLong());
            return wanted == null || !wanted.isAir();
        };
    }

    /** True if a support may go at {@code at} so that {@code target} can be placed next to it. */
    public boolean allowedFor(BlockState target, BlockPos at, WorldView world) {
        if (!PlacementSolver.standsWithoutSupport(target)) return false;
        if (!world.isChunkLoaded(at) || reserved.test(at)) return false;
        return world.getBlockState(at).canBeReplaced();
    }

    /** First whitelisted block the inventory holds. */
    public Optional<Block> pick(InventoryView inv) {
        return whitelist.stream().filter(block -> inv.count(block.asItem()) > 0).findFirst();
    }

    public void placed(BlockPos pos, BlockState state) {
        placed.add(new PlacementLog.Entry(pos.immutable(), state, true, 0L));
        placedTotal++;
    }

    /** Supports of this visit that have not been cleared away yet. */
    public boolean pending() {
        return !placed.isEmpty() || clearing.map(UndoSession::running).orElse(false);
    }

    /** One removal step; the first call starts clearing everything placed during this visit, newest first. */
    public void tickClearing(WorldView world, PlayerView player, ActionBudget budget) {
        if (clearing.isEmpty() || !clearing.get().running()) {
            if (placed.isEmpty()) return;
            clearing = Optional.of(new UndoSession(placed, placed.size(), breaker, budget, notes));
            placed.clear();
        }
        UndoSession session = clearing.get();
        int before = session.removedCount();
        session.tick(world, player);
        removedTotal += session.removedCount() - before;
    }

    /** A new cluster visit: supports left over from an aborted visit are forgotten (they are in the log as temp). */
    public void reset() {
        placed.clear();
        clearing = Optional.empty();
    }

    public int placedCount() {
        return placedTotal;
    }

    public int removedCount() {
        return removedTotal;
    }
}
