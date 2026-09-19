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
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RotationSegment;
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
 * P2-02 AK1–AK3 and P2-04 AK1. Expected outcomes are checked against the vanilla 26.2 placement formulas
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
    void unknownBlocksAreUnsupported() {
        FakeWorld floor = new FakeWorld().set(POS.below(), STONE);
        assertInstanceOf(SolveResult.Unsupported.class, solve(solver(true), Blocks.RAIL.defaultBlockState(), floor));
        assertEquals(Optional.of("no rule for RailBlock"), PlacementSolver.unsupportedReason(Blocks.RAIL.defaultBlockState()));
        assertEquals(Optional.empty(), PlacementSolver.unsupportedReason(STONE));
        assertInstanceOf(SolveResult.Unsupported.class,
            solve(solver(true), new BlockTask(POS, STONE, STONE, TaskKind.SKIP, 0, SkipReason.NEVER_PLACE), floor));
    }

    // --- P2-04 AK1: dependent blocks, one expectation per class ----------------------------------------

    @Test
    void standingTorchesClickTheFloorTop() {
        for (BlockState torch : List.of(Blocks.TORCH.defaultBlockState(), Blocks.REDSTONE_TORCH.defaultBlockState(), Blocks.SOUL_TORCH.defaultBlockState())) {
            PlacementPlan plan = ok(solver(true), torch, new FakeWorld().set(POS.below(), STONE).set(POS.north(), STONE));
            assertEquals(POS.below(), plan.clickPos(), torch.toString());
            assertEquals(Direction.UP, plan.clickFace());
            assertFalse(plan.requiresRealRotation());
            // Only a wall: clicking it would make a wall torch, so there is no click for a standing one.
            assertEquals(new SolveResult.NeedsSupport(POS.below()), solve(solver(true), torch, new FakeWorld().set(POS.north(), STONE)));
            assertEquals(new SolveResult.NeedsSupport(POS.below()), solve(solver(false), torch, new FakeWorld()));
        }
    }

    @Test
    void wallBlocksClickTheWallTheyHangOn() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            List<BlockState> states = List.of(
                Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing),
                Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, facing),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing),
                Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing),
                wallButton(facing));
            BlockPos wall = POS.relative(facing.getOpposite());
            // Floor and all four walls present: only the wall behind FACING yields the state.
            FakeWorld world = new FakeWorld().set(POS.below(), STONE);
            for (Direction d : Direction.Plane.HORIZONTAL) world.set(POS.relative(d), STONE);
            FakePlayer eyeInFront = new FakePlayer(Vec3.atCenterOf(POS).add(facing.getStepX() * 0.3, 0.1, facing.getStepZ() * 0.3), 4.5);
            for (BlockState state : states) {
                PlacementPlan plan = ok(solver(true), state, world, eyeInFront);
                assertEquals(wall, plan.clickPos(), state.toString());
                // Vanilla: the clicked neighbour comes first in getNearestLookingDirections, so FACING = clicked face.
                assertEquals(facing, plan.clickFace(), state.toString());
                assertFalse(plan.requiresRealRotation());
                assertEquals(new SolveResult.NeedsSupport(wall), solve(solver(false), state, new FakeWorld().set(POS.below(), STONE)), state.toString());
            }
        }
    }

    @Test
    void floorAndCeilingButtonsAndLeversTakeFacingFromYaw() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState floorLever = Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, facing);
            PlacementPlan onFloor = ok(solver(true), floorLever, new FakeWorld().set(POS.below(), STONE));
            assertEquals(Direction.UP, onFloor.clickFace());
            assertTrue(onFloor.requiresRealRotation());
            assertEquals(facing, Direction.fromYRot(onFloor.yaw()), "yaw " + onFloor.yaw());

            BlockState ceilingButton = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.CEILING).setValue(ButtonBlock.FACING, facing);
            FakePlayer below = new FakePlayer(new Vec3(0.5, 63.0, -2.0), 4.5);
            PlacementPlan onCeiling = ok(solver(true), ceilingButton, new FakeWorld().set(POS.above(), STONE).set(POS.below(), STONE), below);
            assertEquals(POS.above(), onCeiling.clickPos());
            assertEquals(Direction.DOWN, onCeiling.clickFace());
            assertEquals(facing, Direction.fromYRot(onCeiling.yaw()));
            assertEquals(new SolveResult.NeedsSupport(POS.above()), solve(solver(true), ceilingButton, new FakeWorld().set(POS.below(), STONE)));
        }
        BlockState on = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.POWERED, true);
        assertEquals(Optional.of("switched on"), PlacementSolver.unsupportedReason(on));
    }

    @Test
    void trapdoorFromSideOrFromFloorAndCeiling() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState bottom = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
            BlockState top = bottom.setValue(TrapDoorBlock.HALF, Half.TOP);
            BlockPos wall = POS.relative(facing.getOpposite());
            FakePlayer eyeInFront = new FakePlayer(Vec3.atCenterOf(POS).add(facing.getStepX() * 1.5, 0.1, facing.getStepZ() * 1.5), 4.5);

            for (BlockState state : List.of(bottom, top)) {
                PlacementPlan side = ok(solver(true), state, new FakeWorld().set(wall, STONE), eyeInFront);
                // Vanilla: side click → FACING = clicked face, HALF = TOP if the hit is above the middle.
                assertEquals(facing, side.clickFace(), state.toString());
                assertEquals(state == top, side.hitVec().y - POS.getY() > 0.5, state.toString());
                assertFalse(side.requiresRealRotation());
            }

            PlacementPlan floor = ok(solver(true), bottom, new FakeWorld().set(POS.below(), STONE));
            assertEquals(Direction.UP, floor.clickFace());
            // Vanilla: vertical click → FACING = opposite of the horizontal look direction.
            assertEquals(facing, Direction.fromYRot(floor.yaw()).getOpposite(), "yaw " + floor.yaw());
            assertTrue(floor.requiresRealRotation());

            assertEquals(new SolveResult.NeedsSupport(POS.above()), solve(solver(true), top, new FakeWorld().set(POS.below(), STONE)));
        }
        BlockState open = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true);
        assertEquals(Optional.of("opened by hand"), PlacementSolver.unsupportedReason(open));
        assertEquals(Optional.empty(), PlacementSolver.unsupportedReason(open.setValue(TrapDoorBlock.POWERED, true)));
    }

    @Test
    void doorLowerHalfFacingFromYawAndHingeFromClick() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoorHingeSide hinge : DoorHingeSide.values()) {
                BlockState door = door(facing, hinge, DoubleBlockHalf.LOWER);
                PlacementPlan plan = ok(solver(true), door, new FakeWorld().set(POS.below(), STONE));
                assertEquals(POS.below(), plan.clickPos());
                assertEquals(Direction.UP, plan.clickFace());
                assertTrue(plan.requiresRealRotation());
                assertEquals(facing, Direction.fromYRot(plan.yaw()), "yaw " + plan.yaw());
                assertEquals(hinge, vanillaClickHinge(facing, plan.hitVec()), facing + " " + hinge + " hit " + plan.hitVec());
            }
        }
    }

    @Test
    void doorUpperHalfBlockedAboveAndForcedHinge() {
        FakeWorld floor = new FakeWorld().set(POS.below(), STONE);
        BlockState upper = door(Direction.NORTH, DoorHingeSide.LEFT, DoubleBlockHalf.UPPER);
        assertEquals(new SolveResult.NeedsSupport(POS.below()), solve(solver(true), upper, floor));
        assertEquals(Optional.empty(), PlacementSolver.unsupportedReason(upper));

        assertEquals(new SolveResult.Unsupported("door blocked above"), solve(solver(true),
            door(Direction.NORTH, DoorHingeSide.LEFT, DoubleBlockHalf.LOWER), new FakeWorld().set(POS.below(), STONE).set(POS.above(), STONE)));

        // Facing north, left is west: two full blocks on the left force hinge LEFT (vanilla balance < 0).
        FakeWorld leftWall = new FakeWorld().set(POS.below(), STONE).set(POS.west(), STONE).set(POS.west().above(), STONE);
        assertInstanceOf(SolveResult.Ok.class, solve(solver(true), door(Direction.NORTH, DoorHingeSide.LEFT, DoubleBlockHalf.LOWER), leftWall));
        assertEquals(new SolveResult.Unsupported("hinge forced by neighbours"),
            solve(solver(true), door(Direction.NORTH, DoorHingeSide.RIGHT, DoubleBlockHalf.LOWER), leftWall));

        assertEquals(Optional.of("opened by hand"),
            PlacementSolver.unsupportedReason(door(Direction.NORTH, DoorHingeSide.LEFT, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, true)));
    }

    @Test
    void carpetNeedsAnyBlockBelow() {
        BlockState carpet = Blocks.CARPET.white().defaultBlockState();
        PlacementPlan plan = ok(solver(true), carpet, new FakeWorld().set(POS.below(), STONE));
        assertEquals(Direction.UP, plan.clickFace());
        assertFalse(plan.requiresRealRotation());

        assertEquals(new SolveResult.NeedsSupport(POS.below()), solve(solver(true), carpet, new FakeWorld().set(POS.west(), STONE)));
        // Below is not empty but has no full top face (fence post): the carpet survives, a side click places it.
        FakeWorld fenceBelow = new FakeWorld().set(POS.below(), Blocks.OAK_FENCE.defaultBlockState()).set(POS.west(), STONE);
        assertEquals(POS.west(), ok(solver(true), carpet, fenceBelow).clickPos());
    }

    @Test
    void standingSignRotationFromYawSegment() {
        for (int rotation = 0; rotation < 16; rotation++) {
            BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
            PlacementPlan plan = ok(solver(true), sign, new FakeWorld().set(POS.below(), STONE));
            assertEquals(Direction.UP, plan.clickFace());
            assertTrue(plan.requiresRealRotation());
            // Vanilla StandingSignBlock: ROTATION = RotationSegment.convertToSegment(yaw + 180).
            assertEquals(rotation, RotationSegment.convertToSegment(plan.yaw() + 180f), "yaw " + plan.yaw());
        }
    }

    @Test
    void dependentBlocksNeverAirplace() {
        List<BlockState> targets = List.of(Blocks.TORCH.defaultBlockState(), Blocks.LADDER.defaultBlockState(),
            Blocks.OAK_TRAPDOOR.defaultBlockState(), door(Direction.NORTH, DoorHingeSide.LEFT, DoubleBlockHalf.LOWER),
            Blocks.OAK_SIGN.defaultBlockState(), wallButton(Direction.NORTH));
        for (BlockState target : targets) {
            assertInstanceOf(SolveResult.NeedsSupport.class, solve(solver(false), target, new FakeWorld()), target.toString());
        }
    }

    // --- P5-03 fluids -------------------------------------------------------------------------------

    @Test
    void waterSourceIsPouredOntoTheFloorFaceWithARealRotation() {
        BlockTask task = fluidTask(Blocks.WATER.defaultBlockState());
        SolveResult result = solver(true).solveFluid(task, new FakeWorld().set(POS.below(), STONE), NORTH_OF_TARGET);
        PlacementPlan plan = assertInstanceOf(SolveResult.Ok.class, result).plan();
        assertEquals(POS.below(), plan.clickPos());
        assertEquals(Direction.UP, plan.clickFace());
        assertEquals(Items.WATER_BUCKET, plan.handItem());
        assertTrue(plan.requiresRealRotation(), "the server raycasts from the packet rotation");
        assertFalse(plan.sneak());
        assertEquals(POS, plan.clickPos().relative(plan.clickFace()), "the fluid lands in the target");
    }

    @Test
    void waterIsNeverPouredAgainstAWaterloggableBlockButLavaIs() {
        BlockState topSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        FakeWorld world = new FakeWorld().set(POS.below(), topSlab);
        assertInstanceOf(SolveResult.NeedsSupport.class,
            solver(true).solveFluid(fluidTask(Blocks.WATER.defaultBlockState()), world, NORTH_OF_TARGET),
            "the bucket would waterlog the slab instead");
        PlacementPlan lava = assertInstanceOf(SolveResult.Ok.class,
            solver(true).solveFluid(fluidTask(Blocks.LAVA.defaultBlockState()), world, NORTH_OF_TARGET)).plan();
        assertEquals(Items.LAVA_BUCKET, lava.handItem());
        assertEquals(POS.below(), lava.clickPos());
    }

    @Test
    void fluidTargetMustBeEmptyAndASource() {
        FakeWorld grass = new FakeWorld().set(POS.below(), STONE).set(POS, Blocks.SHORT_GRASS.defaultBlockState());
        assertInstanceOf(SolveResult.Unsupported.class,
            solver(true).solveFluid(fluidTask(Blocks.WATER.defaultBlockState()), grass, NORTH_OF_TARGET),
            "grass would catch the raycast");

        BlockState flowing = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3);
        FakeWorld floor = new FakeWorld().set(POS.below(), STONE);
        assertInstanceOf(SolveResult.Unsupported.class, solver(true).solveFluid(fluidTask(flowing), floor, NORTH_OF_TARGET));

        FakeWorld flowingThere = new FakeWorld().set(POS.below(), STONE).set(POS, flowing);
        assertInstanceOf(SolveResult.Ok.class,
            solver(true).solveFluid(fluidTask(Blocks.WATER.defaultBlockState()), flowingThere, NORTH_OF_TARGET),
            "flowing water is replaced by the source");
    }

    private static BlockTask fluidTask(BlockState target) {
        return new BlockTask(POS, target, Blocks.AIR.defaultBlockState(), TaskKind.FLUID, WorkPlanner.PRIORITY_FLUID, SkipReason.NONE);
    }

    private static BlockState wallButton(Direction facing) {
        return Blocks.OAK_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, facing);
    }

    private static BlockState door(Direction facing, DoorHingeSide hinge, DoubleBlockHalf half) {
        return Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.HALF, half);
    }

    /** Vanilla DoorBlock.getHinge when no neighbour forces a side: the click position inside the door column decides. */
    private static DoorHingeSide vanillaClickHinge(Direction facing, Vec3 hit) {
        int stepX = facing.getStepX();
        int stepZ = facing.getStepZ();
        double clickX = hit.x - POS.getX();
        double clickZ = hit.z - POS.getZ();
        return (stepX >= 0 || !(clickZ < 0.5)) && (stepX <= 0 || !(clickZ > 0.5)) && (stepZ >= 0 || !(clickX > 0.5)) && (stepZ <= 0 || !(clickX < 0.5))
            ? DoorHingeSide.LEFT
            : DoorHingeSide.RIGHT;
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
