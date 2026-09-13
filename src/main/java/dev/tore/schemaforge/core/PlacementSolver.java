package dev.tore.schemaforge.core;

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.WorldView;

/**
 * BlockState to PlacementPlan (click face, look direction, hand item).
 * Shell from P0-04; base block classes in P2-02, dependent blocks in P2-04.
 */
public final class PlacementSolver {
    /** Implemented in P2-02, extended in P2-04. */
    public SolveResult solve(BlockTask task, WorldView world, PlayerView player, EasyPlaceProtocol proto) {
        throw new UnsupportedOperationException("P2-02");
    }
}
