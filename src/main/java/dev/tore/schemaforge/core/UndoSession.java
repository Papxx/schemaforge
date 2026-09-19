package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Removes the last blocks SchemaForge placed (P5-01), newest first and only where the world still holds
 * exactly what was logged - anything else belongs to someone else and is left alone.
 * Breaking goes through {@link Actions}, one step per budget unit.
 */
public final class UndoSession {
    /** What the undo needs from the game. */
    public interface Actions {
        /** One mining step on the block; false = nothing sent. */
        boolean breakBlock(BlockPos pos);
    }

    /** Ticks of mining on one block before it is given up on. */
    public static final int BREAK_TIMEOUT_TICKS = 200;

    private final Deque<PlacementLog.Entry> queue;
    private final Actions actions;
    private final ActionBudget budget;
    private final Consumer<String> notes;
    private final int requested;

    private Optional<PlacementLog.Entry> current = Optional.empty();
    private int tick;
    private int startedBreakingAt;
    private int removed;
    private int skipped;

    /**
     * @param entries log entries oldest first, as {@link PlacementLog#entries()} returns them
     * @param count   how many of the newest entries to undo
     */
    public UndoSession(List<PlacementLog.Entry> entries, int count, Actions actions, ActionBudget budget,
                       Consumer<String> notes) {
        List<PlacementLog.Entry> newestFirst = new ArrayList<>(entries);
        java.util.Collections.reverse(newestFirst);
        this.queue = new ArrayDeque<>(newestFirst.stream().limit(Math.max(0, count)).toList());
        this.requested = queue.size();
        this.actions = actions;
        this.budget = budget;
        this.notes = notes;
    }

    public void tick(WorldView world, PlayerView player) {
        tick++;
        if (current.isEmpty() && !pickNext(world, player)) return;

        PlacementLog.Entry entry = current.get();
        if (!matches(world, entry)) {
            // Gone already, by our own mining or someone else's.
            removed++;
            current = Optional.empty();
            return;
        }
        if (tick - startedBreakingAt > BREAK_TIMEOUT_TICKS) {
            notes.accept("Could not break the block at " + describe(entry.pos()) + "; leaving it.");
            skipped++;
            current = Optional.empty();
            return;
        }
        if (!budget.tryConsume()) return;
        actions.breakBlock(entry.pos());
    }

    /** True while there is still something to undo. */
    public boolean running() {
        return current.isPresent() || !queue.isEmpty();
    }

    public int removedCount() {
        return removed;
    }

    /** Blocks that were in the log but could not be removed, or no longer matched. */
    public int skippedCount() {
        return skipped;
    }

    public int requestedCount() {
        return requested;
    }

    /** The block being removed right now. */
    public Optional<BlockPos> target() {
        return current.map(PlacementLog.Entry::pos);
    }

    /** Takes the next entry that is still in reach and still ours; false when nothing is left this tick. */
    private boolean pickNext(WorldView world, PlayerView player) {
        while (!queue.isEmpty()) {
            PlacementLog.Entry entry = queue.poll();
            if (!world.isChunkLoaded(entry.pos())) {
                notes.accept("The block at " + describe(entry.pos()) + " is not loaded; leaving it.");
                skipped++;
                continue;
            }
            if (!matches(world, entry)) {
                // Someone else changed it, or it was never placed: not ours to remove.
                skipped++;
                continue;
            }
            if (!inReach(entry.pos(), player)) {
                notes.accept("The block at " + describe(entry.pos()) + " is out of reach; leaving it.");
                skipped++;
                continue;
            }
            current = Optional.of(entry);
            startedBreakingAt = tick;
            return true;
        }
        return false;
    }

    /** Only a block that is still exactly what was logged counts as ours. */
    private static boolean matches(WorldView world, PlacementLog.Entry entry) {
        BlockState state = world.getBlockState(entry.pos());
        return state.equals(entry.block());
    }

    private static boolean inReach(BlockPos pos, PlayerView player) {
        return player.eyePos().distanceTo(Vec3.atCenterOf(pos)) <= player.reach();
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
