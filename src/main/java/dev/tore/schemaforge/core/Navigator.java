package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.PlayerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Hands cluster and container targets to a pathfinder (P3-02). Knows nothing about Baritone: the production
 * implementation of {@link Pathing} is {@code BaritoneBridge.PATHING}, tests use a fake.
 * One target at a time; the caller ticks it while the build session is in TRAVELING (ARCHITECTURE.md §5).
 */
public final class Navigator {
    /** The pathfinder as the Navigator needs it; every method must be safe to call when {@link #isPresent()} is false. */
    public interface Pathing {
        boolean isPresent();

        void gotoNear(BlockPos pos, int radius);

        boolean isPathing();

        void stop();
    }

    public enum Progress { TRAVELING, ARRIVED, FAILED }

    /** Radius handed to the pathfinder, so the player ends up next to the cluster instead of inside it. */
    public static final int GOAL_RADIUS = 3;

    /** Distance from the target that counts as arrived; the goal radius plus room for the pathfinder's own metric. */
    public static final double ARRIVE_DISTANCE = GOAL_RADIUS + 2.0;

    public static final long TIMEOUT_MS = 20_000;

    /** Travel attempts per target before it is blacklisted (AK1: no cluster is approached more often). */
    public static final int MAX_ATTEMPTS = 3;

    /** Grace period before a pathfinder that is not pathing counts as failed; it needs a few ticks to start. */
    private static final long START_GRACE_MS = 1_500;

    private final Pathing pathing;
    private final LongSupplier clockMs;
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private final Set<BlockPos> blacklist = new HashSet<>();

    private BlockPos target;
    private long startedAt;

    public Navigator(Pathing pathing, LongSupplier clockMs) {
        this.pathing = pathing;
        this.clockMs = clockMs;
    }

    /** False without a pathfinder; the caller then works every cluster from where it stands. */
    public boolean available() {
        return pathing.isPresent();
    }

    /** Starts walking to {@code target}; false if there is no pathfinder or the target is blacklisted. */
    public boolean start(BlockPos target) {
        if (!available() || blacklisted(target)) return false;
        this.target = target;
        startedAt = clockMs.getAsLong();
        pathing.gotoNear(target, GOAL_RADIUS);
        return true;
    }

    /**
     * One step of the running travel. FAILED counts an attempt and stops the pathfinder; after
     * {@link #MAX_ATTEMPTS} the target is blacklisted.
     */
    public Progress tick(PlayerView player) {
        if (target == null) return Progress.FAILED;
        if (player.eyePos().distanceTo(Vec3.atCenterOf(target)) <= ARRIVE_DISTANCE) {
            cancel();
            return Progress.ARRIVED;
        }
        long elapsed = clockMs.getAsLong() - startedAt;
        // Not pathing any more without having arrived: no path was found, or something else took control.
        if (elapsed >= TIMEOUT_MS || (elapsed >= START_GRACE_MS && !pathing.isPathing())) {
            BlockPos failed = target;
            cancel();
            int count = attempts.merge(failed, 1, Integer::sum);
            if (count >= MAX_ATTEMPTS) blacklist.add(failed);
            return Progress.FAILED;
        }
        return Progress.TRAVELING;
    }

    /** Stops the pathfinder without counting an attempt (arrival, pause, end of the run). */
    public void cancel() {
        target = null;
        if (available()) pathing.stop();
    }

    /** True once the target failed {@link #MAX_ATTEMPTS} times; it is never approached again in this run. */
    public boolean blacklisted(BlockPos target) {
        return blacklist.contains(target);
    }

    /** Failed attempts so far, for messages and tests. */
    public int attempts(BlockPos target) {
        return attempts.getOrDefault(target, 0);
    }

    /** The target being walked to, or null. */
    public BlockPos target() {
        return target;
    }
}
