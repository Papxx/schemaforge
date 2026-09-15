package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.WorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1-05: line budget, material folding, missing counts and warnings of the preview text. */
class PreviewReportTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final PreviewReport.Environment ENV = new PreviewReport.Environment(-64, 319, 12, _ -> 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void schematicWithFifteenMaterialsFitsInTwentyFiveLines() {
        SchematicSnapshot snap = row(distinctBlocks(15));
        PreviewReport report = report(snap, new FakeWorld(), ENV);

        assertTrue(report.lines().size() <= 25, "lines: " + report.lines().size());
        assertEquals(15, materialRows(report).size());
        assertTrue(text(report).contains("Materials: 15 types, 15 items, 15 missing in inventory"), text(report));
        assertTrue(report.lines().stream().noneMatch(l -> l.text().startsWith(" …")));
    }

    @Test
    void moreThanThirtyMaterialsShowTopThirtyAndEllipsis() {
        List<Block> blocks = distinctBlocks(40);
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) states.put(ORIGIN.east(i), blocks.get(i).defaultBlockState());
        // Stone appears three times, so it must be the first row.
        states.put(ORIGIN.north(), Blocks.STONE.defaultBlockState());
        states.put(ORIGIN.north(2), Blocks.STONE.defaultBlockState());
        states.put(ORIGIN.north(3), Blocks.STONE.defaultBlockState());
        PreviewReport report = report(snapshot(states), new FakeWorld(), ENV);

        List<PreviewReport.Line> rows = materialRows(report);
        assertEquals(PreviewReport.MAX_MATERIAL_ROWS, rows.size());
        assertTrue(rows.getFirst().text().startsWith(" 3 x stone"), rows.getFirst().text());
        assertTrue(text(report).contains(" … 11 more types"), text(report));
    }

    @Test
    void missingCountsOnlyWhatTasksStillNeedBeyondInventory() {
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (int i = 0; i < 10; i++) states.put(ORIGIN.east(i), Blocks.STONE.defaultBlockState());
        states.put(ORIGIN.south(), Blocks.OAK_PLANKS.defaultBlockState());
        FakeWorld world = new FakeWorld();
        for (int i = 0; i < 3; i++) world.set(ORIGIN.east(i), Blocks.STONE.defaultBlockState());
        PreviewReport.Environment env = new PreviewReport.Environment(-64, 319, 12, item -> item == Items.STONE ? 5 : 1);

        PreviewReport report = report(snapshot(states), world, env);

        // 7 stone still to place, 5 carried → 2 missing; the one plank is carried.
        assertTrue(text(report).contains(" 10 x stone (missing 2)"), text(report));
        assertTrue(text(report).contains(" 1 x oak_planks\n"), text(report));
        assertTrue(text(report).contains("Blocks: 11 in schematic, 8 to place"), text(report));
        assertTrue(text(report).contains("11 items, 2 missing in inventory"), text(report));
    }

    @Test
    void warnsAboutBuildHeightRenderDistanceAndUnloadedPositions() {
        Map<BlockPos, BlockState> states = new HashMap<>();
        states.put(new BlockPos(0, -70, 0), Blocks.STONE.defaultBlockState());
        states.put(new BlockPos(500, 330, 0), Blocks.STONE.defaultBlockState());
        PreviewReport report = report(snapshot(states), new FakeWorld(), ENV);

        List<String> warnings = report.lines().stream()
            .filter(l -> l.level() == PreviewReport.Level.WARNING).map(PreviewReport.Line::text).toList();
        assertTrue(warnings.contains("Below world bottom: min Y -70 < -64"), warnings.toString());
        assertTrue(warnings.contains("Above build limit: max Y 330 > 319"), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Wider than render distance (12 chunks, 384 blocks across)")), warnings.toString());
        long unknown = 501L * 401 - 2;
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith(unknown + " positions not loaded")), warnings.toString());
    }

    @Test
    void compactPlacementInsideLimitsHasNoWarnings() {
        PreviewReport report = report(row(distinctBlocks(3)), new FakeWorld().set(ORIGIN.east(50), Blocks.DIRT.defaultBlockState()), ENV);
        assertTrue(report.lines().stream()
            .filter(l -> !materialRows(report).contains(l))
            .noneMatch(l -> l.level() == PreviewReport.Level.WARNING), text(report));
    }

    @Test
    void skippedTasksAreBrokenDownByReason() {
        Map<BlockPos, BlockState> states = Map.of(
            ORIGIN, Blocks.STONE.defaultBlockState(),
            ORIGIN.east(), Blocks.TNT.defaultBlockState());
        FakeWorld world = new FakeWorld().set(ORIGIN, Blocks.DIRT.defaultBlockState());

        PreviewReport report = report(snapshot(states), world, ENV);

        assertTrue(text(report).contains("0 to place, 0 fluids, 0 to break, 2 skipped"), text(report));
        assertTrue(text(report).contains("Skipped: 1 never placed (filter), 1 wrong block in world (additive only)"), text(report));
    }

    private static PreviewReport report(SchematicSnapshot snap, WorldView world, PreviewReport.Environment env) {
        return PreviewReport.of(snap, new WorkPlanner(PlanConfig.defaults()).plan(snap, world, ORIGIN), env);
    }

    private static List<PreviewReport.Line> materialRows(PreviewReport report) {
        return report.lines().stream().filter(l -> l.text().matches(" \\d+ x .*")).toList();
    }

    private static String text(PreviewReport report) {
        return String.join("\n", report.lines().stream().map(PreviewReport.Line::text).toList()) + "\n";
    }

    /** Full-cube blocks whose items are all different, so each adds one material type. */
    private static List<Block> distinctBlocks(int n) {
        List<Block> result = new ArrayList<>();
        Set<Object> items = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (result.size() == n) break;
            if (state.isAir() || block == Blocks.STONE || block == Blocks.TNT) continue;
            if (!state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
            List<MaterialRules.Requirement> required = MaterialRules.required(state);
            if (required.size() == 1 && required.getFirst().count() == 1 && items.add(required.getFirst().item())) result.add(block);
        }
        assertEquals(n, result.size());
        return result;
    }

    private static SchematicSnapshot row(List<Block> blocks) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) states.put(ORIGIN.east(i), blocks.get(i).defaultBlockState());
        return snapshot(states);
    }

    /** Snapshot over the bounding box of {@code states}; positions in the box that are not given count as unloaded. */
    private static SchematicSnapshot snapshot(Map<BlockPos, BlockState> states) {
        Long2ObjectMap<BlockState> map = new Long2ObjectOpenHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : states.entrySet()) {
            BlockPos p = e.getKey();
            map.put(p.asLong(), e.getValue());
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new SchematicSnapshot("test", new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), map,
            MaterialRules.totals(map.values()));
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
