package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.WorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Diff to tasks to clusters to build order. Rules and ordering are specified in docs/ARCHITECTURE.md §4.
 * {@code plan} is implemented in P1-03, {@code refresh} in P2-03.
 */
public final class WorkPlanner {
    public static final int PRIORITY_BREAK = 400;
    public static final int PRIORITY_FULL_BLOCK = 300;
    public static final int PRIORITY_BLOCK = 200;
    public static final int PRIORITY_DEPENDENT = 100;
    /** Curved rails after the straight ones they connect to (P5-04). */
    public static final int PRIORITY_RAIL_CURVE = 90;
    public static final int PRIORITY_FLUID = 50;
    public static final int PRIORITY_SKIP = 0;

    private static final Direction[] SLAB_CARRIERS = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final PlanConfig cfg;

    public WorkPlanner(PlanConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        if (cfg.clusterSize() < 1) throw new IllegalArgumentException("clusterSize must be >= 1, was " + cfg.clusterSize());
    }

    /**
     * All tasks of {@code snap} against the current world, grouped into clusters in build order.
     * {@code start} (usually the player position) seeds the nearest-neighbor ordering. Implemented in P1-03.
     */
    public List<Cluster> plan(SchematicSnapshot snap, WorldView world, BlockPos start) {
        Map<Long, List<BlockTask>> cells = new HashMap<>();
        Function<BlockPos, BlockState> planned = planned(snap);
        for (Long2ObjectMap.Entry<BlockState> entry : snap.blocks().long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            taskFor(pos, entry.getValue(), world, (p, target) -> placePriority(p, target, planned, world))
                .ifPresent(task -> cells.computeIfAbsent(cellKey(pos, snap.min()), k -> new ArrayList<>()).add(task));
        }

        List<Cell> ordered = orderClusters(cells, start);
        List<Cluster> clusters = new ArrayList<>(ordered.size());
        BlockPos cursor = start;
        for (Cell cell : ordered) {
            List<BlockTask> sorted = orderTasks(cell.tasks(), cursor);
            cursor = sorted.getLast().pos();
            clusters.add(new Cluster(clusters.size(), cell.center(), List.copyOf(sorted)));
        }
        return List.copyOf(clusters);
    }

    /**
     * Re-reads the world state of a cluster (P2-03): the rules of {@link #plan} for every task target, positions that
     * need nothing any more are dropped, the order of {@code c} is kept. PLACE tasks keep their priority; other tasks
     * that turn into PLACE get a new one, with carriers taken from the world only (no snapshot at hand).
     */
    public List<BlockTask> refresh(Cluster c, WorldView world) {
        List<BlockTask> tasks = new ArrayList<>(c.tasks().size());
        for (BlockTask old : c.tasks()) {
            taskFor(old.pos(), old.target(), world, (pos, target) -> old.kind() == TaskKind.PLACE
                ? old.priority()
                : placePriority(pos, target, _ -> null, world))
                .ifPresent(tasks::add);
        }
        return List.copyOf(tasks);
    }

    /** Empty when nothing has to happen at {@code pos}. */
    private Optional<BlockTask> taskFor(BlockPos pos, BlockState target, WorldView world, PlacePriority priority) {
        boolean targetAir = isEffectivelyAir(target);
        if (targetAir && cfg.ignoreAir()) return Optional.empty();
        // World state is unknown in unloaded chunks; void air marks that instead of a guessed state.
        if (!world.isChunkLoaded(pos)) return skip(pos, target, Blocks.VOID_AIR.defaultBlockState(), SkipReason.CHUNK_NOT_LOADED);
        BlockState current = world.getBlockState(pos);
        if (!targetAir && cfg.neverPlace().contains(target.getBlock())) return skip(pos, target, current, SkipReason.NEVER_PLACE);

        boolean currentAir = isEffectivelyAir(current);
        if (targetAir ? currentAir : matches(target, current)) return Optional.empty();
        if (cfg.skipIfWorldIs().contains(current.getBlock())) return skip(pos, target, current, SkipReason.WORLD_FILTER);

        if (!targetAir && (currentAir || current.canBeReplaced())) {
            if (target.getBlock() instanceof LiquidBlock) return task(pos, target, current, TaskKind.FLUID, PRIORITY_FLUID);
            return task(pos, target, current, TaskKind.PLACE, priority.of(pos, target));
        }
        if (cfg.additiveOnly()) return skip(pos, target, current, SkipReason.MISMATCH_ADDITIVE_ONLY);
        return task(pos, target, current, TaskKind.BREAK, PRIORITY_BREAK);
    }

