package dev.tore.schemaforge.core;

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.PrintActions;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tick loop that places the blocks of one cluster within reach (P2-03, rules in ARCHITECTURE.md §4).
 * Walking to the cluster and the build state machine belong to SchemaPrinter (P2-07). Client thread only.
 */
public final class Printer {
    /** Looks at a task per cluster visit before it is given up (AK3). */
    public static final int MAX_ATTEMPTS = 3;

    private final PlacementSolver solver;
    private final WorkPlanner planner;
    private final MaterialManager materials;
    private final ActionBudget budget;
    private final PlacementLog log;
    private final Map<BlockPos, Integer> attempts = new HashMap<>();

    private Optional<Cluster> cluster = Optional.empty();
    /** Open tasks of the running pass; empty = start a new pass with a refresh. */
    private List<BlockTask> pass = List.of();
    private int cursor;
    private boolean done;
    private int placed;

    public Printer(PlacementSolver solver, WorkPlanner planner, MaterialManager materials, ActionBudget budget, PlacementLog log) {
        this.solver = solver;
        this.planner = planner;
        this.materials = materials;
        this.budget = budget;
        this.log = log;
    }

    /** Starts a new visit of {@code c}: attempts, pass and placed count start from zero; the material demand is recomputed. */
    public void startCluster(Cluster c) {
        cluster = Optional.of(c);
        materials.startCluster(c);
        attempts.clear();
        pass = List.of();
        cursor = 0;
        done = false;
        placed = 0;
    }

    /** Continues the current pass until the budget is used up or the pass ends (at most one pass per tick). */
    public void tick(WorldView world, PlayerView player, InventoryView inv, PrintActions actions) {
        if (cluster.isEmpty() || done) return;
        if (pass.isEmpty()) {
            pass = planner.refresh(cluster.get(), world).stream()
                .filter(t -> t.kind() == TaskKind.PLACE && attempts.getOrDefault(t.pos(), 0) < MAX_ATTEMPTS)
                .toList();
            cursor = 0;
            if (pass.isEmpty()) {
                done = true;
                return;
            }
        }

        while (cursor < pass.size()) {
            BlockTask task = pass.get(cursor);
            // Placed or blocked since the refresh; the next pass decides whether anything is left to do.
            if (!world.getBlockState(task.pos()).canBeReplaced()) {
                cursor++;
                continue;
            }
            Optional<PlacementPlan> plan = plan(task, world, player);
            // Missing items count as an attempt without a packet; the MaterialManager has fired the shortage event.
            Optional<MaterialManager.Selection> selection = plan
                .map(p -> materials.select(p.handItem(), inv))
                .filter(s -> !(s instanceof MaterialManager.Selection.Missing));
            if (selection.isPresent() && !budget.tryConsume()) return;

            attempts.merge(task.pos(), 1, Integer::sum);
            cursor++;
            if (selection.isEmpty()) continue;
            switch (selection.get()) {
                case MaterialManager.Selection.Ready(int slot) -> {
                    if (actions.place(plan.get(), slot)) {
                        placed++;
                        log.append(task.pos(), task.target(), false);
                    }
                }
                // Placed in the next pass, once the item is in the hotbar.
                case MaterialManager.Selection.Swap(int from, int to) -> actions.swapToHotbar(from, to);
                case MaterialManager.Selection.Missing _ -> {
                }
            }
        }
        pass = List.of();
    }

    /** True once no PLACE task of the cluster is left that still has attempts. */
    public boolean clusterDone() {
        return done;
    }

    /** Placements sent during this cluster visit. */
    public int placedCount() {
        return placed;
    }

    /** A plan the player can send from where they stand, or empty if the task cannot be placed right now. */
    private Optional<PlacementPlan> plan(BlockTask task, WorldView world, PlayerView player) {
        // proto is evaluated from P5-06 on.
        if (!(solver.solve(task, world, player, EasyPlaceProtocol.NONE) instanceof SolveResult.Ok(PlacementPlan plan))) {
            return Optional.empty();
        }
        return reachable(plan, player) ? Optional.of(plan) : Optional.empty();
    }

    /** Same checks as the solver's candidate rating; the solver returns an unusable plan when nothing better exists. */
    private boolean reachable(PlacementPlan plan, PlayerView player) {
        Vec3 eye = player.eyePos();
        if (eye.subtract(plan.hitVec()).dot(plan.clickFace().getUnitVec3()) <= 0) return false;
        if (eye.distanceTo(plan.hitVec()) > player.reach()) return false;
        return !solver.config().lineOfSight() || player.hasLineOfSight(plan.hitVec());
    }
}
