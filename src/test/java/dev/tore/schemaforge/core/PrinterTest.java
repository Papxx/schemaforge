/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.PrintActions;
import dev.tore.schemaforge.core.view.WorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-03: tick loop against fakes. AK3 (max attempts per task per cluster visit) plus budget, log and order rules. */
class PrinterTest {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final int ROW = 5;
    private static final int SLOT = 4;
    /** Two blocks north of the middle of the row, eyes above it: every target of the row is within 4.5. */
    private static final FakePlayer NEAR = new FakePlayer(new Vec3(2.5, 65.62, -1.5), 4.5);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void placesOneBlockPerTickInTaskOrderAndLogsIt() {
        Setup s = new Setup(row(0), 1);
        List<BlockPos> order = s.cluster.tasks().stream().map(BlockTask::pos).toList();

        for (int tick = 1; tick <= ROW; tick++) {
            s.tick();
            assertEquals(tick, s.actions.sent.size(), "one placement per tick with limit 1");
        }
        assertEquals(order, s.actions.targets());
        assertEquals(order, s.log.entries().stream().map(PlacementLog.Entry::pos).toList());
        assertTrue(s.log.entries().stream().allMatch(e -> e.block() == STONE && !e.temp()));
        assertTrue(s.actions.sent.stream().allMatch(p -> p.slot() == SLOT));
        assertEquals(ROW, s.printer.placedCount());

        s.tick();
        assertTrue(s.printer.clusterDone());
    }

    @Test
    void higherLimitPlacesSeveralBlocksInOneTick() {
        Setup s = new Setup(row(0), 3);
        s.tick();
        assertEquals(3, s.actions.sent.size());
        s.tick();
        assertEquals(ROW, s.actions.sent.size());
    }

    @Test
    void emptyBudgetEndsTickWithoutCountingAttempts() {
        AtomicInteger limit = new AtomicInteger(0);
        Setup s = new Setup(row(0), limit);
        for (int i = 0; i < 10; i++) s.tick();
        assertTrue(s.actions.sent.isEmpty());
        assertFalse(s.printer.clusterDone());

        limit.set(ROW);
        s.tick();
        assertEquals(ROW, s.actions.sent.size());
    }

    /** AK3: out of reach → given up after MAX_ATTEMPTS passes, never sent. */
    @Test
    void outOfReachTaskIsGivenUpAfterThreeAttempts() {
        Setup s = new Setup(row(20), 1);
        for (int tick = 0; tick < Printer.MAX_ATTEMPTS; tick++) {
            s.tick();
            assertFalse(s.printer.clusterDone(), "attempt " + (tick + 1));
        }
        s.tick();
        assertTrue(s.printer.clusterDone());
        assertTrue(s.actions.sent.isEmpty());
    }

    /** AK3 for sent placements: a server that rejects every click is not asked more than MAX_ATTEMPTS times per block. */
    @Test
    void rejectedPlacementIsRetriedAtMostThreeTimes() {
        Setup s = new Setup(row(0), 10);
        s.actions.accept = false;
        for (int i = 0; i < 20; i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertEquals(ROW * Printer.MAX_ATTEMPTS, s.actions.sent.size());
    }

    @Test
    void newClusterVisitResetsAttempts() {
        Setup s = new Setup(row(20), 1);
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) s.tick();
        assertTrue(s.printer.clusterDone());

        s.printer.startCluster(s.cluster);
        s.player = new FakePlayer(new Vec3(22.5, 65.62, -1.5), 4.5);
        s.tick();
        assertEquals(1, s.actions.sent.size());
    }

    @Test
    void missingItemOrUnsupportedTargetIsNeverSent() {
        Setup noItem = new Setup(row(0), 1);
        noItem.inventory.clear();
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) noItem.tick();
        assertTrue(noItem.printer.clusterDone());
        assertTrue(noItem.actions.sent.isEmpty());

