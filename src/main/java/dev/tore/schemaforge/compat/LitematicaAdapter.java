package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.core.SchematicSnapshot;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * The only class allowed to touch {@code fi.dy.masa.*}; all fragile accesses go through
 * MethodHandles verified at startup (hard rule 2).
 * Shell from P0-04; each method names the ticket that implements it.
 */
public final class LitematicaAdapter {
    private LitematicaAdapter() {
    }

    /** Implemented in P1-01. */
    public static boolean isPresent() {
        throw new UnsupportedOperationException("P1-01");
    }

    /** Which Litematica signatures resolved. Implemented in P0-05. */
    public static ProbeReport.Section probe() {
        throw new UnsupportedOperationException("P0-05");
    }

    /** All loaded placements. Implemented in P1-01. */
    public static List<String> placementNames() {
        throw new UnsupportedOperationException("P1-01");
    }

    /** Sub-regions resolved, mirror/rotation applied. Implemented in P1-02. */
    public static Optional<SchematicSnapshot> snapshot(String name) {
        throw new UnsupportedOperationException("P1-02");
    }

    /** Selection bounds for the buildOnlySelection setting. No ticket yet, see Backlog in docs/TASKS.md. */
    public static Optional<BlockPos[]> selectionBounds() {
        throw new UnsupportedOperationException("Backlog: buildOnlySelection");
    }

    /** Implemented in P0-05. */
    public static Optional<EasyPlaceProtocol> detectedProtocol() {
        throw new UnsupportedOperationException("P0-05");
    }
}
