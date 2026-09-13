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
