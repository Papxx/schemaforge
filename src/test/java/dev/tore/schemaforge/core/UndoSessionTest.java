package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P5-01: undo removes the newest own blocks, leaves everything else alone and respects the budget. */
class UndoSessionTest {
    private static BlockState stone;
    private static BlockState dirt;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        stone = Blocks.STONE.defaultBlockState();
        dirt = Blocks.DIRT.defaultBlockState();
    }

    @Test
    void theNewestBlocksGoFirst() {
        Fixture f = new Fixture();
        f.placed(new BlockPos(0, 64, 0));
        f.placed(new BlockPos(1, 64, 0));
        f.placed(new BlockPos(2, 64, 0));

        f.undo(2).run(50);

        assertEquals(List.of(new BlockPos(2, 64, 0), new BlockPos(1, 64, 0)), f.actions.broken,
            "newest first, and only as many as asked for");
        assertEquals(2, f.session.removedCount());
        assertTrue(f.world.states.containsKey(new BlockPos(0, 64, 0)), "the oldest block stays");
    }

    @Test
    void aBlockSomeoneElseChangedIsLeftAlone() {
        Fixture f = new Fixture();
        BlockPos ours = new BlockPos(0, 64, 0);
        BlockPos changed = new BlockPos(1, 64, 0);
        f.placed(ours);
        f.placed(changed);
        // Someone replaced our stone with dirt: not ours to remove any more.
        f.world.states.put(changed, dirt);

        f.undo(2).run(50);

        assertEquals(List.of(ours), f.actions.broken);
        assertEquals(1, f.session.removedCount());
        assertEquals(1, f.session.skippedCount());
        assertEquals(dirt, f.world.states.get(changed), "the foreign block is untouched");
    }

    @Test
    void blocksOutOfReachOrUnloadedAreReportedAndSkipped() {
        Fixture f = new Fixture();
        BlockPos far = new BlockPos(100, 64, 0);
        BlockPos unloaded = new BlockPos(5, 64, 0);
        f.placed(far);
        f.placed(unloaded);
        f.world.unloaded.add(unloaded);

        f.undo(2).run(50);

        assertEquals(List.of(), f.actions.broken);
        assertEquals(2, f.session.skippedCount());
        assertTrue(f.notes.stream().anyMatch(note -> note.contains("out of reach")), f.notes.toString());
        assertTrue(f.notes.stream().anyMatch(note -> note.contains("not loaded")), f.notes.toString());
    }

    @Test
    void miningRunsOverSeveralTicksAndThroughTheBudget() {
        Fixture f = new Fixture();
        f.placed(new BlockPos(0, 64, 0));
        f.actions.hitsNeeded = 3;
        f.limit = 1;

        f.undo(1);
        for (int i = 0; i < 2; i++) f.tickOnce();

        assertEquals(2, f.actions.broken.size(), "one mining step per tick");
        assertTrue(f.session.running(), "still mining");
        f.run(20);
        assertEquals(1, f.session.removedCount());
    }

    @Test
    void aBlockThatWillNotBreakIsGivenUpOn() {
        Fixture f = new Fixture();
        f.placed(new BlockPos(0, 64, 0));
        f.actions.hitsNeeded = Integer.MAX_VALUE;

        f.undo(1).run(UndoSession.BREAK_TIMEOUT_TICKS + 10);

        assertFalse(f.session.running());
        assertEquals(1, f.session.skippedCount());
        assertTrue(f.notes.getLast().contains("Could not break"), f.notes.toString());
    }

    @Test
    void askingForMoreThanWasPlacedUndoesWhatThereIs() {
        Fixture f = new Fixture();
        f.placed(new BlockPos(0, 64, 0));

        f.undo(50).run(50);

        assertEquals(1, f.session.requestedCount());
        assertEquals(1, f.session.removedCount());
    }

    private final class Fixture {
        final FakeWorld world = new FakeWorld();
        final FakeActions actions = new FakeActions(world);
        final List<PlacementLog.Entry> log = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        int limit = 4;
        final ActionBudget budget = new ActionBudget(() -> limit);
        UndoSession session;

        void placed(BlockPos pos) {
            world.states.put(pos, stone);
            log.add(new PlacementLog.Entry(pos, stone, false, log.size()));
        }

        Fixture undo(int count) {
            session = new UndoSession(log, count, actions, budget, notes::add);
            return this;
        }

        void tickOnce() {
            budget.resetTick();
            session.tick(world, PLAYER);
        }

        void run(int maxTicks) {
            for (int i = 0; i < maxTicks && session.running(); i++) tickOnce();
        }
    }

    private static final PlayerView PLAYER = new PlayerView() {
        @Override
        public Vec3 eyePos() {
            return new Vec3(1, 65.5, 0);
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
    };

    /** Breaks a block after {@code hitsNeeded} mining steps. */
    private static final class FakeActions implements UndoSession.Actions {
        private final FakeWorld world;
        final List<BlockPos> broken = new ArrayList<>();
        final Map<BlockPos, Integer> hits = new HashMap<>();
        int hitsNeeded = 1;

        FakeActions(FakeWorld world) {
            this.world = world;
        }

        @Override
        public boolean breakBlock(BlockPos pos) {
            broken.add(pos);
            if (hits.merge(pos, 1, Integer::sum) >= hitsNeeded) world.states.remove(pos);
            return true;
        }
    }

    private static final class FakeWorld implements WorldView {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();

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
