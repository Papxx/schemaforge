package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Learned container contents plus persistence.
 * Shell from P0-04; implemented in P4-01.
 */
public final class ContainerIndex {
    public record Entry(BlockPos pos, ContainerType type, long lastSeenEpochMs, Map<Item, Integer> items, boolean stale) {
    }

    /** Implemented in P4-01. */
    public void learn(BlockPos pos, ContainerType type, Map<Item, Integer> items) {
        throw new UnsupportedOperationException("P4-01");
    }

    /** Sorted by distance, stale entries last. Implemented in P4-01. */
    public List<Entry> sourcesFor(Item item, Vec3 from) {
        throw new UnsupportedOperationException("P4-01");
    }

    /** Implemented in P4-01. */
    public void markStale(Duration olderThan) {
        throw new UnsupportedOperationException("P4-01");
    }

    /** Implemented in P4-01. */
    public void save(Path file) {
        throw new UnsupportedOperationException("P4-01");
    }

    /** Implemented in P4-01. */
    public static ContainerIndex load(Path file) {
        throw new UnsupportedOperationException("P4-01");
    }
}