    /** Air, a block from {@code treatAsAir}, or flowing fluid (only fluid sources can be placed). */
    private boolean isEffectivelyAir(BlockState state) {
        if (state.isAir() || cfg.treatAsAir().contains(state.getBlock())) return true;
        return state.getBlock() instanceof LiquidBlock && state.getValue(LiquidBlock.LEVEL) != 0;
    }

    /** Same block or an allowed substitute, and every shared property equal except {@code ignoreProperties}. */
    private boolean matches(BlockState target, BlockState current) {
        Block block = current.getBlock();
        boolean sameBlock = block == target.getBlock();
        if (!sameBlock && !cfg.substitutes().getOrDefault(target.getBlock(), List.of()).contains(block)) return false;
        for (Property<?> property : target.getProperties()) {
            if (cfg.ignoreProperties().contains(property.getName())) continue;
            Optional<Property<?>> other = sameBlock ? Optional.of(property) : propertyNamed(current, property.getName());
            if (other.isEmpty()) continue;
            if (!valueName(target, property).equals(valueName(current, other.get()))) return false;
        }
        return true;
    }

    /** Priority of a PLACE task at {@code pos}. */
    @FunctionalInterface
    private interface PlacePriority {
        int of(BlockPos pos, BlockState target);
    }

    /** Planned target at a position, or null if the schematic has none there (or none is known). */
    private static Function<BlockPos, BlockState> planned(SchematicSnapshot snap) {
        return pos -> snap.blocks().get(pos.asLong());
    }

    private int placePriority(BlockPos pos, BlockState target, Function<BlockPos, BlockState> planned, WorldView world) {
        if (isRailCurve(target)) return PRIORITY_RAIL_CURVE;
        if (isDependent(pos, target, planned, world)) return PRIORITY_DEPENDENT;
        return target.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? PRIORITY_FULL_BLOCK : PRIORITY_BLOCK;
    }

    /** Blocks that need a carrier which must already stand in the world. */
    private boolean isDependent(BlockPos pos, BlockState target, Function<BlockPos, BlockState> planned, WorldView world) {
        Block block = target.getBlock();
        if (block instanceof SlabBlock) return target.getValue(SlabBlock.TYPE) == SlabType.TOP && !hasSlabCarrier(pos, planned, world);
        return block instanceof BaseTorchBlock
            || block instanceof FaceAttachedHorizontalDirectionalBlock
            || block instanceof BaseRailBlock
            || block instanceof CarpetBlock
            || block instanceof DoorBlock
            || block instanceof SignBlock
            || block instanceof AbstractBannerBlock
            || block instanceof LadderBlock
            || block instanceof VineBlock
            || block instanceof BasePressurePlateBlock
            || block instanceof RedStoneWireBlock
            || block instanceof DiodeBlock
            || block instanceof VegetationBlock;
    }

    /** A curved rail only comes out right if the rails it connects to are already there (P5-04). */
    static boolean isRailCurve(BlockState target) {
        if (!(target.getBlock() instanceof BaseRailBlock rail)) return false;
        return switch (target.getValue(rail.getShapeProperty())) {
            case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> true;
            default -> false;
        };
    }

    /** A top slab can be clicked into place from the block above or from a side neighbor. */
    private boolean hasSlabCarrier(BlockPos pos, Function<BlockPos, BlockState> planned, WorldView world) {
        for (Direction direction : SLAB_CARRIERS) {
            BlockPos neighbor = pos.relative(direction);
            BlockState plannedState = planned.apply(neighbor);
            if (plannedState != null && !isEffectivelyAir(plannedState)) return true;
            if (world.isChunkLoaded(neighbor) && !world.getBlockState(neighbor).canBeReplaced()) return true;
        }
        return false;
    }

    private record Cell(long key, List<BlockTask> tasks, BlockPos center) {
    }

