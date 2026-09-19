package dev.tore.schemaforge.core;

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.PrintActions;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tick loop that places the blocks of one cluster within reach (P2-03, rules in ARCHITECTURE.md §4).
 * Walking to the cluster and the build state machine belong to SchemaPrinter (P2-07). Client thread only.
 */
public final class Printer {
    /** Looks at a task per cluster visit before it is given up (AK3). */
    public static final int MAX_ATTEMPTS = 3;

    /**
     * Optional behaviour on top of plain printing.
     *
     * @param supports     temporary support blocks (P5-02); empty with additive-only on
     * @param handleFluids place water and lava sources with a bucket (P5-03)
     * @param notes        user-facing one-liners
     */
    public record Options(Optional<TempSupports> supports, boolean handleFluids, Consumer<String> notes) {
        public static Options defaults() {
            return new Options(Optional.empty(), false, _ -> {
            });
        }
    }

    private final PlacementSolver solver;
    private final WorkPlanner planner;
    private final MaterialManager materials;
    private final ActionBudget budget;
    private final PlacementLog log;
    private final Options options;
    private final Map<BlockPos, Integer> attempts = new HashMap<>();

    private Optional<Cluster> cluster = Optional.empty();
    /** Open tasks of the running pass; empty = start a new pass with a refresh. */
    private List<BlockTask> pass = List.of();
    private int cursor;
    private boolean done;
    private int placed;

    public Printer(PlacementSolver solver, WorkPlanner planner, MaterialManager materials, ActionBudget budget, PlacementLog log) {
        this(solver, planner, materials, budget, log, Options.defaults());
    }

    public Printer(PlacementSolver solver, WorkPlanner planner, MaterialManager materials, ActionBudget budget,
                   PlacementLog log, Options options) {
        this.solver = solver;
        this.planner = planner;
        this.materials = materials;
        this.budget = budget;
        this.log = log;
        this.options = options;
    }

    /** Starts a new visit of {@code c}: attempts, pass and placed count start from zero; the material demand is recomputed. */
    public void startCluster(Cluster c) {
        cluster = Optional.of(c);
        materials.startCluster(c);
        options.supports().ifPresent(TempSupports::reset);
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
                .filter(t -> works(t) && attempts.getOrDefault(t.pos(), 0) < MAX_ATTEMPTS)
                .toList();
            cursor = 0;
            if (pass.isEmpty()) {
                // The cluster is finished once its temporary supports are gone again (P5-02).
                Optional<TempSupports> supports = options.supports().filter(TempSupports::pending);
                if (supports.isPresent()) {
                    supports.get().tickClearing(world, player, budget);
                    return;
                }
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
            Optional<Step> step = step(task, world, player, inv);
            // Missing items count as an attempt without a packet; the MaterialManager has fired the shortage event.
            Optional<MaterialManager.Selection> selection = step
                .map(s -> materials.select(s.plan().handItem(), inv))
                .filter(s -> !(s instanceof MaterialManager.Selection.Missing));
            if (selection.isPresent() && !budget.tryConsume()) return;

            attempts.merge(task.pos(), 1, Integer::sum);
            cursor++;
            if (selection.isEmpty()) continue;
            switch (selection.get()) {
                case MaterialManager.Selection.Ready(int slot) -> {
                    Step s = step.get();
                    if (s.fluid() ? actions.useBucket(s.plan(), slot) : actions.place(s.plan(), slot)) sent(s);
                }
                // Placed in the next pass, once the item is in the hotbar.
                case MaterialManager.Selection.Swap(int from, int to) -> actions.swapToHotbar(from, to);
                case MaterialManager.Selection.Missing _ -> {
                }
            }
        }
        pass = List.of();
    }

    /**
     * Tasks this printer works on: PLACE, and FLUID with fluid handling on (P5-03). The build session uses it to pick
     * clusters and to count what is left.
     */
    public boolean works(BlockTask task) {
        return task.kind() == TaskKind.PLACE || (task.kind() == TaskKind.FLUID && options.handleFluids());
    }

