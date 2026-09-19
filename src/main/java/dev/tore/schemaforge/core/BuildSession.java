package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.PrintActions;
import dev.tore.schemaforge.core.view.SafetyView;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * State machine of one print run (P2-07, ARCHITECTURE.md §5): plan, work through the clusters, verify, repeat up to
 * {@link #MAX_ROUNDS} times. The module SchemaPrinter owns settings, views and chat; this class only decides.
 * Client thread only.
 */
public final class BuildSession {
    public enum State { IDLE, PLANNING, BUILDING, TRAVELING, RESTOCKING, PAUSED, VERIFYING, DONE }

    /** Building rounds; later rounds catch blocks whose support only appeared in a later cluster. */
    public static final int MAX_ROUNDS = 3;

    /**
     * @param clusterIndex 1-based position of the current cluster, 0 before the first one
     * @param mismatched   SKIP tasks with {@link SkipReason#MISMATCH_ADDITIVE_ONLY} in the latest plan
     * @param remaining    PLACE tasks in the latest plan
     */
    public record Status(State state, String placement, int round, int clusterIndex, int clusterCount,
                         int placed, double blocksPerMinute, int mismatched, int remaining) {
    }

    private static final long MIN_ACTIVE_MS = 1000;

    private final SchematicSnapshot snap;
    private final WorkPlanner planner;
    private final Printer printer;
    private final Navigator navigator;
    private final SafetyMonitor safety;
    private final LongSupplier clockMs;
    private final BiConsumer<State, State> onStateChange;
    private final Consumer<String> notes;

    private State state = State.IDLE;
    private State beforePause = State.IDLE;
    private List<Cluster> clusters = new ArrayList<>();
    /** Index into {@link #clusters} of the cluster the printer works on; -1 before the first. */
    private int current = -1;
    /** True while the printer's placed count belongs to a cluster visit not yet added to {@link #placedBefore}. */
    private boolean visitOpen;
    private int round = 1;
    private int placedBefore;
    private int placedInRound;
    private int mismatched;
    private int remaining;
    private long activeMs;
    private long activeSince;
    /** Cluster the first round starts at; set by {@link #start(int)} for a resumed build (P3-04). */
    private int resumeFrom;
    /** True while the running round skipped clusters because of a resume; it may not end the build on its own. */
    private boolean resumedRound;
    /** The safety stop that paused the build, empty after a manual pause or while running (P3-03). */
    private Optional<SafetyMonitor.Trigger> safetyPause = Optional.empty();

    /**
     * @param navigator walks to clusters that are out of reach (P3-02); without a pathfinder every cluster is worked
     *                  on from where the player stands
     * @param safety    pauses the build on damage, hunger, a player nearby and the like (P3-03)
     * @param notes     user-facing one-liners, e.g. about a cluster that cannot be reached
     */
    public BuildSession(SchematicSnapshot snap, WorkPlanner planner, Printer printer, Navigator navigator,
                        SafetyMonitor safety, LongSupplier clockMs, BiConsumer<State, State> onStateChange,
                        Consumer<String> notes) {
        this.snap = snap;
        this.planner = planner;
        this.printer = printer;
        this.navigator = navigator;
        this.safety = safety;
        this.clockMs = clockMs;
        this.onStateChange = onStateChange;
        this.notes = notes;
    }

    /** IDLE → PLANNING; ignored in any other state. */
    public void start() {
        start(0);
    }

    /**
     * Starts and skips straight to {@code fromCluster} (0-based) after planning (P3-04).
     * Clusters before it are not visited again; whatever is still open there is caught by the VERIFYING round.
     */
    public void start(int fromCluster) {
        if (state != State.IDLE) return;
        resumeFrom = Math.max(0, fromCluster);
        activeSince = clockMs.getAsLong();
        enter(State.PLANNING);
    }

    public void tick(WorldView world, PlayerView player, InventoryView inv, SafetyView safetyView, PrintActions actions) {
        if (state == State.PAUSED) {
            autoResume(world, player, inv, safetyView);
            return;
        }
        if (running() && pauseForSafety(world, player, inv, safetyView)) return;
        switch (state) {
            case PLANNING -> {
                replan(world, player);
                enter(State.BUILDING);
            }
            case BUILDING -> build(world, player, inv, actions);
            case TRAVELING -> travel(player);
            case VERIFYING -> verify(world, player);
            // RESTOCKING (P4-04) is not entered yet.
            case IDLE, RESTOCKING, PAUSED, DONE -> {
            }
        }
    }

    /** Pauses a running session; false if there is nothing to pause. */
    public boolean pause() {
        if (!running()) return false;
        activeMs += clockMs.getAsLong() - activeSince;
        beforePause = state;
        if (state == State.TRAVELING) {
            // Hand the pathfinder back; on resume the cluster is picked again and walked to from wherever we stand.
            navigator.cancel();
            current--;
            beforePause = State.BUILDING;
        }
        enter(State.PAUSED);
        return true;
    }

    /** Continues where {@link #pause()} stopped; false if not paused. */
    public boolean resume() {
        if (state != State.PAUSED) return false;
        safetyPause = Optional.empty();
        activeSince = clockMs.getAsLong();
        enter(beforePause);
        return true;
    }

    public State state() {
        return state;
    }

    /** The safety stop that paused the build; empty while running or after a manual pause (P3-03). */
    public Optional<SafetyMonitor.Trigger> safetyPause() {
        return safetyPause;
    }

    /** Pauses if a safety stop fires; true means this tick does nothing else (P3-03). */
    private boolean pauseForSafety(WorldView world, PlayerView player, InventoryView inv, SafetyView view) {
        Optional<SafetyMonitor.Trigger> trigger = safety.check(view, chunkLoaded(world, player), printer.outOfMaterials(inv));
        if (trigger.isEmpty()) return false;
        pause();
        safetyPause = trigger;
        notes.accept(trigger.get().message() + (trigger.get().autoResume()
            ? " Paused; continues once that is over."
            : " Paused, continue with .sf resume."));
        return true;
    }

    /** Continues a build paused by a safety stop as soon as the reason is gone (P3-03). */
    private void autoResume(WorldView world, PlayerView player, InventoryView inv, SafetyView view) {
        if (safetyPause.isEmpty()) return;
        SafetyMonitor.Trigger trigger = safetyPause.get();
        if (!trigger.autoResume()) return;
        if (!safety.cleared(trigger, view, chunkLoaded(world, player), printer.outOfMaterials(inv))) return;
        resume();
        notes.accept("Continuing, " + trigger.reason() + " is over.");
    }

    /** The chunk being worked in: the current cluster, or where the player stands before the first one. */
    private boolean chunkLoaded(WorldView world, PlayerView player) {
        BlockPos at = current >= 0 && current < clusters.size()
            ? clusters.get(current).center()
            : BlockPos.containing(player.eyePos());
        return world.isChunkLoaded(at);
    }

    public Status status() {
        int placed = placedBefore + (visitOpen ? printer.placedCount() : 0);
        long active = activeMs + (running() ? clockMs.getAsLong() - activeSince : 0);
        double perMinute = active < MIN_ACTIVE_MS ? 0 : placed * 60_000.0 / active;
        return new Status(state, snap.placementName(), round, current + 1, clusters.size(), placed, perMinute, mismatched, remaining);
    }

    private void build(WorldView world, PlayerView player, InventoryView inv, PrintActions actions) {
        if (!visitOpen || printer.clusterDone()) {
            if (!nextCluster(player)) {
                enter(State.VERIFYING);
                return;
            }
            // nextCluster switched to TRAVELING: that cluster is out of reach and is walked to first.
            if (state == State.TRAVELING) return;
        }
        int before = printer.placedCount();
        printer.tick(world, player, inv, actions);
        placedInRound += printer.placedCount() - before;
    }

    /**
     * Moves to the next cluster with a PLACE task; false if none is left in this round.
     * A cluster out of reach is walked to first (state TRAVELING), blacklisted ones are skipped (P3-02).
     */
    private boolean nextCluster(PlayerView player) {
        if (visitOpen) placedBefore += printer.placedCount();
        visitOpen = false;
        while (++current < clusters.size()) {
            Cluster c = clusters.get(current);
            if (c.tasks().stream().noneMatch(t -> t.kind() == TaskKind.PLACE)) continue;
            if (navigator.blacklisted(c.center())) continue;
            if (!inReach(c, player) && navigator.start(c.center())) {
                enter(State.TRAVELING);
                return true;
            }
            startVisit(c);
            return true;
        }
        current = clusters.size() - 1;
        return false;
    }

    /** One step of the walk to the current cluster; on arrival the printer takes over again (P3-02). */
    private void travel(PlayerView player) {
        switch (navigator.tick(player)) {
            case TRAVELING -> {
            }
            case ARRIVED -> {
                startVisit(clusters.get(current));
                enter(State.BUILDING);
            }
            // The next BUILDING tick picks the following cluster; a blacklisted one is skipped there.
            case FAILED -> {
                reportFailedTravel(clusters.get(current));
                enter(State.BUILDING);
            }
        }
    }

    private void reportFailedTravel(Cluster c) {
        BlockPos at = c.center();
        if (navigator.blacklisted(at)) {
            notes.accept(String.format("Cluster at %d %d %d cannot be reached (%d tries); skipping it.",
                at.getX(), at.getY(), at.getZ(), Navigator.MAX_ATTEMPTS));
            return;
        }
        notes.accept(String.format("Could not reach the cluster at %d %d %d; trying it again later.",
            at.getX(), at.getY(), at.getZ()));
        moveCurrentToEnd();
    }

    private void startVisit(Cluster c) {
        printer.startCluster(c);
        visitOpen = true;
    }

    /** Puts the current cluster last and steps back, so the next {@code nextCluster} picks the one after it. */
    private void moveCurrentToEnd() {
        clusters.add(clusters.remove(current));
        current--;
    }

    /** True if a PLACE task of the cluster is close enough for the printer to try it from here. */
    private boolean inReach(Cluster c, PlayerView player) {
        double reach = player.reach();
        return c.tasks().stream()
            .filter(t -> t.kind() == TaskKind.PLACE)
            .anyMatch(t -> player.eyePos().distanceTo(Vec3.atCenterOf(t.pos())) <= reach);
    }

    private void verify(WorldView world, PlayerView player) {
        int placedThisRound = placedInRound;
        // A resumed round skipped clusters, so placing nothing says nothing about what is left to do (P3-04).
        boolean skippedClusters = resumedRound;
        resumedRound = false;
        replan(world, player);
        if (remaining == 0 || (placedThisRound == 0 && !skippedClusters) || round >= MAX_ROUNDS) {
            activeMs += clockMs.getAsLong() - activeSince;
            enter(State.DONE);
            return;
        }
        round++;
        enter(State.BUILDING);
    }

    private void replan(WorldView world, PlayerView player) {
        clusters = new ArrayList<>(planner.plan(snap, world, BlockPos.containing(player.eyePos())));
        // A resumed run skips the clusters it already worked on; only the first plan of the run does this.
        current = Math.min(resumeFrom, clusters.size()) - 1;
        resumedRound = resumeFrom > 0;
        resumeFrom = 0;
        placedInRound = 0;
        List<BlockTask> tasks = clusters.stream().flatMap(c -> c.tasks().stream()).toList();
        remaining = (int) tasks.stream().filter(t -> t.kind() == TaskKind.PLACE).count();
        mismatched = (int) tasks.stream().filter(t -> t.skipReason() == SkipReason.MISMATCH_ADDITIVE_ONLY).count();
    }

    private boolean running() {
        return state != State.IDLE && state != State.PAUSED && state != State.DONE;
    }

    private void enter(State next) {
        State previous = state;
        state = next;
        onStateChange.accept(previous, next);
    }
}
