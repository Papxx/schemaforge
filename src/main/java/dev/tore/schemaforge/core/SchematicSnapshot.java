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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Target state of a Litematica placement, already transformed into world coordinates.
 * {@code blocks} is keyed by {@link BlockPos#asLong()} and contains air entries where the schematic demands air.
 * Produced by {@code LitematicaAdapter.snapshot()} in P1-02.
 */
public record SchematicSnapshot(
    String placementName,
    BlockPos min,
    BlockPos max,
    Long2ObjectMap<BlockState> blocks,
    Map<Item, Integer> materialTotals
) {
}
