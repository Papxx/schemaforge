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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * BlockState to PlacementPlan (click face, hit point, look direction, hand item).
 * Base block classes since P2-02 (rules in ARCHITECTURE.md §4, derived from vanilla 26.2 {@code getStateForPlacement});
 * dependent blocks follow in P2-04.
 */
public final class PlacementSolver {
    /** Yaw may deviate this far from a cardinal direction and still count as facing it (vanilla boundary is 45°). */
    private static final float QUADRANT_MARGIN = 44f;
    /** Hit height above the block bottom for side clicks that must produce a bottom or top half. */
    private static final double LOWER_HIT = 0.25;
    private static final double UPPER_HIT = 0.75;

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
        Optional<Rule> found = rule(task.target());
        if (found.isEmpty()) return new SolveResult.Unsupported(unsupportedReason(task.target()).orElse("no rule"));
        Rule rule = found.get();
        Item item = task.target().getBlock().asItem();

        BlockPos pos = task.pos();
        List<Candidate> candidates = new ArrayList<>();
        for (Direction toNeighbour : Direction.values()) {
            Direction clickFace = toNeighbour.getOpposite();
            if (!rule.faceAllowed().test(clickFace)) continue;
            BlockPos neighbour = pos.relative(toNeighbour);
            if (!isSupport(world.getBlockState(neighbour), clickFace)) continue;
            candidates.add(candidate(neighbour, clickFace, hitOnFace(pos, toNeighbour, rule.half()), true, player));
        }
        if (!cfg.clickAdjacentOnly() && world.getBlockState(pos).canBeReplaced()) {
            for (Direction face : Direction.values()) {
                if (!rule.faceAllowed().test(face)) continue;
                candidates.add(candidate(pos, face, hitOnFace(pos, face, rule.half()), false, player));
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
        if (rule.playerFacing().isPresent()) yaw = intoQuadrant(yaw, rule.playerFacing().get());
        return new SolveResult.Ok(new PlacementPlan(c.clickPos(), c.clickFace(), c.hitVec(), Mth.wrapDegrees(yaw), pitch,
            rule.playerFacing().isPresent(), item, true));
    }

    /**
     * Why a target state cannot be placed by this solver at all, independent of world and player;
     * empty if a rule exists. Used by {@code .sf preview} for its warning line.
     */
    public static Optional<String> unsupportedReason(BlockState target) {
        if (target.getBlock().asItem() == Items.AIR) return Optional.of("no item");
        if (target.getBlock() instanceof SlabBlock && target.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return Optional.of("double slab");
        }
        if (rule(target).isEmpty()) return Optional.of("no rule for " + target.getBlock().getClass().getSimpleName());
        return Optional.empty();
    }

    /**
     * @param faceAllowed  which clicked faces yield the target state
     * @param half         hit height for side clicks
     * @param playerFacing horizontal direction the player must look in, if the state depends on it
     */
    private record Rule(Predicate<Direction> faceAllowed, HitHeight half, Optional<Direction> playerFacing) {
    }

    private enum HitHeight { MIDDLE, LOWER, UPPER }

    private record Candidate(BlockPos clickPos, Direction clickFace, Vec3 hitVec, boolean neighbour, boolean usable, double distance) {
    }

    private static Optional<Rule> rule(BlockState target) {
        Block block = target.getBlock();
        if (block.asItem() == Items.AIR) return Optional.empty();
        if (block instanceof SlabBlock) {
            return switch (target.getValue(SlabBlock.TYPE)) {
                case BOTTOM -> Optional.of(new Rule(face -> face != Direction.DOWN, HitHeight.LOWER, Optional.empty()));
                case TOP -> Optional.of(new Rule(face -> face != Direction.UP, HitHeight.UPPER, Optional.empty()));
                case DOUBLE -> Optional.empty();
            };
        }
        if (block instanceof StairBlock) {
            boolean top = target.getValue(StairBlock.HALF) == Half.TOP;
            return Optional.of(new Rule(face -> face != (top ? Direction.UP : Direction.DOWN),
                top ? HitHeight.UPPER : HitHeight.LOWER, Optional.of(target.getValue(StairBlock.FACING))));
        }
        if (block instanceof RotatedPillarBlock) {
            Direction.Axis axis = target.getValue(RotatedPillarBlock.AXIS);
            return Optional.of(new Rule(face -> face.getAxis() == axis, HitHeight.MIDDLE, Optional.empty()));
        }
        if (block instanceof GlazedTerracottaBlock) {
            return Optional.of(new Rule(_ -> true, HitHeight.MIDDLE, Optional.of(target.getValue(HorizontalDirectionalBlock.FACING).getOpposite())));
        }
        if (block instanceof AbstractFurnaceBlock) {
            return Optional.of(new Rule(_ -> true, HitHeight.MIDDLE, Optional.of(target.getValue(AbstractFurnaceBlock.FACING).getOpposite())));
        }
        if (block instanceof FenceBlock || block instanceof WallBlock || isPlain(target)) {
            return Optional.of(new Rule(_ -> true, HitHeight.MIDDLE, Optional.empty()));
        }
        return Optional.empty();
    }

    /**
     * Full block without properties besides waterlogged: every click yields the target. Non-full blocks without
     * properties (torches, flowers, carpets) need a specific support and are left to P2-04.
     */
    private static boolean isPlain(BlockState target) {
        return target.getProperties().stream().allMatch(p -> p == BlockStateProperties.WATERLOGGED)
            && target.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A neighbour can be clicked if it is solid, not replaceable and its face towards the target is full. */
    private static boolean isSupport(BlockState state, Direction clickFace) {
        return !state.isAir() && !state.canBeReplaced() && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, clickFace);
    }

    /** Point on the face of {@code pos} in direction {@code face}; side faces get the hit height of the wanted half. */
    private static Vec3 hitOnFace(BlockPos pos, Direction face, HitHeight half) {
        Vec3 center = Vec3.atCenterOf(pos);
        double y = face.getAxis().isHorizontal()
            ? pos.getY() + switch (half) {
                case MIDDLE -> 0.5;
                case LOWER -> LOWER_HIT;
                case UPPER -> UPPER_HIT;
            }
            : center.y + face.getStepY() * 0.5;
        return new Vec3(center.x + face.getStepX() * 0.5, y, center.z + face.getStepZ() * 0.5);
    }

    private Candidate candidate(BlockPos clickPos, Direction clickFace, Vec3 hitVec, boolean neighbour, PlayerView player) {
        Vec3 eye = player.eyePos();
        boolean facesEye = eye.subtract(hitVec).dot(clickFace.getUnitVec3()) > 0;
        double distance = eye.distanceTo(hitVec);
        boolean usable = facesEye && distance <= player.reach() && (!cfg.lineOfSight() || player.hasLineOfSight(hitVec));
        return new Candidate(clickPos, clickFace, hitVec, neighbour, usable, distance);
    }

    private static float intoQuadrant(float yaw, Direction facing) {
        float center = facing.toYRot();
        return center + Mth.clamp(Mth.wrapDegrees(yaw - center), -QUADRANT_MARGIN, QUADRANT_MARGIN);
    }

    private static BlockPos missingSupport(BlockState target, BlockPos pos) {
        Block block = target.getBlock();
        boolean upperHalf = (block instanceof SlabBlock && target.getValue(SlabBlock.TYPE) == SlabType.TOP)
            || (block instanceof StairBlock && target.getValue(StairBlock.HALF) == Half.TOP);
        if (upperHalf) return pos.above();
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
