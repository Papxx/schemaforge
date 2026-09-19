package dev.tore.schemaforge.core;

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import dev.tore.schemaforge.core.view.PlayerView;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * BlockState to PlacementPlan (click face, hit point, look direction, hand item).
 * Base block classes since P2-02, dependent blocks since P2-04, rails since P5-04 (rules in ARCHITECTURE.md §4, derived from vanilla 26.2
 * {@code getStateForPlacement}).
 */
public final class PlacementSolver {
    /** Yaw may deviate this far from a cardinal direction and still count as facing it (vanilla boundary is 45°). */
    private static final float QUADRANT_MARGIN = 44f;
    /** Yaw may deviate this far from a sign rotation segment (vanilla boundary is 11.25°). */
    private static final float SEGMENT_MARGIN = 11f;
    /** Hit height above the block bottom for side clicks that must produce a bottom or top half. */
    private static final double LOWER_HIT = 0.25;
    private static final double UPPER_HIT = 0.75;
    /** Horizontal shift of the hit point on a door's floor that selects the hinge side. */
    private static final double HINGE_SHIFT = 0.25;

    private final SolverConfig cfg;

    public PlacementSolver(SolverConfig cfg) {
        this.cfg = cfg;
    }

    /** The settings this solver rates candidates with; the Printer repeats the reach and sight checks (P2-03). */
    public SolverConfig config() {
        return cfg;
    }

