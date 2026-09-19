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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-07: the state machine of one run against fakes – order of states, cluster progress, pause/resume and status. */
class BuildSessionTest {
    /** Assigned in {@link #bootstrapRegistries()}: touching Blocks before the bootstrap breaks the registries for the whole JVM. */
    private static BlockState stone;
    /** Two rows of five that fall into two clusters of edge length 5. */
    private static final int ROW = 5;
    /** Far enough from the player that the printer cannot reach that row (P3-02). */
    private static final int FAR_ROW_Z = 40;
    /** Eyes between the two rows; every target of both rows is within 4.5. */
    private static final Vec3 BETWEEN_ROWS = new Vec3(2.5, 65.62, 3.0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        stone = Blocks.STONE.defaultBlockState();
    }

    @Test
    void runsThroughPlanningBuildingVerifyingToDone() {
        Setup s = new Setup(rows(1));

        assertEquals(BuildSession.State.IDLE, s.session.state());
        s.session.start();
        assertEquals(BuildSession.State.PLANNING, s.session.state());
        s.tick();
        assertEquals(BuildSession.State.BUILDING, s.session.state());

        s.tickUntilDone();
        assertEquals(BuildSession.State.DONE, s.session.state());
        assertEquals(ROW, s.actions.placed.size(), "every target placed once");
        assertEquals(List.of(BuildSession.State.PLANNING, BuildSession.State.BUILDING,
            BuildSession.State.VERIFYING, BuildSession.State.DONE), s.states);

        BuildSession.Status status = s.session.status();
        assertEquals(ROW, status.placed());
        assertEquals(0, status.remaining());
        assertEquals(0, status.mismatched());
        assertEquals(1, status.round(), "one round is enough when nothing is left");
    }

    @Test
    void worksThroughEveryClusterInOrder() {
        Setup s = new Setup(rows(2));
        s.session.start();
        s.tick();

        assertEquals(2, s.session.status().clusterCount());
        assertEquals(0, s.session.status().clusterIndex(), "no cluster started before the first building tick");
        s.tick();
        assertEquals(1, s.session.status().clusterIndex());
        s.tickUntilDone();

        assertEquals(2 * ROW, s.actions.placed.size());
        assertEquals(2 * ROW, s.session.status().placed());
        // Every target of both rows was placed, none twice.
        assertEquals(s.targets.keySet(), Set.copyOf(s.actions.placed));
    }

    @Test
    void aSecondRoundPlacesWhatTheFirstOneMissed() {
        Setup s = new Setup(rows(1));
        // The server swallows the whole first round, including the printer's retries per task (P2-03 AK3).
        s.actions.rejectFirst = ROW * Printer.MAX_ATTEMPTS;
        s.session.start();
        s.tick();
        s.tickUntilDone();

        assertEquals(2, s.session.status().round());
        assertEquals(ROW * Printer.MAX_ATTEMPTS + ROW, s.actions.placed.size(), "three tries each, then one that lands");
        assertEquals(0, s.session.status().remaining());
        // placed counts what was sent, not what the server kept (same caveat as the placement log, P2-03).
        assertEquals(s.actions.placed.size(), s.session.status().placed());
    }

    @Test
    void givesUpAfterMaxRoundsWhenPlacementsNeverLand() {
        Setup s = new Setup(rows(1));
        s.actions.accept = false;
        s.session.start();
        s.tick();
        s.tickUntilDone();

        assertEquals(BuildSession.State.DONE, s.session.state());
        assertEquals(BuildSession.MAX_ROUNDS, s.session.status().round());
        assertEquals(ROW, s.session.status().remaining(), "reported as not placed");
        assertEquals(BuildSession.MAX_ROUNDS * ROW * Printer.MAX_ATTEMPTS, s.actions.placed.size());
    }

    @Test
    void mismatchedBlocksAreCountedAndLeftAlone() {
        Map<BlockPos, BlockState> targets = rows(1);
        BlockPos foreign = new BlockPos(2, 64, 0);
        Setup s = new Setup(targets, Map.of(foreign, Blocks.DIRT.defaultBlockState()));
        s.session.start();
        s.tick();
        s.tickUntilDone();

        assertEquals(1, s.session.status().mismatched());
        assertEquals(ROW - 1, s.actions.placed.size());
        assertFalse(s.actions.placed.contains(foreign));
    }

