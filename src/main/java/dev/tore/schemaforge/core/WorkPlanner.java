package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.WorldView;

import java.util.List;

/**
 * Diff to tasks to clusters to build order.
 * Shell from P0-04; {@code plan} is implemented in P1-03, {@code refresh} in P2-03.
 */
public final class WorkPlanner {
    public WorkPlanner(PlanConfig cfg) {
        throw new UnsupportedOperationException("P1-03");
    }

    /** Implemented in P1-03. */
    public List<Cluster> plan(SchematicSnapshot snap, WorldView world) {
        throw new UnsupportedOperationException("P1-03");
    }

    /** Re-reads the world state of a cluster. Implemented in P2-03. */
    public List<BlockTask> refresh(Cluster c, WorldView world) {
        throw new UnsupportedOperationException("P2-03");
    }
}
