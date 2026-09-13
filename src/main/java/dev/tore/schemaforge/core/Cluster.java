package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Spatially connected task group and Baritone travel target. Produced by the WorkPlanner in P1-03. */
public record Cluster(int index, BlockPos center, List<BlockTask> tasks) {
}