    @Test
    void pauseStopsWorkAndResumeContinues() {
        Setup s = new Setup(rows(1));
        s.session.start();
        s.tick();
        s.tick();
        int afterFirstTick = s.actions.placed.size();
        assertTrue(afterFirstTick > 0);

        assertTrue(s.session.pause());
        assertEquals(BuildSession.State.PAUSED, s.session.state());
        s.tick();
        s.tick();
        assertEquals(afterFirstTick, s.actions.placed.size(), "a paused build sends nothing");
        assertFalse(s.session.pause(), "already paused");

        assertTrue(s.session.resume());
        assertEquals(BuildSession.State.BUILDING, s.session.state());
        assertFalse(s.session.resume(), "not paused any more");
        s.tickUntilDone();
        assertEquals(ROW, s.actions.placed.size());
    }

    @Test
    void blocksPerMinuteUsesActiveTimeOnly() {
        Setup s = new Setup(rows(1));
        s.session.start();
        s.tick();
        while (s.actions.placed.size() < ROW) {
            s.clock.addAndGet(1000);
            s.tick();
        }
        // 5 blocks in 5 seconds of building, then a minute of pause.
        s.session.pause();
        s.clock.addAndGet(60_000);

        assertEquals(60.0, s.session.status().blocksPerMinute(), 1.0);
    }

    @Test
    void statusBeforeTheFirstSecondReportsNoRate() {
        Setup s = new Setup(rows(1));
        s.session.start();
        s.tick();
        assertEquals(0.0, s.session.status().blocksPerMinute());
    }

    @Test
    void clusterOutOfReachIsWalkedToFirst() {
        Setup s = new Setup(nearAndFarRow());
        s.msPerTick = 100;
        s.session.start();
        s.tick();

        // The near row is built from where we stand, then the far one is walked to.
        s.tickUntil(BuildSession.State.TRAVELING, 60);
        assertEquals(ROW, s.actions.placed.size(), "near row built before travelling");
        assertEquals(List.of(new BlockPos(2, 64, FAR_ROW_Z)), s.pathing.goals, "walks to the far cluster centre");
        assertEquals(Navigator.GOAL_RADIUS, s.pathing.lastRadius);

        s.tick();
        assertEquals(BuildSession.State.TRAVELING, s.session.state(), "still walking while far away");
        s.player.eyePos = new Vec3(2.5, 65.62, FAR_ROW_Z - 3.0);
        s.tick();

        assertEquals(BuildSession.State.BUILDING, s.session.state(), "arriving hands over to the printer");
        s.tickUntilDone();
        assertEquals(2 * ROW, s.actions.placed.size(), "both rows built");
        assertEquals(List.of(), s.notes);
    }

    @Test
    void unreachableClusterIsBlacklistedAfterThreeTriesAndTheBuildGoesOn() {
        Setup s = new Setup(nearAndFarRow());
        s.msPerTick = 100;
        // The pathfinder finds no path to the walled-in cluster and never starts walking.
        s.pathing.pathFound = false;
        s.session.start();
        s.tickUntilDone();

        BlockPos farCentre = new BlockPos(2, 64, FAR_ROW_Z);
        assertEquals(Navigator.MAX_ATTEMPTS, s.pathing.goals.size(), "no cluster is approached more than three times");
        assertTrue(s.navigator.blacklisted(farCentre));
        assertEquals(ROW, s.actions.placed.size(), "the reachable row is still built");
        assertEquals(Navigator.MAX_ATTEMPTS, s.notes.size(), "one message per failed try");
        assertTrue(s.notes.getLast().contains("cannot be reached"), s.notes.getLast());
        assertEquals(ROW, s.session.status().remaining(), "the unreachable row stays open");
    }

    @Test
    void withoutAPathfinderEveryClusterIsTriedFromWhereWeStand() {
        Setup s = new Setup(nearAndFarRow());
        s.pathing.present = false;
        s.session.start();
        s.tickUntilDone();

        assertFalse(s.states.contains(BuildSession.State.TRAVELING), "nothing to walk with");
        assertEquals(List.of(), s.pathing.goals);
        assertEquals(ROW, s.actions.placed.size(), "only what is within reach");
    }

    @Test
    void pausingWhileTravellingHandsThePathBackAndResumesTheSameCluster() {
        Setup s = new Setup(nearAndFarRow());
        s.msPerTick = 100;
        s.session.start();
        s.tick();
        s.tickUntil(BuildSession.State.TRAVELING, 60);

        assertTrue(s.session.pause());
        assertEquals(1, s.pathing.stops, "the pathfinder is free while paused");
        s.tick();
        assertEquals(BuildSession.State.PAUSED, s.session.state(), "a paused session does not walk");

        assertTrue(s.session.resume());
        s.tick();
        assertEquals(BuildSession.State.TRAVELING, s.session.state());
        assertEquals(2, s.pathing.goals.size(), "the same cluster is walked to again");
        assertEquals(new BlockPos(2, 64, FAR_ROW_Z), s.pathing.goals.getLast());
        assertEquals(0, s.navigator.attempts(s.pathing.goals.getLast()), "a pause is not a failed try");
    }

