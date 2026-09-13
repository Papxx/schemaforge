package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Single diff entry between target and world state. Produced by the WorkPlanner in P1-03. */
public record BlockTask(BlockPos pos, BlockState target, BlockState current, TaskKind kind, int priority) {
}