        // Redstone dust has no rule.
        Setup dust = new Setup(Map.of(new BlockPos(0, 64, 0), Blocks.REDSTONE_WIRE.defaultBlockState()), 1);
        dust.inventory.put(5, Items.REDSTONE, 64);
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) dust.tick();
        assertTrue(dust.printer.clusterDone());
        assertTrue(dust.actions.sent.isEmpty());
    }

    /** P2-05: item only in the main inventory → one swap into an allowed slot (budget unit), placed in the next pass. */
    @Test
    void itemOutsideAllowedHotbarIsSwappedInFirst() {
        Setup s = new Setup(row(0), 1);
        s.inventory.clear();
        s.inventory.put(0, Items.STONE, 10).put(20, Items.STONE, 64);   // slot 1 (index 0) is not allowed by "2-8"

        s.tick();
        assertEquals(1, s.actions.swaps.size());
        assertEquals(20, s.actions.swaps.getFirst()[0]);
        assertTrue(HotbarSlots.parse("2-8").allows(s.actions.swaps.getFirst()[1]));
        assertTrue(s.actions.sent.isEmpty(), "the swap used this tick's budget");

        for (int i = 0; i < 2 * ROW; i++) s.tick();
        assertEquals(ROW, s.actions.sent.size());
        assertEquals(1, s.actions.swaps.size());
        assertTrue(s.actions.sent.stream().allMatch(p -> HotbarSlots.parse("2-8").allows(p.slot())));
        assertTrue(s.printer.clusterDone());
    }

    @Test
    void missingItemFiresOneShortageEvent() {
        Setup s = new Setup(row(0), 1);
        s.inventory.clear();
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) s.tick();
        assertEquals(List.of(new MaterialManager.Shortage(Items.STONE, ROW)), s.shortages);
    }

    @Test
    void noLineOfSightIsNotSentWhenRequired() {
        Setup s = new Setup(row(0), 1);
        s.player = new FakePlayer(NEAR.eyePos(), 4.5, false);
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) s.tick();
        assertTrue(s.actions.sent.isEmpty());
    }

    @Test
    void blockPlacedByOthersMeanwhileIsSkippedWithoutClick() {
        Setup s = new Setup(row(0), 1);
        for (BlockTask task : s.cluster.tasks()) s.world.set(task.pos(), STONE);
        s.tick();
        s.tick();
        assertTrue(s.actions.sent.isEmpty());
        assertTrue(s.printer.clusterDone());
    }

    @Test
    void wrongBlockInWorldIsNeverTouched() {
        BlockPos foreign = new BlockPos(2, 64, 0);
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (boolean additiveOnly : new boolean[]{true, false}) {
            Setup s = new Setup(row(0), Map.of(foreign, dirt), new AtomicInteger(10), additiveOnly);
            BlockTask task = s.cluster.tasks().stream().filter(t -> t.pos().equals(foreign)).findFirst().orElseThrow();
            assertEquals(additiveOnly ? TaskKind.SKIP : TaskKind.BREAK, task.kind());
            if (additiveOnly) assertEquals(SkipReason.MISMATCH_ADDITIVE_ONLY, task.skipReason());

            for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) s.tick();
            assertEquals(ROW - 1, s.actions.sent.size());
            assertFalse(s.actions.targets().contains(foreign));
            assertEquals(dirt, s.world.getBlockState(foreign), "additiveOnly=" + additiveOnly);
            assertTrue(s.printer.clusterDone());
        }
    }

    @Test
    void placementLogWritesOneVersionedJsonLine() {
        String line = PlacementLog.toJson(new PlacementLog.Entry(new BlockPos(1, -2, 3),
            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), true, 42L));
        assertTrue(line.startsWith("{\"v\":1,"), line);
        assertTrue(line.contains("\"pos\":[1,-2,3]"), line);
        assertTrue(line.contains("\"block\":\"minecraft:oak_stairs[facing=east,"), line);
        assertTrue(line.contains("\"temp\":true"), line);
        assertTrue(line.endsWith("\"t\":42}"), line);
    }

    @Test
    void placementLogAppendsToFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/placementlog-test.jsonl");
        PlacementLog log = PlacementLog.toFile(file);
        log.append(new BlockPos(0, 64, 0), STONE, false);
        log.append(new BlockPos(1, 64, 0), STONE, false);
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("\"pos\":[1,64,0]"));
        assertTrue(log.writeError().isEmpty());
        assertEquals(2, log.entries().size());
    }

    // --- P5-02 temporary supports -------------------------------------------------------------------

    /** A block floating one above the floor: dirt goes under it first, the stone follows, the dirt goes again. */
    @Test
    void floatingBlockGetsATemporarySupportThatIsRemovedAtTheClusterEnd() {
        BlockPos target = new BlockPos(2, 65, 0);
        BlockPos support = target.below();
        List<BlockPos> broken = new ArrayList<>();
        Setup s = floating(target, broken, List.of(Blocks.DIRT));
        s.inventory.put(5, Items.DIRT, 16);

        for (int i = 0; i < 12 && !s.printer.clusterDone(); i++) s.tick();

        assertTrue(s.printer.clusterDone());
        assertEquals(List.of(support, target), s.actions.targets(), "support first, then the target");
        assertEquals(List.of(support, target), s.log.entries().stream().map(PlacementLog.Entry::pos).toList());
        assertTrue(s.log.entries().getFirst().temp(), "the support is logged as temp");
        assertFalse(s.log.entries().get(1).temp());
        assertEquals(List.of(support), broken.stream().distinct().toList(), "only the support is broken");
        assertTrue(s.world.getBlockState(support).isAir());
        assertEquals(STONE, s.world.getBlockState(target));
        assertEquals(1, s.printer.placedCount(), "supports do not count as placed blocks");
    }

    @Test
    void withoutSupportsAFloatingBlockIsNeverSentAndNothingIsBroken() {
        BlockPos target = new BlockPos(2, 65, 0);
        Setup s = new Setup(Map.of(target, STONE), Map.of(new BlockPos(2, 63, 0), STONE), new AtomicInteger(1), true,
            false, _ -> Printer.Options.defaults());
        s.inventory.put(5, Items.DIRT, 16);
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertTrue(s.actions.sent.isEmpty());
    }

    @Test
    void noSupportBlockInTheInventoryMeansNoSupport() {
        List<BlockPos> broken = new ArrayList<>();
        Setup s = floating(new BlockPos(2, 65, 0), broken, List.of(Blocks.DIRT));
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertTrue(s.actions.sent.isEmpty());
        assertTrue(broken.isEmpty());
    }

    /** A support someone replaced meanwhile is no longer ours and stays. */
    @Test
    void aSupportChangedByOthersIsLeftStanding() {
        BlockPos target = new BlockPos(2, 65, 0);
        BlockPos support = target.below();
        List<BlockPos> broken = new ArrayList<>();
        Setup s = floating(target, broken, List.of(Blocks.DIRT));
        s.inventory.put(5, Items.DIRT, 16);
        s.tick();
        s.tick();
        s.world.set(support, Blocks.COBBLESTONE.defaultBlockState());
        for (int i = 0; i < 10 && !s.printer.clusterDone(); i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertTrue(broken.isEmpty());
        assertEquals(Blocks.COBBLESTONE, s.world.getBlockState(support).getBlock());
    }

    /** Stone target floating above a floor two blocks down, additive-only off, supports from {@code whitelist}. */
    private static Setup floating(BlockPos target, List<BlockPos> broken, List<Block> whitelist) {
        return new Setup(Map.of(target, STONE), Map.of(target.below(2), STONE), new AtomicInteger(1), false, false,
            world -> new Printer.Options(Optional.of(new TempSupports(whitelist, _ -> false, pos -> {
                broken.add(pos);
                world.set(pos, Blocks.AIR.defaultBlockState());
                return true;
            }, _ -> {
            })), false, _ -> {
            }));
    }

    // --- P5-04 rails --------------------------------------------------------------------------------

    /** Verifier cross-check: vanilla made the rail north-south, the schematic wants east-west - reported once. */
    @Test
    void railThatConnectedDifferentlyIsReportedOnce() {
        BlockPos pos = new BlockPos(2, 64, 0);
        List<String> notes = new ArrayList<>();
        Setup s = new Setup(Map.of(pos, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST)),
            Map.of(), new AtomicInteger(1), true, true, _ -> new Printer.Options(Optional.empty(), false, notes::add));
        s.inventory.put(5, Items.RAIL, 16);
        for (int i = 0; i < 6 && !s.printer.clusterDone(); i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertEquals(1, s.actions.sent.size(), "additive-only: the wrong rail is not placed again");
        assertEquals(List.of("Rail at 2 64 0 connected as north_south, the schematic wants east_west."), notes);
    }

    @Test
    void railWithTheRightShapeIsNotReported() {
        List<String> notes = new ArrayList<>();
        Setup s = new Setup(Map.of(new BlockPos(2, 64, 0), Blocks.RAIL.defaultBlockState()),
            Map.of(), new AtomicInteger(1), true, true, _ -> new Printer.Options(Optional.empty(), false, notes::add));
        s.inventory.put(5, Items.RAIL, 16);
        for (int i = 0; i < 6 && !s.printer.clusterDone(); i++) s.tick();
        assertEquals(1, s.actions.sent.size());
        assertTrue(notes.isEmpty(), notes.toString());
    }

    // --- P5-03 fluids -------------------------------------------------------------------------------

    @Test
    void waterSourceIsPlacedWithABucketAndNotLogged() {
        BlockPos pos = new BlockPos(2, 64, 0);
        Setup s = fluids(Map.of(pos, Blocks.WATER.defaultBlockState()), true);
        s.inventory.put(5, Items.WATER_BUCKET, 1);
        for (int i = 0; i < 5 && !s.printer.clusterDone(); i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertEquals(1, s.actions.buckets.size());
        assertTrue(s.actions.sent.isEmpty(), "no block placement for a fluid");
        assertEquals(Items.WATER_BUCKET, s.actions.buckets.getFirst().plan().handItem());
        assertEquals(Blocks.WATER.defaultBlockState(), s.world.getBlockState(pos));
        assertEquals(1, s.printer.placedCount());
        assertTrue(s.log.entries().isEmpty(), "undo cannot take a fluid back");
    }

    @Test
    void fluidsAreLeftAloneWhenFluidHandlingIsOff() {
        Setup s = fluids(Map.of(new BlockPos(2, 64, 0), Blocks.WATER.defaultBlockState()), false);
        s.inventory.put(5, Items.WATER_BUCKET, 1);
        s.tick();
        assertTrue(s.printer.clusterDone());
        assertTrue(s.actions.buckets.isEmpty());
        assertFalse(s.printer.works(s.cluster.tasks().getFirst()));
    }

    /** Source check: flowing water where a source belongs does not finish the task; it is retried, then given up. */
    @Test
    void onlyASourceBlockCountsAsPlaced() {
        Setup s = fluids(Map.of(new BlockPos(2, 64, 0), Blocks.WATER.defaultBlockState()), true);
        s.inventory.put(5, Items.WATER_BUCKET, 1);
        s.actions.flowingOnly = true;
        for (int i = 0; i < 10 && !s.printer.clusterDone(); i++) s.tick();
        assertTrue(s.printer.clusterDone());
        assertEquals(Printer.MAX_ATTEMPTS, s.actions.buckets.size());
    }

    /** Blocks before fluids: the stone of the cluster goes first, the water last. */
    @Test
    void fluidsComeLastInTheCluster() {
        BlockPos water = new BlockPos(1, 64, 0);
        BlockPos stone = new BlockPos(3, 64, 0);
        Setup s = fluids(Map.of(water, Blocks.WATER.defaultBlockState(), stone, STONE), true);
        s.inventory.put(5, Items.WATER_BUCKET, 1);
        s.tick();
        assertEquals(List.of(stone), s.actions.targets());
        assertTrue(s.actions.buckets.isEmpty());
        for (int i = 0; i < 6 && !s.printer.clusterDone(); i++) s.tick();
        assertEquals(1, s.actions.buckets.size());
        PlacementPlan bucket = s.actions.buckets.getFirst().plan();
        assertEquals(water, bucket.clickPos().relative(bucket.clickFace()));
    }

    private static Setup fluids(Map<BlockPos, BlockState> targets, boolean handleFluids) {
        return new Setup(targets, Map.of(), new AtomicInteger(1), true, true,
            _ -> new Printer.Options(Optional.empty(), handleFluids, _ -> {
            }));
    }

    // --- helpers ------------------------------------------------------------------------------------

    /** A row of stone targets at y=64 starting at x, over a stone floor at y=63. */
    private static Map<BlockPos, BlockState> row(int x) {
        Map<BlockPos, BlockState> targets = new HashMap<>();
        for (int i = 0; i < ROW; i++) targets.put(new BlockPos(x + i, 64, 0), STONE);
        return targets;
    }

    private static final class Setup {
        final FakeWorld world = new FakeWorld();
        final FakeInventory inventory = new FakeInventory().put(SLOT, Items.STONE, 64);
        final FakeActions actions = new FakeActions(world, inventory);
        final List<MaterialManager.Shortage> shortages = new ArrayList<>();
        final PlacementLog log = PlacementLog.inMemory();
        final Cluster cluster;
        final SchematicSnapshot snap;
        final Printer printer;
        final ActionBudget budget;
        FakePlayer player = NEAR;

        Setup(Map<BlockPos, BlockState> targets, int limit) {
            this(targets, new AtomicInteger(limit));
        }

        Setup(Map<BlockPos, BlockState> targets, AtomicInteger limit) {
            this(targets, Map.of(), limit, true);
        }

        /** {@code existing} is set after the floor, before planning. */
        Setup(Map<BlockPos, BlockState> targets, Map<BlockPos, BlockState> existing, AtomicInteger limit, boolean additiveOnly) {
            this(targets, existing, limit, additiveOnly, true, _ -> Printer.Options.defaults());
        }

        /** @param floor stone under every target; without it only {@code existing} stands */
        Setup(Map<BlockPos, BlockState> targets, Map<BlockPos, BlockState> existing, AtomicInteger limit, boolean additiveOnly,
              boolean floor, java.util.function.Function<FakeWorld, Printer.Options> options) {
            if (floor) for (BlockPos pos : targets.keySet()) world.set(pos.below(), STONE);
            existing.forEach(world::set);
            WorkPlanner planner = new WorkPlanner(new PlanConfig(16, Direction.Axis.Y, true, additiveOnly, true,
                Set.of(), Set.of(), Set.of(), Map.of(), Set.of()));
            snap = snapshot(targets);
            List<Cluster> clusters = planner.plan(snap, world, BlockPos.ZERO);
            assertEquals(1, clusters.size());
            cluster = clusters.getFirst();
            budget = new ActionBudget(limit::get);
            MaterialManager materials = new MaterialManager(() -> HotbarSlots.parse("2-8"), shortages::add);
            printer = new Printer(new PlacementSolver(SolverConfig.defaults()), planner, materials, budget, log,
                options.apply(world));
            printer.startCluster(cluster);
        }

        void tick() {
            budget.resetTick();
            printer.tick(world, player, inventory, actions);
        }
    }

    private static SchematicSnapshot snapshot(Map<BlockPos, BlockState> blocks) {
        Long2ObjectMap<BlockState> map = new Long2ObjectOpenHashMap<>();
        blocks.forEach((pos, state) -> map.put(pos.asLong(), state));
        BlockPos min = blocks.keySet().stream().reduce((a, b) -> new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()))).orElseThrow();
        BlockPos max = blocks.keySet().stream().reduce((a, b) -> new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))).orElseThrow();
        return new SchematicSnapshot("test", min, max, map, MaterialRules.totals(map.values()));
    }

    private record Sent(PlacementPlan plan, int slot) {
    }

    /** Records every placement; if {@code accept}, the clicked block appears in the world like a server-confirmed placement. */
    private static final class FakeActions implements PrintActions {
        final FakeWorld world;
        final FakeInventory inventory;
        final List<Sent> sent = new ArrayList<>();
        final List<int[]> swaps = new ArrayList<>();
        final List<Sent> buckets = new ArrayList<>();
        boolean accept = true;
        /** The bucket leaves flowing water instead of a source. */
        boolean flowingOnly;

        FakeActions(FakeWorld world, FakeInventory inventory) {
            this.world = world;
            this.inventory = inventory;
        }

        @Override
        public boolean swapToHotbar(int inventorySlot, int hotbarSlot) {
            swaps.add(new int[]{inventorySlot, hotbarSlot});
            inventory.swap(inventorySlot, hotbarSlot);
            return true;
        }

        @Override
        public boolean place(PlacementPlan plan, int hotbarSlot) {
            sent.add(new Sent(plan, hotbarSlot));
            if (accept) world.set(target(plan), Block.byItem(plan.handItem()).defaultBlockState());
            return true;
        }

        @Override
        public boolean useBucket(PlacementPlan plan, int hotbarSlot) {
            buckets.add(new Sent(plan, hotbarSlot));
            BlockState fluid = plan.handItem() == Items.LAVA_BUCKET ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
            if (flowingOnly) fluid = fluid.setValue(LiquidBlock.LEVEL, 2);
            if (accept) world.set(target(plan), fluid);
            return true;
        }

        List<BlockPos> targets() {
            return sent.stream().map(s -> target(s.plan())).toList();
        }

        private static BlockPos target(PlacementPlan plan) {
            return plan.clickPos().relative(plan.clickFace());
        }
    }

    private record FakePlayer(Vec3 eyePos, double reach, boolean sight) implements PlayerView {
        FakePlayer(Vec3 eyePos, double reach) {
            this(eyePos, reach, true);
        }

        @Override
        public float yaw() {
            return 0;
        }

        @Override
        public float pitch() {
            return 0;
        }

        @Override
        public boolean hasLineOfSight(Vec3 target) {
            return sight;
        }
    }

    private static final class FakeWorld implements WorldView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        FakeWorld set(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
            return this;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isChunkLoaded(BlockPos pos) {
            return true;
        }

        @Override
        public Optional<ContainerType> containerAt(BlockPos pos) {
            return Optional.empty();
        }
    }
}