    @Test
    void damagePausesTheBuildUntilResumedByHand() {
        Setup s = new Setup(rows(1));
        s.session.start();
        s.tick();
        s.tick();
        int placedBeforeDamage = s.actions.placed.size();

        s.safety.health = 12;
        s.tick();
        assertEquals(BuildSession.State.PAUSED, s.session.state());
        assertEquals(SafetyMonitor.Reason.DAMAGE, s.session.safetyPause().orElseThrow().reason());
        assertTrue(s.notes.getLast().contains(".sf resume"), s.notes.getLast());

        for (int i = 0; i < 10; i++) s.tick();
        assertEquals(BuildSession.State.PAUSED, s.session.state(), "damage never continues on its own");
        assertEquals(placedBeforeDamage, s.actions.placed.size(), "nothing is placed while paused");

        assertTrue(s.session.resume());
        assertTrue(s.session.safetyPause().isEmpty());
        s.tickUntilDone();
        assertEquals(ROW, s.actions.placed.size());
    }

    @Test
    void aPlayerNearbyPausesAndTheBuildContinuesOnItsOwn() {
        Setup s = new Setup(rows(1));
        s.session.start();
        s.tick();

        s.safety.nearest = java.util.OptionalDouble.of(5);
        s.tick();
        assertEquals(BuildSession.State.PAUSED, s.session.state());
        assertEquals(SafetyMonitor.Reason.PLAYER_NEARBY, s.session.safetyPause().orElseThrow().reason());

        s.safety.nearest = java.util.OptionalDouble.of(40);
        s.tick();
        assertEquals(BuildSession.State.BUILDING, s.session.state(), "continues once they left");
        assertTrue(s.session.safetyPause().isEmpty());
        assertTrue(s.notes.getLast().contains("PLAYER_NEARBY"), s.notes.getLast());
        s.tickUntilDone();
        assertEquals(ROW, s.actions.placed.size());
    }

    @Test
    void anEmptyInventoryPausesUntilItemsAreBack() {
        Setup s = new Setup(rows(1));
        s.inventory.put(3, Items.AIR, 0);
        s.session.start();
        // The demand is known once the first cluster starts, so the stop fires on the tick after that.
        s.tickUntil(BuildSession.State.PAUSED, 5);

        assertEquals(SafetyMonitor.Reason.NO_MATERIALS, s.session.safetyPause().orElseThrow().reason());
        assertEquals(List.of(), s.actions.placed);

        s.inventory.put(3, Items.STONE, 64);
        s.tick();
        assertEquals(BuildSession.State.BUILDING, s.session.state());
        s.tickUntilDone();
        assertEquals(ROW, s.actions.placed.size());
    }

    @Test
    void aResumedRunStartsAtTheGivenCluster() {
        Setup s = new Setup(rows(2));
        s.session.start(1);
        s.tick();
        s.tick();

        // Cluster 1 is skipped; the first building tick goes straight to cluster 2.
        assertEquals(2, s.session.status().clusterIndex());
        s.tickUntilDone();

        // The skipped row is still built: the verifying round plans it again from scratch.
        assertEquals(2 * ROW, s.actions.placed.size());
    }

    @Test
    void resumingBeyondTheLastClusterFallsBackToVerifying() {
        Setup s = new Setup(rows(1));
        s.session.start(99);
        s.tick();
        s.tick();

        assertEquals(List.of(), s.actions.placed, "nothing to do in the first round");
        s.tickUntilDone();
        assertEquals(ROW, s.actions.placed.size(), "the second round catches the whole row");
    }

    // --- helpers ------------------------------------------------------------------------------------

    /** One row within reach and one 40 blocks away, so the second cluster has to be walked to. */
    private static Map<BlockPos, BlockState> nearAndFarRow() {
        Map<BlockPos, BlockState> targets = new HashMap<>(rows(1));
        for (int i = 0; i < ROW; i++) targets.put(new BlockPos(i, 64, FAR_ROW_Z), stone);
        return targets;
    }

    /** {@code rows} rows of five stone targets at y=64 over a stone floor, 6 blocks apart so each is its own cluster. */
    private static Map<BlockPos, BlockState> rows(int rows) {
        Map<BlockPos, BlockState> targets = new HashMap<>();
        for (int row = 0; row < rows; row++) {
            for (int i = 0; i < ROW; i++) targets.put(new BlockPos(i, 64, row * 6), stone);
        }
        return targets;
    }

