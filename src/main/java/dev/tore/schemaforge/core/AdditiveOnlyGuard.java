package dev.tore.schemaforge.core;

import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the pathfinder from breaking blocks while the printer runs in additive-only mode (P2-06, ARCHITECTURE.md §4).
 * The printer itself never breaks: it only handles PLACE tasks and {@code PrintActions} has no break action.
 * baritone.api has no position-based break protection, so breaking is forbidden everywhere while engaged.
 * Client thread only.
 */
public final class AdditiveOnlyGuard {
    /**
     * Baritone's {@code allowBreak} and {@code allowBreakAnyway}. The list is kept as the same object that was read:
     * Baritone's {@code #modified} compares {@code value == defaultValue}, so writing back a copy would count as a change.
     */
    public record BreakSettings(boolean allowBreak, List<Block> allowBreakAnyway) {
    }

    /** Reads and writes the pathfinder's break settings; production: {@code compat.BaritoneBridge.BREAK_SETTINGS}. */
    public interface PathfinderSettings {
        /** False if no pathfinder is installed; {@link #read()} and {@link #write} are never called then. */
        boolean isPresent();

        BreakSettings read();

        void write(BreakSettings settings);
    }

    private final PathfinderSettings pathfinder;
    private Optional<BreakSettings> saved = Optional.empty();

    public AdditiveOnlyGuard(PathfinderSettings pathfinder) {
        this.pathfinder = pathfinder;
    }

    /** On start: remembers the current settings and forbids breaking, if {@code additiveOnly} and not engaged yet. */
    public void engage(boolean additiveOnly) {
        if (!additiveOnly || saved.isPresent() || !pathfinder.isPresent()) return;
        saved = Optional.of(pathfinder.read());
        // Mutable like Baritone's own lists.
        pathfinder.write(new BreakSettings(false, new ArrayList<>()));
    }

    /** On stop: writes back the remembered settings (the same objects); does nothing if not engaged. */
    public void release() {
        saved.ifPresent(pathfinder::write);
        saved = Optional.empty();
    }

    public boolean engaged() {
        return saved.isPresent();
    }
}
