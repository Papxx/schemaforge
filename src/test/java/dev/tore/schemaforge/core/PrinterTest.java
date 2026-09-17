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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
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

        // Rails have no rule until P5-04.
        Setup rail = new Setup(Map.of(new BlockPos(0, 64, 0), Blocks.RAIL.defaultBlockState()), 1);
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) rail.tick();
        assertTrue(rail.printer.clusterDone());
        assertTrue(rail.actions.sent.isEmpty());
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
        final Printer printer;
        final ActionBudget budget;
        FakePlayer player = NEAR;

        Setup(Map<BlockPos, BlockState> targets, int limit) {
            this(targets, new AtomicInteger(limit));
        }

        Setup(Map<BlockPos, BlockState> targets, AtomicInteger limit) {
            for (BlockPos pos : targets.keySet()) world.set(pos.below(), STONE);
            WorkPlanner planner = new WorkPlanner(new PlanConfig(16, Direction.Axis.Y, true, true, true,
                Set.of(), Set.of(), Set.of(), Map.of(), Set.of()));
            List<Cluster> clusters = planner.plan(snapshot(targets), world, BlockPos.ZERO);
            assertEquals(1, clusters.size());
            cluster = clusters.getFirst();
            budget = new ActionBudget(limit::get);
            MaterialManager materials = new MaterialManager(() -> HotbarSlots.parse("2-8"), shortages::add);
            printer = new Printer(new PlacementSolver(SolverConfig.defaults()), planner, materials, budget, log);
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
        boolean accept = true;

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
            if (accept) world.set(target(plan), STONE);
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
