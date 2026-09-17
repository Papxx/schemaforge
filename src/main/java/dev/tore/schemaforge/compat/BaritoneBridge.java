package dev.tore.schemaforge.compat;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import dev.tore.schemaforge.core.AdditiveOnlyGuard;
import net.minecraft.core.BlockPos;

/**
 * The only class allowed to touch {@code baritone.api.*} (hard rule 1).
 * Baritone classes are only resolved when a method needs them; callers check {@link #isPresent()} first.
 * Shell from P0-04; navigation is implemented in P3-01, presence check and break settings in P2-06.
 */
public final class BaritoneBridge {
    private static final String API_CLASS = "baritone.api.BaritoneAPI";

    /** allowBreak and allowBreakAnyway for the AdditiveOnlyGuard (P2-06). Values are passed through as the same objects. */
    public static final AdditiveOnlyGuard.PathfinderSettings BREAK_SETTINGS = new AdditiveOnlyGuard.PathfinderSettings() {
        @Override
        public boolean isPresent() {
            return BaritoneBridge.isPresent();
        }

        @Override
        public AdditiveOnlyGuard.BreakSettings read() {
            Settings settings = BaritoneAPI.getSettings();
            return new AdditiveOnlyGuard.BreakSettings(settings.allowBreak.value, settings.allowBreakAnyway.value);
        }

        @Override
        public void write(AdditiveOnlyGuard.BreakSettings values) {
            Settings settings = BaritoneAPI.getSettings();
            // Boxing true/false yields the cached Boolean instances, which are the defaults' instances as well.
            settings.allowBreak.value = values.allowBreak();
            settings.allowBreakAnyway.value = values.allowBreakAnyway();
        }
    };

    private BaritoneBridge() {
    }

    /** True if baritone.api can be loaded; checked without initializing any Baritone class (P2-06). */
    public static boolean isPresent() {
        return SignatureCheck.load(BaritoneBridge.class.getClassLoader(), API_CLASS).isPresent();
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
}
