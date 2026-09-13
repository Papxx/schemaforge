package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;

/** Outcome of {@link PlacementSolver#solve}. Produced from P2-02 on. */
public sealed interface SolveResult permits SolveResult.Ok, SolveResult.NeedsSupport, SolveResult.Unsupported {
    record Ok(PlacementPlan plan) implements SolveResult {
    }

    record NeedsSupport(BlockPos missingSupport) implements SolveResult {
    }

    record Unsupported(String reason) implements SolveResult {
    }
}