    /**
     * Picks the best click for a PLACE task. The returned plan may still be out of reach or out of sight when no
     * better candidate exists; the Printer checks that before sending (P2-03).
     */
    public SolveResult solve(BlockTask task, WorldView world, PlayerView player, EasyPlaceProtocol proto) {
        // proto is evaluated from P5-06 on; until then every rotation-dependent block needs a real rotation.
        if (task.kind() != TaskKind.PLACE) return new SolveResult.Unsupported("not a PLACE task: " + task.kind());
        Optional<String> unsupported = unsupportedReason(task.target());
        if (unsupported.isPresent()) return new SolveResult.Unsupported(unsupported.get());
        Rule rule = rule(task.target()).orElseThrow();
        BlockPos pos = task.pos();
        Optional<SolveResult> blocked = blocked(task.target(), pos, world);
        if (blocked.isPresent()) return blocked.get();
        Item item = task.target().getBlock().asItem();

        List<Candidate> candidates = new ArrayList<>();
        for (Direction toNeighbour : Direction.values()) {
            Direction clickFace = toNeighbour.getOpposite();
            if (!rule.faceAllowed().test(clickFace)) continue;
            BlockPos neighbour = pos.relative(toNeighbour);
            if (!isSupport(world.getBlockState(neighbour), clickFace)) continue;
            candidates.add(candidate(neighbour, clickFace, hitOnFace(pos, toNeighbour, rule), true, player));
        }
        if (rule.airplace() && !cfg.clickAdjacentOnly() && world.getBlockState(pos).canBeReplaced()) {
            for (Direction face : Direction.values()) {
                if (!rule.faceAllowed().test(face)) continue;
                candidates.add(candidate(pos, face, hitOnFace(pos, face, rule), false, player));
            }
        }

        Optional<Candidate> best = candidates.stream().min(Comparator
            .comparing((Candidate c) -> !c.usable())
            .thenComparing(c -> !c.neighbour())
            .thenComparingDouble(Candidate::distance));
        if (best.isEmpty()) return new SolveResult.NeedsSupport(missingSupport(task.target(), pos));

        Candidate c = best.get();
        Vec3 look = c.hitVec().subtract(player.eyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        Optional<YawTarget> yawTarget = rule.yaw().apply(c.clickFace());
        if (yawTarget.isPresent()) yaw = yawTarget.get().clamp(yaw);
        return new SolveResult.Ok(new PlacementPlan(c.clickPos(), c.clickFace(), c.hitVec(), Mth.wrapDegrees(yaw), pitch,
            yawTarget.isPresent(), item, true));
    }

    /**
     * A bucket click for a FLUID task (P5-03): the face of a neighbour towards the target, so the fluid lands in the
     * target position. The server raycasts from the rotation of the use packet, so the plan always needs a real rotation.
     */
    public SolveResult solveFluid(BlockTask task, WorldView world, PlayerView player) {
        if (task.kind() != TaskKind.FLUID) return new SolveResult.Unsupported("not a FLUID task: " + task.kind());
        BlockState target = task.target();
        Item bucket = target.getBlock() instanceof LiquidBlock && target.getValue(LiquidBlock.LEVEL) == 0
            ? MaterialRules.required(target).stream().map(MaterialRules.Requirement::item).findFirst().orElse(Items.AIR)
            : Items.AIR;
        if (bucket == Items.AIR) return new SolveResult.Unsupported("no fluid source");
        BlockPos pos = task.pos();
        BlockState current = world.getBlockState(pos);
        // Anything with an outline in the target position would catch the server's raycast first.
        if (!current.isAir() && !(current.getBlock() instanceof LiquidBlock)) return new SolveResult.Unsupported("fluid target not empty");
        boolean water = target.getFluidState().is(Fluids.WATER);

        List<Candidate> candidates = new ArrayList<>();
        for (Direction toNeighbour : Direction.values()) {
            Direction clickFace = toNeighbour.getOpposite();
            BlockPos neighbour = pos.relative(toNeighbour);
            BlockState state = world.getBlockState(neighbour);
            if (!isSupport(state, clickFace)) continue;
            // A water bucket fills a waterloggable block it is used on instead of the block in front of it.
            if (water && state.getBlock() instanceof LiquidBlockContainer) continue;
            Vec3 center = Vec3.atCenterOf(pos);
            Vec3 hit = center.add(toNeighbour.getStepX() * 0.5, toNeighbour.getStepY() * 0.5, toNeighbour.getStepZ() * 0.5);
            candidates.add(candidate(neighbour, clickFace, hit, true, player));
        }
        Optional<Candidate> best = candidates.stream().min(Comparator
            .comparing((Candidate c) -> !c.usable())
            .thenComparingDouble(Candidate::distance));
        if (best.isEmpty()) return new SolveResult.NeedsSupport(pos.below());

        Candidate c = best.get();
        Vec3 look = c.hitVec().subtract(player.eyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new SolveResult.Ok(new PlacementPlan(c.clickPos(), c.clickFace(), c.hitVec(), Mth.wrapDegrees(yaw), pitch,
            true, bucket, false));
    }

    /**
     * Why a target state cannot be placed by this solver at all, independent of world and player;
     * empty if a rule exists. Used by {@code .sf preview} for its warning line.
     */
    public static Optional<String> unsupportedReason(BlockState target) {
        Block block = target.getBlock();
        if (block.asItem() == Items.AIR) return Optional.of("no item");
        if (block instanceof SlabBlock && target.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return Optional.of("double slab");
        if ((block instanceof DoorBlock || block instanceof TrapDoorBlock)
            && target.getValue(BlockStateProperties.OPEN) && !target.getValue(BlockStateProperties.POWERED)) {
            return Optional.of("opened by hand");
        }
        if ((block instanceof ButtonBlock || block instanceof LeverBlock) && target.getValue(BlockStateProperties.POWERED)) {
            return Optional.of("switched on");
        }
        if (rule(target).isEmpty()) return Optional.of("no rule for " + block.getClass().getSimpleName());
        return Optional.empty();
    }

    /**
     * True if the placed block keeps standing when the block it was clicked against disappears again (P5-02):
     * a rule exists and it is not one of the dependent blocks that hang on a floor, wall or ceiling.
     */
    public static boolean standsWithoutSupport(BlockState target) {
        return unsupportedReason(target).isEmpty() && dependentRule(target).isEmpty();
    }

    /**
     * @param faceAllowed which clicked faces yield the target state
     * @param half        hit height for side clicks
     * @param yaw         look direction the player needs for a clicked face, if the state depends on it
     * @param airplace    whether clicking the target position itself yields the state
     * @param hitShift    direction to move the hit point by {@link #HINGE_SHIFT} within the face (door hinge)
     */
    private record Rule(Predicate<Direction> faceAllowed, HitHeight half, Function<Direction, Optional<YawTarget>> yaw,
                        boolean airplace, Optional<Direction> hitShift) {
        /** Base block: every allowed face needs the same (or no) look direction, airplace possible. */
        static Rule base(Predicate<Direction> faceAllowed, HitHeight half, Optional<YawTarget> yaw) {
            return new Rule(faceAllowed, half, _ -> yaw, true, Optional.empty());
        }

        /** Dependent block clicked on exactly one kind of face, no airplace. */
        static Rule attached(Predicate<Direction> faceAllowed, Optional<YawTarget> yaw) {
            return new Rule(faceAllowed, HitHeight.MIDDLE, _ -> yaw, false, Optional.empty());
        }
    }

    private enum HitHeight { MIDDLE, LOWER, UPPER }

    /**
     * Yaw the player must look at: {@code center} ± {@code margin} degrees; with {@code eitherWay} the opposite
     * direction counts as well.
     */
    private record YawTarget(float center, float margin, boolean eitherWay) {
        YawTarget(float center, float margin) {
            this(center, margin, false);
        }

        /** The player looks in {@code facing} ({@code Direction.fromYRot}). */
        static Optional<YawTarget> facing(Direction facing) {
            return Optional.of(new YawTarget(facing.toYRot(), QUADRANT_MARGIN));
        }

        /** The player looks along the axis of {@code direction}, whichever way is closer (P5-04, rails). */
        static Optional<YawTarget> along(Direction direction) {
            return Optional.of(new YawTarget(direction.toYRot(), QUADRANT_MARGIN, true));
        }

        float clamp(float yaw) {
            float c = eitherWay && Math.abs(Mth.wrapDegrees(yaw - center)) > 90f ? center + 180f : center;
            return c + Mth.clamp(Mth.wrapDegrees(yaw - c), -margin, margin);
        }
    }

    private record Candidate(BlockPos clickPos, Direction clickFace, Vec3 hitVec, boolean neighbour, boolean usable, double distance) {
    }

    private static Optional<Rule> rule(BlockState target) {
        Block block = target.getBlock();
        if (block.asItem() == Items.AIR) return Optional.empty();
        if (block instanceof SlabBlock) {
            return switch (target.getValue(SlabBlock.TYPE)) {
                case BOTTOM -> Optional.of(Rule.base(face -> face != Direction.DOWN, HitHeight.LOWER, Optional.empty()));
                case TOP -> Optional.of(Rule.base(face -> face != Direction.UP, HitHeight.UPPER, Optional.empty()));
                case DOUBLE -> Optional.empty();
            };
        }
        if (block instanceof StairBlock) {
            boolean top = target.getValue(StairBlock.HALF) == Half.TOP;
            return Optional.of(Rule.base(face -> face != (top ? Direction.UP : Direction.DOWN),
                top ? HitHeight.UPPER : HitHeight.LOWER, YawTarget.facing(target.getValue(StairBlock.FACING))));
        }
        if (block instanceof RotatedPillarBlock) {
            Direction.Axis axis = target.getValue(RotatedPillarBlock.AXIS);
            return Optional.of(Rule.base(face -> face.getAxis() == axis, HitHeight.MIDDLE, Optional.empty()));
        }
        if (block instanceof GlazedTerracottaBlock) {
            return Optional.of(Rule.base(_ -> true, HitHeight.MIDDLE, YawTarget.facing(target.getValue(HorizontalDirectionalBlock.FACING).getOpposite())));
        }
        if (block instanceof AbstractFurnaceBlock) {
            return Optional.of(Rule.base(_ -> true, HitHeight.MIDDLE, YawTarget.facing(target.getValue(AbstractFurnaceBlock.FACING).getOpposite())));
        }
        if (block instanceof FenceBlock || block instanceof WallBlock || isPlain(target)) {
            return Optional.of(Rule.base(_ -> true, HitHeight.MIDDLE, Optional.empty()));
        }
        return dependentRule(target);
    }

    /** P2-04: blocks that hang on a neighbour, a floor or a ceiling. */
    private static Optional<Rule> dependentRule(BlockState target) {
        Block block = target.getBlock();
        // Wall variants extend the standing torch classes, so they are checked first.
        if (block instanceof WallTorchBlock || block instanceof RedstoneWallTorchBlock || block instanceof LadderBlock || block instanceof WallSignBlock) {
            Direction facing = target.getValue(HorizontalDirectionalBlock.FACING);
            return Optional.of(Rule.attached(face -> face == facing, Optional.empty()));
        }
        if (block instanceof TorchBlock || block instanceof RedstoneTorchBlock) {
            return Optional.of(Rule.attached(face -> face == Direction.UP, Optional.empty()));
        }
        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            Direction facing = target.getValue(HorizontalDirectionalBlock.FACING);
            return Optional.of(switch (target.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
                case FLOOR -> Rule.attached(face -> face == Direction.UP, YawTarget.facing(facing));
                case CEILING -> Rule.attached(face -> face == Direction.DOWN, YawTarget.facing(facing));
                case WALL -> Rule.attached(face -> face == facing, Optional.empty());
            });
        }
        if (block instanceof TrapDoorBlock) {
            Direction facing = target.getValue(HorizontalDirectionalBlock.FACING);
            boolean top = target.getValue(TrapDoorBlock.HALF) == Half.TOP;
            Direction vertical = top ? Direction.DOWN : Direction.UP;
            return Optional.of(new Rule(
                face -> face.getAxis().isHorizontal() ? face == facing : face == vertical,
                top ? HitHeight.UPPER : HitHeight.LOWER,
                face -> face.getAxis().isHorizontal() ? Optional.empty() : YawTarget.facing(facing.getOpposite()),
                false, Optional.empty()));
        }
        if (block instanceof DoorBlock) {
            // The upper half has no click of its own; it appears together with the lower half (NeedsSupport below).
            if (target.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                return Optional.of(Rule.attached(_ -> false, Optional.empty()));
            }
            Direction facing = target.getValue(HorizontalDirectionalBlock.FACING);
            Direction shift = target.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? facing.getCounterClockWise() : facing.getClockWise();
            return Optional.of(new Rule(face -> face == Direction.UP, HitHeight.MIDDLE, _ -> YawTarget.facing(facing), false, Optional.of(shift)));
        }
        if (block instanceof CarpetBlock) {
            return Optional.of(Rule.base(_ -> true, HitHeight.MIDDLE, Optional.empty()));
        }
        if (block instanceof StandingSignBlock) {
            float yaw = RotationSegment.convertToDegrees(target.getValue(StandingSignBlock.ROTATION)) - 180f;
            return Optional.of(Rule.attached(face -> face == Direction.UP, Optional.of(new YawTarget(yaw, SEGMENT_MARGIN))));
        }
        if (block instanceof BaseRailBlock rail) {
            // The rail lands on pos whatever face is clicked; vanilla sets NORTH_SOUTH or EAST_WEST from the look
            // direction and then connects it to neighbouring rails, which also makes curves and slopes (P5-04).
            Optional<YawTarget> yaw = switch (target.getValue(rail.getShapeProperty())) {
                case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> YawTarget.along(Direction.SOUTH);
                case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> YawTarget.along(Direction.EAST);
                case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> Optional.empty();
            };
            return Optional.of(new Rule(_ -> true, HitHeight.MIDDLE, _ -> yaw, false, Optional.empty()));
        }
        return Optional.empty();
    }

    /** World conditions that make a placement fail regardless of the click (P2-04). */
    private static Optional<SolveResult> blocked(BlockState target, BlockPos pos, WorldView world) {
        Block block = target.getBlock();
        if (block instanceof CarpetBlock && world.getBlockState(pos.below()).isAir()) {
            return Optional.of(new SolveResult.NeedsSupport(pos.below()));
        }
        if (block instanceof BaseRailBlock
            && !world.getBlockState(pos.below()).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)) {
            return Optional.of(new SolveResult.NeedsSupport(pos.below()));
        }
        if (block instanceof DoorBlock && target.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            if (!world.getBlockState(pos.above()).canBeReplaced()) return Optional.of(new SolveResult.Unsupported("door blocked above"));
            Optional<DoorHingeSide> forced = forcedHinge(pos, target.getValue(HorizontalDirectionalBlock.FACING), world);
            if (forced.isPresent() && forced.get() != target.getValue(DoorBlock.HINGE)) {
                return Optional.of(new SolveResult.Unsupported("hinge forced by neighbours"));
            }
        }
        return Optional.empty();
    }

    /** Hinge side that vanilla {@code DoorBlock.getHinge} derives from the neighbours; empty if the click position decides. */
    private static Optional<DoorHingeSide> forcedHinge(BlockPos pos, Direction facing, WorldView world) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        int balance = (fullBlock(world, pos.relative(left)) ? -1 : 0)
            + (fullBlock(world, pos.above().relative(left)) ? -1 : 0)
            + (fullBlock(world, pos.relative(right)) ? 1 : 0)
            + (fullBlock(world, pos.above().relative(right)) ? 1 : 0);
        boolean doorLeft = lowerDoor(world.getBlockState(pos.relative(left)));
        boolean doorRight = lowerDoor(world.getBlockState(pos.relative(right)));
        if ((doorLeft && !doorRight) || balance > 0) return Optional.of(DoorHingeSide.RIGHT);
        if ((doorRight && !doorLeft) || balance < 0) return Optional.of(DoorHingeSide.LEFT);
        return Optional.empty();
    }

