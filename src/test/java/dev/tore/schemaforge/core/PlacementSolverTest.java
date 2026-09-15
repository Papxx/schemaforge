package dev.tore.schemaforge.core;

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-02 AK1–AK3. Expected outcomes are checked against the vanilla 26.2 placement formulas
 * ({@link #vanillaBottomHalf}, {@link Direction#fromYRot}), not against the solver's own constants.
 */
class PlacementSolverTest {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    /** Standing two blocks north of the target, eyes above it, looking south. */
    private static final FakePlayer NORTH_OF_TARGET = new FakePlayer(new Vec3(0.5, 65.62, -2.5), 4.5);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- AK1: one expectation per block class -------------------------------------------------------

    @Test
    void fullBlockClicksTopOfFloor() {
        PlacementPlan plan = ok(solver(true), STONE, new FakeWorld().set(POS.below(), STONE));
        assertEquals(POS.below(), plan.clickPos());
        assertEquals(Direction.UP, plan.clickFace());
        assertEquals(new Vec3(0.5, 64.0, 0.5), plan.hitVec());
        assertFalse(plan.requiresRealRotation());
        assertEquals(Items.STONE, plan.handItem());
        assertTrue(plan.sneak());
    }

    @Test
    void bottomSlabFromFloorOrLowerSideHalf() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        PlacementPlan fromFloor = ok(solver(true), slab, new FakeWorld().set(POS.below(), STONE));
        assertEquals(Direction.UP, fromFloor.clickFace());
        assertTrue(vanillaBottomHalf(fromFloor));

        PlacementPlan fromSide = ok(solver(true), slab, new FakeWorld().set(POS.west(), STONE));
        assertEquals(Direction.EAST, fromSide.clickFace());
        assertTrue(vanillaBottomHalf(fromSide), "hit y " + fromSide.hitVec().y);
    }

    @Test
    void topSlabNeedsCeilingOrUpperSideHalf() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);

        assertEquals(new SolveResult.NeedsSupport(POS.above()), solve(solver(true), slab, new FakeWorld().set(POS.below(), STONE)));

        PlacementPlan fromSide = ok(solver(true), slab, new FakeWorld().set(POS.below(), STONE).set(POS.west(), STONE));
        assertEquals(Direction.EAST, fromSide.clickFace());
        assertFalse(vanillaBottomHalf(fromSide), "hit y " + fromSide.hitVec().y);

        PlacementPlan fromCeiling = ok(solver(true), slab, new FakeWorld().set(POS.above(), STONE));
        assertEquals(Direction.DOWN, fromCeiling.clickFace());
        assertFalse(vanillaBottomHalf(fromCeiling));
    }

    @Test
    void doubleSlabIsUnsupported() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        assertEquals(new SolveResult.Unsupported("double slab"), solve(solver(true), slab, new FakeWorld().set(POS.below(), STONE)));
    }

    @Test
    void stairsTakeFacingFromYawAndHalfFromClick() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState bottom = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
            PlacementPlan plan = ok(solver(true), bottom, new FakeWorld().set(POS.below(), STONE));
            assertTrue(plan.requiresRealRotation());
            assertEquals(facing, Direction.fromYRot(plan.yaw()), "yaw " + plan.yaw());
            assertTrue(vanillaBottomHalf(plan));
        }

        BlockState top = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.TOP);
        PlacementPlan plan = ok(solver(true), top, new FakeWorld().set(POS.below(), STONE).set(POS.west(), STONE));
        assertEquals(Direction.EAST, plan.clickFace());
        assertFalse(vanillaBottomHalf(plan));
        assertEquals(Direction.NORTH, Direction.fromYRot(plan.yaw()));
    }

    @Test
    void pillarClicksAlongItsAxis() {
        BlockState logY = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        assertEquals(Direction.UP, ok(solver(true), logY, new FakeWorld().set(POS.below(), STONE)).clickFace());

        BlockState logX = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        assertEquals(new SolveResult.NeedsSupport(POS.west()), solve(solver(true), logX, new FakeWorld().set(POS.below(), STONE)));

        PlacementPlan plan = ok(solver(true), logX, new FakeWorld().set(POS.below(), STONE).set(POS.east(), STONE));
        assertEquals(POS.east(), plan.clickPos());
        assertEquals(Direction.Axis.X, plan.clickFace().getAxis());
        assertFalse(plan.requiresRealRotation());
    }

    @Test
    void glazedTerracottaAndFurnaceFaceThePlayer() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState terracotta = Blocks.GLAZED_TERRACOTTA.white().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
            PlacementPlan plan = ok(solver(true), terracotta, new FakeWorld().set(POS.below(), STONE));
            assertTrue(plan.requiresRealRotation());
            assertEquals(facing.getOpposite(), Direction.fromYRot(plan.yaw()), "terracotta " + facing + " yaw " + plan.yaw());

            BlockState furnace = Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing);
            PlacementPlan furnacePlan = ok(solver(true), furnace, new FakeWorld().set(POS.below(), STONE));
            assertEquals(facing.getOpposite(), Direction.fromYRot(furnacePlan.yaw()), "furnace " + facing);
        }
    }

    @Test
    void fencesAndWallsIgnoreNeighbourProperties() {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.EAST, true);
        PlacementPlan plan = ok(solver(true), fence, new FakeWorld().set(POS.below(), STONE));
        assertEquals(Direction.UP, plan.clickFace());
        assertFalse(plan.requiresRealRotation());

        assertInstanceOf(SolveResult.Ok.class, solve(solver(true), Blocks.COBBLESTONE_WALL.defaultBlockState(), new FakeWorld().set(POS.below(), STONE)));
    }

    @Test
    void dependentAndUnknownBlocksAreUnsupported() {
        FakeWorld floor = new FakeWorld().set(POS.below(), STONE);
        assertInstanceOf(SolveResult.Unsupported.class, solve(solver(true), Blocks.TORCH.defaultBlockState(), floor));
        assertInstanceOf(SolveResult.Unsupported.class, solve(solver(true), Blocks.OAK_DOOR.defaultBlockState(), floor));
        assertEquals(Optional.of("no rule for TorchBlock"), PlacementSolver.unsupportedReason(Blocks.TORCH.defaultBlockState()));
        assertEquals(Optional.empty(), PlacementSolver.unsupportedReason(STONE));
        assertInstanceOf(SolveResult.Unsupported.class,
            solve(solver(true), new BlockTask(POS, STONE, STONE, TaskKind.SKIP, 0, SkipReason.NEVER_PLACE), floor));
    }

    // --- AK2 -------------------------------------------------------------------------------------------

    @Test
    void noNeighbourYieldsNeedsSupport() {
        assertEquals(new SolveResult.NeedsSupport(POS.below()), solve(solver(true), STONE, new FakeWorld()));
        // Replaceable or non-sturdy neighbours do not count as support.
        FakeWorld weak = new FakeWorld().set(POS.below(), Blocks.SHORT_GRASS.defaultBlockState())
            .set(POS.west(), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        assertEquals(new SolveResult.NeedsSupport(POS.below()), solve(solver(true), STONE, weak));
    }

    // --- AK3 -------------------------------------------------------------------------------------------

    @Test
    void clickAdjacentOnlyNeverClicksTargetPosition() {
        List<BlockState> targets = List.of(STONE,
            Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
            Blocks.OAK_STAIRS.defaultBlockState(),
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z),
            Blocks.GLAZED_TERRACOTTA.white().defaultBlockState());
        List<FakeWorld> worlds = List.of(new FakeWorld(), new FakeWorld().set(POS.below(), STONE),
            new FakeWorld().set(POS.north(), STONE).set(POS.above(), STONE));
        for (BlockState target : targets) {
            for (FakeWorld world : worlds) {
                if (solve(solver(true), target, world) instanceof SolveResult.Ok(PlacementPlan plan)) {
                    assertNotEquals(POS, plan.clickPos(), target + " in " + world.states);
                }
            }
        }
    }

    @Test
    void withoutClickAdjacentOnlyTargetPositionIsFallbackOnly() {
        PlacementPlan airplace = ok(solver(false), STONE, new FakeWorld());
        assertEquals(POS, airplace.clickPos());
        // The face points at the player (north), so a real ray could hit it.
        assertEquals(Direction.NORTH, airplace.clickFace());

        PlacementPlan withFloor = ok(solver(false), STONE, new FakeWorld().set(POS.below(), STONE));
        assertEquals(POS.below(), withFloor.clickPos());
    }

    // --- choice between candidates ---------------------------------------------------------------------

    @Test
    void prefersFaceTowardsEyeAndWithinReach() {
        FakeWorld floorAndCeiling = new FakeWorld().set(POS.below(), STONE).set(POS.above(), STONE);
        FakePlayer below = new FakePlayer(new Vec3(0.5, 62.0, -2.0), 4.5);
        assertEquals(Direction.DOWN, ok(solver(true), STONE, floorAndCeiling, below).clickFace());
        assertEquals(Direction.UP, ok(solver(true), STONE, floorAndCeiling, NORTH_OF_TARGET).clickFace());

        // Eye just below the floor's top face: the floor is closer but its face points away, the west neighbour's face does not.
        FakeWorld floorAndWest = new FakeWorld().set(POS.below(), STONE).set(POS.west(), STONE);
        FakePlayer lowEast = new FakePlayer(new Vec3(2.5, 63.9, 0.5), 4.5);
        assertEquals(POS.west(), ok(solver(true), STONE, floorAndWest, lowEast).clickPos());

        // Floor hit 4.31 away (out of reach 4), own north face 3.67 away: a usable airplace beats an unusable neighbour.
        FakePlayer farNorth = new FakePlayer(new Vec3(0.5, 65.62, -3.5), 4.0);
        assertEquals(POS, ok(solver(false), STONE, new FakeWorld().set(POS.below(), STONE), farNorth).clickPos());
        assertEquals(POS.below(), ok(solver(true), STONE, new FakeWorld().set(POS.below(), STONE), farNorth).clickPos());

        FakePlayer blind = new FakePlayer(NORTH_OF_TARGET.eyePos(), 4.5, false);
        PlacementPlan unseen = ok(new PlacementSolver(new SolverConfig(true, false)), STONE, new FakeWorld().set(POS.below(), STONE), blind);
        assertEquals(POS.below(), unseen.clickPos());
    }

    @Test
    void lookAnglesPointAtHitVec() {
        PlacementPlan plan = ok(solver(true), STONE, new FakeWorld().set(POS.below(), STONE));
        Vec3 eye = NORTH_OF_TARGET.eyePos();
        double yawRad = Math.toRadians(plan.yaw());
        double pitchRad = Math.toRadians(plan.pitch());
        Vec3 view = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3 expected = plan.hitVec().subtract(eye).normalize();
        assertTrue(view.distanceTo(expected) < 1e-4, view + " vs " + expected);
    }

    /** Vanilla SlabBlock/StairBlock: bottom unless the face is DOWN, or a side face was hit above the middle. */
    private static boolean vanillaBottomHalf(PlacementPlan plan) {
        BlockPos placedAt = plan.clickPos().relative(plan.clickFace());
        Direction face = plan.clickFace();
        return face != Direction.DOWN && (face == Direction.UP || !(plan.hitVec().y - placedAt.getY() > 0.5));
    }

    private static PlacementSolver solver(boolean clickAdjacentOnly) {
        return new PlacementSolver(new SolverConfig(clickAdjacentOnly, true));
    }

    private static SolveResult solve(PlacementSolver solver, BlockState target, WorldView world) {
        return solve(solver, new BlockTask(POS, target, Blocks.AIR.defaultBlockState(), TaskKind.PLACE, WorkPlanner.PRIORITY_BLOCK, SkipReason.NONE), world);
    }

    private static SolveResult solve(PlacementSolver solver, BlockTask task, WorldView world) {
        return solver.solve(task, world, NORTH_OF_TARGET, EasyPlaceProtocol.NONE);
    }

    private static PlacementPlan ok(PlacementSolver solver, BlockState target, WorldView world) {
        return ok(solver, target, world, NORTH_OF_TARGET);
    }

    private static PlacementPlan ok(PlacementSolver solver, BlockState target, WorldView world, PlayerView player) {
        BlockTask task = new BlockTask(POS, target, Blocks.AIR.defaultBlockState(), TaskKind.PLACE, WorkPlanner.PRIORITY_BLOCK, SkipReason.NONE);
        SolveResult result = solver.solve(task, world, player, EasyPlaceProtocol.NONE);
        return assertInstanceOf(SolveResult.Ok.class, result, target + " -> " + result).plan();
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