    private static final class Setup {
        final Map<BlockPos, BlockState> targets;
        final FakeWorld world = new FakeWorld();
        final FakeInventory inventory = new FakeInventory().put(3, Items.STONE, 64);
        final FakeActions actions = new FakeActions(world);
        final List<BuildSession.State> states = new ArrayList<>();
        final AtomicLong clock = new AtomicLong(1_000_000);
        final ActionBudget budget = new ActionBudget(() -> 1);
        final NavigatorTest.FakePathing pathing = new NavigatorTest.FakePathing();
        final FakePlayer player = new FakePlayer(BETWEEN_ROWS);
        final List<String> notes = new ArrayList<>();
        final Navigator navigator;
        final SafetyMonitorTest.FakeSafety safety = new SafetyMonitorTest.FakeSafety();
        SafetyMonitor.Config safetyConfig = SafetyMonitor.Config.defaults();
        final BuildSession session;

        Setup(Map<BlockPos, BlockState> targets) {
            this(targets, Map.of());
        }

        Setup(Map<BlockPos, BlockState> targets, Map<BlockPos, BlockState> existing) {
            this.targets = targets;
            for (BlockPos pos : targets.keySet()) world.set(pos.below(), stone);
            existing.forEach(world::set);
            WorkPlanner planner = new WorkPlanner(new PlanConfig(5, Direction.Axis.Y, true, true, true,
                Set.of(), Set.of(), Set.of(), Map.of(), Set.of()));
            MaterialManager materials = new MaterialManager(() -> HotbarSlots.parse("1-9"), _ -> {
            });
            Printer printer = new Printer(new PlacementSolver(SolverConfig.defaults()), planner, materials, budget,
                PlacementLog.inMemory());
            navigator = new Navigator(pathing, clock::get);
            session = new BuildSession(snapshot(targets), planner, printer, navigator,
                new SafetyMonitor(() -> safetyConfig), clock::get, (_, to) -> states.add(to), notes::add);
        }

        /** Milliseconds the clock advances per tick; travel timeouts need a clock that moves. */
        int msPerTick;

        void tick() {
            budget.resetTick();
            clock.addAndGet(msPerTick);
            session.tick(world, player, inventory, safety, actions);
        }

        /** Ticks at most {@code max} times or until the session is in {@code until}. */
        void tickUntil(BuildSession.State until, int max) {
            for (int i = 0; i < max && session.state() != until; i++) tick();
            assertSame(until, session.state(), "reached " + until + " within " + max + " ticks");
        }

        /** Ticks until DONE; fails loudly instead of hanging if the machine ever stalls. */
        void tickUntilDone() {
            for (int i = 0; i < 500 && session.state() != BuildSession.State.DONE; i++) tick();
            assertSame(BuildSession.State.DONE, session.state(), "reached DONE within 500 ticks");
        }
    }

    private static SchematicSnapshot snapshot(Map<BlockPos, BlockState> blocks) {
        Long2ObjectMap<BlockState> map = new Long2ObjectOpenHashMap<>();
        blocks.forEach((pos, state) -> map.put(pos.asLong(), state));
        BlockPos min = blocks.keySet().stream().reduce((a, b) -> new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()))).orElseThrow();
        BlockPos max = blocks.keySet().stream().reduce((a, b) -> new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))).orElseThrow();
        return new SchematicSnapshot("test", min, max, map, MaterialRules.totals(map.values()));
    }

    /** Records the target of every placement; with {@code accept} the block appears in the world. */
    private static final class FakeActions implements PrintActions {
        final FakeWorld world;
        final List<BlockPos> placed = new ArrayList<>();
        boolean accept = true;
        /** The first n placements are sent but do not appear in the world, like a server rejecting them. */
        int rejectFirst;

        FakeActions(FakeWorld world) {
            this.world = world;
        }

        @Override
        public boolean place(PlacementPlan plan, int hotbarSlot) {
            BlockPos target = plan.clickPos().relative(plan.clickFace());
            placed.add(target);
            if (accept && placed.size() > rejectFirst) world.set(target, stone);
            return true;
        }

        @Override
        public boolean swapToHotbar(int inventorySlot, int hotbarSlot) {
            return false;
        }
    }

    private static final class FakePlayer implements PlayerView {
        private Vec3 eyePos;

        FakePlayer(Vec3 eyePos) {
            this.eyePos = eyePos;
        }

        @Override
        public Vec3 eyePos() {
            return eyePos;
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
        public double reach() {
            return 4.5;
        }

        @Override
        public boolean hasLineOfSight(Vec3 target) {
            return true;
        }
    }

    private static final class FakeWorld implements WorldView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        void set(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
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
