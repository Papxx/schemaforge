package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.PrintActions;
import dev.tore.schemaforge.core.view.WorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import java.util.OptionalInt;
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
        noItem.inventory.has = false;
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) noItem.tick();
        assertTrue(noItem.printer.clusterDone());
        assertTrue(noItem.actions.sent.isEmpty());

        // A torch has no rule until P2-04.
        Setup torch = new Setup(Map.of(new BlockPos(0, 64, 0), Blocks.TORCH.defaultBlockState()), 1);
        for (int i = 0; i <= Printer.MAX_ATTEMPTS; i++) torch.tick();
        assertTrue(torch.printer.clusterDone());
        assertTrue(torch.actions.sent.isEmpty());
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
        final FakeInventory inventory = new FakeInventory();
        final FakeActions actions = new FakeActions(world);
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
            printer = new Printer(new PlacementSolver(SolverConfig.defaults()), planner, budget, log);
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
        final List<Sent> sent = new ArrayList<>();
        boolean accept = true;

        FakeActions(FakeWorld world) {
            this.world = world;
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

    private static final class FakeInventory implements InventoryView {
        boolean has = true;

        @Override
        public int count(Item item) {
            return has ? 64 : 0;
        }

        @Override
        public OptionalInt hotbarSlotWith(Item item) {
            return has ? OptionalInt.of(SLOT) : OptionalInt.empty();
        }

        @Override
        public int freeSlots() {
            return 0;
        }

        @Override
        public List<ItemStack> shulkersContaining(Item item) {
            return List.of();
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