    /** True once no task of the cluster is left that still has attempts and its temporary supports are gone. */
    public boolean clusterDone() {
        return done;
    }

    /** Placements sent during this cluster visit; temporary supports do not count. */
    public int placedCount() {
        return placed;
    }

    /**
     * True if the current cluster needs items and the inventory holds none of them (P3-03).
     * The demand is the one from the start of the visit, so it also counts blocks already placed - good enough to
     * tell "nothing left to work with" from "one item type is missing".
     */
    public boolean outOfMaterials(InventoryView inv) {
        Map<Item, Integer> demand = materials.demand();
        return !demand.isEmpty() && demand.keySet().stream().allMatch(item -> inv.count(item) == 0);
    }

    /**
     * What to send for a task: its own placement, or a temporary support first if the target has nothing to be
     * clicked against (P5-02).
     *
     * @param pos   where the block appears
     * @param block the block that appears there
     * @param temp  true for a temporary support
     * @param fluid true for a bucket use (P5-03)
     */
    private record Step(PlacementPlan plan, BlockPos pos, BlockState block, boolean temp, boolean fluid) {
    }

    private void sent(Step step) {
        // Fluids stay out of the log: undo mines blocks and cannot take a source back (P5-03).
        if (!step.fluid()) log.append(step.pos(), step.block(), step.temp());
        if (step.temp()) {
            options.supports().ifPresent(s -> s.placed(step.pos(), step.block()));
        } else {
            placed++;
        }
    }

    /** A step the player can send from where they stand, or empty if the task cannot be worked on right now. */
    private Optional<Step> step(BlockTask task, WorldView world, PlayerView player, InventoryView inv) {
        boolean fluid = task.kind() == TaskKind.FLUID;
        // proto is evaluated from P5-06 on.
        SolveResult result = fluid ? solver.solveFluid(task, world, player) : solver.solve(task, world, player, EasyPlaceProtocol.NONE);
        if (result instanceof SolveResult.Ok(PlacementPlan plan)) {
            return reachable(plan, player) ? Optional.of(new Step(plan, task.pos(), task.target(), false, fluid)) : Optional.empty();
        }
        if (fluid) return Optional.empty();
        if (result instanceof SolveResult.NeedsSupport(BlockPos at)) return support(task, at, world, player, inv);
        return Optional.empty();
    }

    /** A temporary support at {@code at}, placed like a full block; never a support for the support. */
    private Optional<Step> support(BlockTask task, BlockPos at, WorldView world, PlayerView player, InventoryView inv) {
        Optional<TempSupports> supports = options.supports();
        if (supports.isEmpty() || !supports.get().allowedFor(task.target(), at, world)) return Optional.empty();
        Optional<Block> block = supports.get().pick(inv);
        if (block.isEmpty()) return Optional.empty();
        BlockState state = block.get().defaultBlockState();
        BlockTask supportTask = new BlockTask(at, state, world.getBlockState(at), TaskKind.PLACE,
            WorkPlanner.PRIORITY_FULL_BLOCK, SkipReason.NONE);
        if (!(solver.solve(supportTask, world, player, EasyPlaceProtocol.NONE) instanceof SolveResult.Ok(PlacementPlan plan))) {
            return Optional.empty();
        }
        return reachable(plan, player) ? Optional.of(new Step(plan, at, state, true, false)) : Optional.empty();
    }

    /** Same checks as the solver's candidate rating; the solver returns an unusable plan when nothing better exists. */
    private boolean reachable(PlacementPlan plan, PlayerView player) {
        Vec3 eye = player.eyePos();
        if (eye.subtract(plan.hitVec()).dot(plan.clickFace().getUnitVec3()) <= 0) return false;
        if (eye.distanceTo(plan.hitVec()) > player.reach()) return false;
        return !solver.config().lineOfSight() || player.hasLineOfSight(plan.hitVec());
    }
}