    private static boolean fullBlock(WorldView world, BlockPos pos) {
        return world.getBlockState(pos).isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static boolean lowerDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Full block without properties besides waterlogged: every click yields the target. Non-full blocks without
     * properties (flowers, pressure plates) need a specific support and have no rule.
     */
    private static boolean isPlain(BlockState target) {
        return target.getProperties().stream().allMatch(p -> p == BlockStateProperties.WATERLOGGED)
            && target.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A neighbour can be clicked if it is solid, not replaceable and its face towards the target is full. */
    private static boolean isSupport(BlockState state, Direction clickFace) {
        return !state.isAir() && !state.canBeReplaced() && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, clickFace);
    }

    /**
     * Point on the face of {@code pos} in direction {@code face}; side faces get the hit height of the wanted half,
     * the rule's hit shift moves the point within the face.
     */
    private static Vec3 hitOnFace(BlockPos pos, Direction face, Rule rule) {
        Vec3 center = Vec3.atCenterOf(pos);
        double y = face.getAxis().isHorizontal()
            ? pos.getY() + switch (rule.half()) {
                case MIDDLE -> 0.5;
                case LOWER -> LOWER_HIT;
                case UPPER -> UPPER_HIT;
            }
            : center.y + face.getStepY() * 0.5;
        Vec3 hit = new Vec3(center.x + face.getStepX() * 0.5, y, center.z + face.getStepZ() * 0.5);
        return rule.hitShift()
            .filter(shift -> shift.getAxis() != face.getAxis())
            .map(shift -> hit.add(shift.getStepX() * HINGE_SHIFT, shift.getStepY() * HINGE_SHIFT, shift.getStepZ() * HINGE_SHIFT))
            .orElse(hit);
    }

    private Candidate candidate(BlockPos clickPos, Direction clickFace, Vec3 hitVec, boolean neighbour, PlayerView player) {
        Vec3 eye = player.eyePos();
        boolean facesEye = eye.subtract(hitVec).dot(clickFace.getUnitVec3()) > 0;
        double distance = eye.distanceTo(hitVec);
        boolean usable = facesEye && distance <= player.reach() && (!cfg.lineOfSight() || player.hasLineOfSight(hitVec));
        return new Candidate(clickPos, clickFace, hitVec, neighbour, usable, distance);
    }

    private static BlockPos missingSupport(BlockState target, BlockPos pos) {
        Block block = target.getBlock();
        boolean upperHalf = (block instanceof SlabBlock && target.getValue(SlabBlock.TYPE) == SlabType.TOP)
            || (block instanceof StairBlock && target.getValue(StairBlock.HALF) == Half.TOP)
            || (block instanceof TrapDoorBlock && target.getValue(TrapDoorBlock.HALF) == Half.TOP)
            || (block instanceof FaceAttachedHorizontalDirectionalBlock && target.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.CEILING);
        if (upperHalf) return pos.above();
        boolean onWall = block instanceof WallTorchBlock || block instanceof RedstoneWallTorchBlock || block instanceof LadderBlock
            || block instanceof WallSignBlock
            || (block instanceof FaceAttachedHorizontalDirectionalBlock && target.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.WALL);
        if (onWall) return pos.relative(target.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
        if (block instanceof RotatedPillarBlock) {
            return switch (target.getValue(RotatedPillarBlock.AXIS)) {
                case X -> pos.west();
                case Z -> pos.north();
                case Y -> pos.below();
            };
        }
        return pos.below();
    }
}
