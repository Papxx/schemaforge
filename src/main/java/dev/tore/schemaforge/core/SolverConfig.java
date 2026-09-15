package dev.tore.schemaforge.core;

/**
 * Settings that change how the PlacementSolver picks a click (P2-02); filled from the SchemaPrinter settings in P2-07.
 *
 * @param clickAdjacentOnly only click existing neighbour blocks, never the target position itself (Paper/Spigot)
 * @param lineOfSight       prefer clicks whose hit point the player can see
 */
public record SolverConfig(boolean clickAdjacentOnly, boolean lineOfSight) {
    /** Defaults from ARCHITECTURE.md §7. */
    public static SolverConfig defaults() {
        return new SolverConfig(true, true);
    }
}