    /** Cell layers along {@code layerAxis}, nearest neighbor by cluster center inside each layer. */
    private List<Cell> orderClusters(Map<Long, List<BlockTask>> cells, BlockPos start) {
        Comparator<Integer> layerOrder = cfg.layerAscending() ? Comparator.naturalOrder() : Comparator.reverseOrder();
        TreeMap<Integer, List<Cell>> layers = new TreeMap<>(layerOrder);
        for (Map.Entry<Long, List<BlockTask>> entry : cells.entrySet()) {
            Cell cell = new Cell(entry.getKey(), entry.getValue(), center(entry.getValue()));
            layers.computeIfAbsent(BlockPos.of(cell.key()).get(cfg.layerAxis()), k -> new ArrayList<>()).add(cell);
        }
        List<Cell> ordered = new ArrayList<>(cells.size());
        BlockPos cursor = start;
        for (List<Cell> layer : layers.values()) {
            layer.sort(Comparator.comparingLong(Cell::key));
            List<Cell> path = nearestNeighbor(layer, Cell::center, cursor);
            ordered.addAll(path);
            cursor = path.getLast().center();
        }
        return ordered;
    }

    /** Priority descending, then layer, then nearest neighbor within each (priority, layer) group. */
    private List<BlockTask> orderTasks(List<BlockTask> tasks, BlockPos start) {
        Comparator<Integer> layerOrder = cfg.layerAscending() ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<BlockTask> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparingInt(BlockTask::priority).reversed()
            .thenComparing(t -> t.pos().get(cfg.layerAxis()), layerOrder)
            .thenComparingLong(t -> t.pos().asLong()));

        List<BlockTask> result = new ArrayList<>(sorted.size());
        BlockPos cursor = start;
        int from = 0;
        while (from < sorted.size()) {
            BlockTask first = sorted.get(from);
            int to = from;
            while (to < sorted.size() && sorted.get(to).priority() == first.priority()
                && sorted.get(to).pos().get(cfg.layerAxis()) == first.pos().get(cfg.layerAxis())) {
                to++;
            }
            List<BlockTask> group = nearestNeighbor(sorted.subList(from, to), BlockTask::pos, cursor);
            result.addAll(group);
            cursor = group.getLast().pos();
            from = to;
        }
        return result;
    }

    /** Greedy nearest-neighbor path from {@code start}; ties keep the input order. O(n²), see Backlog. */
    private static <T> List<T> nearestNeighbor(List<T> items, Function<T, BlockPos> position, BlockPos start) {
        List<T> remaining = new ArrayList<>(items);
        List<T> path = new ArrayList<>(items.size());
        BlockPos cursor = start;
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                double distance = cursor.distSqr(position.apply(remaining.get(i)));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            T next = remaining.remove(best);
            path.add(next);
            cursor = position.apply(next);
        }
        return path;
    }

    /** Cube of edge {@code clusterSize} counted from the snapshot minimum, packed like a BlockPos. */
    private long cellKey(BlockPos pos, BlockPos min) {
        int size = cfg.clusterSize();
        return BlockPos.asLong(
            Math.floorDiv(pos.getX() - min.getX(), size),
            Math.floorDiv(pos.getY() - min.getY(), size),
            Math.floorDiv(pos.getZ() - min.getZ(), size));
    }

    /** Rounded mean of the task positions. */
    private static BlockPos center(List<BlockTask> tasks) {
        long x = 0, y = 0, z = 0;
        for (BlockTask task : tasks) {
            x += task.pos().getX();
            y += task.pos().getY();
            z += task.pos().getZ();
        }
        int n = tasks.size();
        return new BlockPos((int) Math.round((double) x / n), (int) Math.round((double) y / n), (int) Math.round((double) z / n));
    }

    private static Optional<BlockTask> task(BlockPos pos, BlockState target, BlockState current, TaskKind kind, int priority) {
        return Optional.of(new BlockTask(pos, target, current, kind, priority, SkipReason.NONE));
    }

    private static Optional<BlockTask> skip(BlockPos pos, BlockState target, BlockState current, SkipReason reason) {
        return Optional.of(new BlockTask(pos, target, current, TaskKind.SKIP, PRIORITY_SKIP, reason));
    }

    private static Optional<Property<?>> propertyNamed(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) return Optional.of(property);
        }
        return Optional.empty();
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
