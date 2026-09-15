package dev.tore.schemaforge.core;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
    /**
     * Defaults from ARCHITECTURE.md §7. Used by {@code .sf preview} (P1-05) until the module settings exist (P2-07).
     * A method rather than a constant because {@link Blocks} needs bootstrapped registries.
     */
    public static PlanConfig defaults() {
        return new PlanConfig(5, Direction.Axis.Y, true, true, true,
            Set.of(), Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS), Set.of(Blocks.TNT),
            Map.of(), Set.of("waterlogged"));
    }
}
