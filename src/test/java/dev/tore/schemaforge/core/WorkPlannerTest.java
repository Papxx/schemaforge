package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.WorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1-03 AK1–AK5 plus ordering rules, against a fake world. */
class WorkPlannerTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptyWorldYieldsAllTasksInExpectedClusterCount() {
        SchematicSnapshot snap = cube(3, Blocks.STONE.defaultBlockState());

        List<Cluster> single = planner(config(5, true)).plan(snap, new FakeWorld(), ORIGIN);
        assertEquals(1, single.size());
        assertEquals(27, taskCount(single));
        assertTrue(allTasks(single).stream().allMatch(t -> t.kind() == TaskKind.PLACE));

        // Edge 2 splits the 3×3×3 cube into 2×2×2 cells.
        List<Cluster> split = planner(config(2, true)).plan(snap, new FakeWorld(), ORIGIN);
        assertEquals(8, split.size());
        assertEquals(27, taskCount(split));
        for (int i = 0; i < split.size(); i++) assertEquals(i, split.get(i).index());
    }

    /** P5-04: straight rails first, the curve that connects them last - even when the curve is nearer. */
    @Test
    void curvedRailsComeAfterTheStraightOnes() {
        Map<BlockPos, BlockState> rails = new HashMap<>();
        rails.put(ORIGIN, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.SOUTH_EAST));
        rails.put(ORIGIN.east(), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        rails.put(ORIGIN.south(), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        FakeWorld world = new FakeWorld();
        for (BlockPos pos : rails.keySet()) world.set(pos.below(), Blocks.STONE.defaultBlockState());

        List<BlockTask> tasks = allTasks(planner(config(5, true)).plan(snapshot(rails), world, ORIGIN));
        assertEquals(ORIGIN, tasks.getLast().pos(), "the curve goes last");
        assertEquals(WorkPlanner.PRIORITY_RAIL_CURVE, tasks.getLast().priority());
        assertTrue(tasks.subList(0, 2).stream().allMatch(t -> t.priority() == WorkPlanner.PRIORITY_DEPENDENT));
    }

    @Test
    void correctBlocksInWorldProduceNoTasks() {
        SchematicSnapshot snap = cube(3, Blocks.STONE.defaultBlockState());
        FakeWorld world = new FakeWorld();
        int placed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(ORIGIN, ORIGIN.offset(2, 2, 2))) {
            if (placed == 10) break;
            world.set(pos, Blocks.STONE.defaultBlockState());
            placed++;
        }

        assertEquals(17, taskCount(planner(config(5, true)).plan(snap, world, ORIGIN)));
    }

    @Test
    void additiveOnlySkipsWrongBlockInsteadOfBreaking() {
        SchematicSnapshot snap = snapshot(Map.of(ORIGIN, Blocks.STONE.defaultBlockState()));
        FakeWorld world = new FakeWorld().set(ORIGIN, Blocks.DIRT.defaultBlockState());

        BlockTask task = single(planner(config(5, true)).plan(snap, world, ORIGIN));
        assertEquals(TaskKind.SKIP, task.kind());
        assertEquals(SkipReason.MISMATCH_ADDITIVE_ONLY, task.skipReason());

        BlockTask breaking = single(planner(config(5, false)).plan(snap, world, ORIGIN));
        assertEquals(TaskKind.BREAK, breaking.kind());
    }

    @Test
    void torchOverMissingFloorHasLowerPriorityThanFloor() {
        BlockPos torchPos = ORIGIN.above();
        SchematicSnapshot snap = snapshot(Map.of(
            ORIGIN, Blocks.STONE.defaultBlockState(),
            torchPos, Blocks.TORCH.defaultBlockState()));

        List<BlockTask> tasks = allTasks(planner(config(5, true)).plan(snap, new FakeWorld(), ORIGIN));
        BlockTask floor = tasks.stream().filter(t -> t.pos().equals(ORIGIN)).findFirst().orElseThrow();
        BlockTask torch = tasks.stream().filter(t -> t.pos().equals(torchPos)).findFirst().orElseThrow();
        assertTrue(torch.priority() < floor.priority(), "torch " + torch.priority() + " vs floor " + floor.priority());
        assertEquals(List.of(floor, torch), tasks);
    }

    @Test
    void substituteInWorldCountsAsCorrect() {
        SchematicSnapshot snap = snapshot(Map.of(ORIGIN, Blocks.STONE.defaultBlockState()));
        FakeWorld world = new FakeWorld().set(ORIGIN, Blocks.ANDESITE.defaultBlockState());
        PlanConfig cfg = config(5, true, Map.of(Blocks.STONE, List.of(Blocks.ANDESITE)), Set.of());

        assertEquals(List.of(), planner(cfg).plan(snap, world, ORIGIN));
    }

    @Test
    void ignoredPropertiesDoNotCauseMismatch() {
        BlockState target = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.WATERLOGGED, true);
        SchematicSnapshot snap = snapshot(Map.of(ORIGIN, target));
        FakeWorld world = new FakeWorld().set(ORIGIN, Blocks.OAK_STAIRS.defaultBlockState());

        assertEquals(1, taskCount(planner(config(5, true)).plan(snap, world, ORIGIN)));
        assertEquals(List.of(), planner(config(5, true, Map.of(), Set.of("waterlogged"))).plan(snap, world, ORIGIN));
    }

    @Test
    void airTargetIsIgnoredOnlyWithIgnoreAir() {
        SchematicSnapshot snap = snapshot(Map.of(ORIGIN, Blocks.AIR.defaultBlockState()));
        FakeWorld world = new FakeWorld().set(ORIGIN, Blocks.DIRT.defaultBlockState());

        assertEquals(List.of(), planner(config(5, true)).plan(snap, world, ORIGIN));
        PlanConfig keepAir = new PlanConfig(5, Direction.Axis.Y, true, false, false, Set.of(), Set.of(), Set.of(), Map.of(), Set.of());
        assertEquals(TaskKind.BREAK, single(planner(keepAir).plan(snap, world, ORIGIN)).kind());
    }

    @Test
    void filtersProduceSkipReasons() {
        BlockPos waterPos = ORIGIN.east();
        BlockPos unloadedPos = ORIGIN.west();
        SchematicSnapshot snap = snapshot(Map.of(
            ORIGIN, Blocks.TNT.defaultBlockState(),
            waterPos, Blocks.STONE.defaultBlockState(),
            unloadedPos, Blocks.STONE.defaultBlockState()));
        FakeWorld world = new FakeWorld().set(waterPos, Blocks.WATER.defaultBlockState()).unload(unloadedPos);
        PlanConfig cfg = new PlanConfig(5, Direction.Axis.Y, true, true, true,
            Set.of(Blocks.WATER), Set.of(), Set.of(Blocks.TNT), Map.of(), Set.of());

        Map<BlockPos, SkipReason> reasons = new HashMap<>();
        for (BlockTask t : allTasks(planner(cfg).plan(snap, world, ORIGIN))) reasons.put(t.pos(), t.skipReason());
        assertEquals(Map.of(
            ORIGIN, SkipReason.NEVER_PLACE,
            waterPos, SkipReason.WORLD_FILTER,
            unloadedPos, SkipReason.CHUNK_NOT_LOADED), reasons);
    }

    @Test
    void treatAsAirAndReplaceableBlocksArePlacedOver() {
        SchematicSnapshot snap = snapshot(Map.of(
            ORIGIN, Blocks.STONE.defaultBlockState(),
            ORIGIN.east(), Blocks.STONE.defaultBlockState()));
        FakeWorld world = new FakeWorld()
            .set(ORIGIN, Blocks.SHORT_GRASS.defaultBlockState())
            .set(ORIGIN.east(), Blocks.WATER.defaultBlockState());

        assertTrue(allTasks(planner(config(5, true)).plan(snap, world, ORIGIN)).stream().allMatch(t -> t.kind() == TaskKind.PLACE));
    }

    @Test
    void waterSourceBecomesFluidTask() {
        SchematicSnapshot snap = snapshot(Map.of(ORIGIN, Blocks.WATER.defaultBlockState()));
        BlockTask task = single(planner(config(5, true)).plan(snap, new FakeWorld(), ORIGIN));
        assertEquals(TaskKind.FLUID, task.kind());
        assertEquals(WorkPlanner.PRIORITY_FLUID, task.priority());
    }

    @Test
    void clustersFollowLayerAxisAndDirection() {
        SchematicSnapshot snap = snapshot(Map.of(
            ORIGIN, Blocks.STONE.defaultBlockState(),
            ORIGIN.above(4), Blocks.STONE.defaultBlockState()));

        List<Cluster> ascending = planner(config(2, true)).plan(snap, new FakeWorld(), ORIGIN.above(4));
        assertEquals(List.of(ORIGIN, ORIGIN.above(4)), ascending.stream().map(Cluster::center).toList());

        PlanConfig descending = new PlanConfig(2, Direction.Axis.Y, false, true, true, Set.of(), Set.of(), Set.of(), Map.of(), Set.of());
        List<Cluster> down = planner(descending).plan(snap, new FakeWorld(), ORIGIN);
        assertEquals(List.of(ORIGIN.above(4), ORIGIN), down.stream().map(Cluster::center).toList());
    }

    @Test
    void layerIsVisitedNearestNeighborFromStart() {
        BlockPos near = ORIGIN.east(10);
        BlockPos middle = ORIGIN.east(20);
        BlockPos far = ORIGIN.east(35);
        SchematicSnapshot snap = snapshot(Map.of(
            ORIGIN, Blocks.STONE.defaultBlockState(),
            near, Blocks.STONE.defaultBlockState(),
            middle, Blocks.STONE.defaultBlockState(),
            far, Blocks.STONE.defaultBlockState()));

        List<Cluster> clusters = planner(config(5, true)).plan(snap, new FakeWorld(), ORIGIN.east(19));
        assertEquals(List.of(middle, near, ORIGIN, far), clusters.stream().map(Cluster::center).toList());
    }

    @Test
    void topSlabWithoutCarrierIsDependent() {
        BlockState topSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
            net.minecraft.world.level.block.state.properties.SlabType.TOP);
        BlockTask floating = single(planner(config(5, true)).plan(snapshot(Map.of(ORIGIN, topSlab)), new FakeWorld(), ORIGIN));
        assertEquals(WorkPlanner.PRIORITY_DEPENDENT, floating.priority());

        FakeWorld wall = new FakeWorld().set(ORIGIN.north(), Blocks.STONE.defaultBlockState());
        BlockTask supported = single(planner(config(5, true)).plan(snapshot(Map.of(ORIGIN, topSlab)), wall, ORIGIN));
        assertEquals(WorkPlanner.PRIORITY_BLOCK, supported.priority());
    }

    private static WorkPlanner planner(PlanConfig cfg) {
        return new WorkPlanner(cfg);
    }

    private static PlanConfig config(int clusterSize, boolean additiveOnly) {
        return config(clusterSize, additiveOnly, Map.of(), Set.of());
    }

    private static PlanConfig config(int clusterSize, boolean additiveOnly, Map<Block, List<Block>> substitutes, Set<String> ignoreProperties) {
        return new PlanConfig(clusterSize, Direction.Axis.Y, true, additiveOnly, true,
            Set.of(), Set.of(Blocks.SHORT_GRASS), Set.of(), substitutes, ignoreProperties);
    }

    private static SchematicSnapshot cube(int edge, BlockState state) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(ORIGIN, ORIGIN.offset(edge - 1, edge - 1, edge - 1))) blocks.put(pos.immutable(), state);
        return snapshot(blocks);
    }

    private static SchematicSnapshot snapshot(Map<BlockPos, BlockState> blocks) {
        Long2ObjectMap<BlockState> map = new Long2ObjectOpenHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            map.put(p.asLong(), e.getValue());
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new SchematicSnapshot("test", new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), map,
            MaterialRules.totals(map.values()));
    }

    private static List<BlockTask> allTasks(List<Cluster> clusters) {
        return clusters.stream().flatMap(c -> c.tasks().stream()).toList();
    }

    private static int taskCount(List<Cluster> clusters) {
        return allTasks(clusters).size();
    }

    // --- P2-03: refresh ------------------------------------------------------------------------------

    @Test
    void refreshDropsPlacedPositionsAndKeepsOrderAndPriority() {
        SchematicSnapshot snap = cube(3, Blocks.STONE.defaultBlockState());
        FakeWorld world = new FakeWorld();
        WorkPlanner planner = planner(config(5, true));
        Cluster cluster = planner.plan(snap, world, ORIGIN).getFirst();
        BlockPos placed = cluster.tasks().get(4).pos();
        world.set(placed, Blocks.STONE.defaultBlockState());

        List<BlockTask> refreshed = planner.refresh(cluster, world);
        List<BlockTask> expected = cluster.tasks().stream().filter(t -> !t.pos().equals(placed)).toList();
        assertEquals(expected, refreshed);
    }

    @Test
    void refreshReadsCurrentStateAgain() {
        SchematicSnapshot snap = snapshot(Map.of(ORIGIN, Blocks.STONE.defaultBlockState()));
        FakeWorld world = new FakeWorld().unload(ORIGIN);
        WorkPlanner planner = planner(config(5, true));
        Cluster cluster = planner.plan(snap, world, ORIGIN).getFirst();
        assertEquals(SkipReason.CHUNK_NOT_LOADED, cluster.tasks().getFirst().skipReason());

        FakeWorld loaded = new FakeWorld();
        BlockTask now = planner.refresh(cluster, loaded).getFirst();
        assertEquals(TaskKind.PLACE, now.kind());
        assertEquals(WorkPlanner.PRIORITY_FULL_BLOCK, now.priority());

        loaded.set(ORIGIN, Blocks.DIRT.defaultBlockState());
        BlockTask mismatch = planner.refresh(cluster, loaded).getFirst();
        assertEquals(TaskKind.SKIP, mismatch.kind());
        assertEquals(SkipReason.MISMATCH_ADDITIVE_ONLY, mismatch.skipReason());
        assertEquals(Blocks.DIRT.defaultBlockState(), mismatch.current());
    }

    private static BlockTask single(List<Cluster> clusters) {
        List<BlockTask> tasks = allTasks(clusters);
        assertEquals(1, tasks.size(), "tasks: " + tasks);
        return tasks.getFirst();
    }

    /** Air everywhere unless set; every chunk loaded unless a position was unloaded. */
    private static final class FakeWorld implements WorldView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> unloaded = new java.util.HashSet<>();

        FakeWorld set(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
            return this;
        }

        FakeWorld unload(BlockPos pos) {
            unloaded.add(pos.immutable());
            return this;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isChunkLoaded(BlockPos pos) {
            return !unloaded.contains(pos);
        }

        @Override
        public Optional<ContainerType> containerAt(BlockPos pos) {
            return Optional.empty();
        }
    }
}
