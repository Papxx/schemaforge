package dev.tore.schemaforge.compat;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * The only class allowed to touch {@code baritone.api.*} (hard rule 1).
 * Shell from P0-04; navigation is implemented in P3-01, the settings guard in P2-06.
 */
public final class BaritoneBridge {
    private BaritoneBridge() {
    }

    /** Implemented in P3-01. */
    public static boolean isPresent() {
        throw new UnsupportedOperationException("P3-01");
    }

    /** GoalNear. Implemented in P3-01. */
    public static void gotoNear(BlockPos pos, int radius) {
        throw new UnsupportedOperationException("P3-01");
    }

    /** GoalGetToBlock. Implemented in P3-01. */
    public static void gotoBlock(BlockPos pos) {
        throw new UnsupportedOperationException("P3-01");
    }

    /** Implemented in P3-01. */
    public static boolean isPathing() {
        throw new UnsupportedOperationException("P3-01");
    }

    /** Implemented in P3-01. */
    public static void stop() {
        throw new UnsupportedOperationException("P3-01");
    }

    /** Protection zone around the placement (avoidBreaking). Implemented in P2-06. */
    public static void setAvoidBreaking(Set<BlockPos> protectedArea) {
        throw new UnsupportedOperationException("P2-06");
    }

    /** Restores Baritone settings on deactivation. Implemented in P2-06. */
    public static void restoreSettings() {
        throw new UnsupportedOperationException("P2-06");
    }
}
