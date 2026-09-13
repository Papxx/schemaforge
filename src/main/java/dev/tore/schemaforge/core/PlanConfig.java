package dev.tore.schemaforge.core;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Planner configuration; {@code clusterSize} is the cube edge length in blocks (default 5).
 * Consumed by the WorkPlanner in P1-03; filled from the SchemaPrinter settings in P2-07.
 */
public record PlanConfig(
    int clusterSize,
    Direction.Axis layerAxis,
    boolean layerAscending,
    boolean additiveOnly,
    boolean ignoreAir,
    Set<Block> skipIfWorldIs,
    Set<Block> treatAsAir,
    Set<Block> neverPlace,
    Map<Block, List<Block>> substitutes,
    Set<String> ignoreProperties
) {
}
