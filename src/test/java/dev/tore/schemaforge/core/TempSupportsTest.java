package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.WorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P5-02: where a temporary support may go and which one is taken. The place/remove cycle is in PrinterTest. */
class TempSupportsTest {
    private static final BlockPos AT = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reservedAreSchematicPositionsThatAreNotKnownToBeAir() {
        Long2ObjectMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        blocks.put(new BlockPos(0, 0, 0).asLong(), Blocks.STONE.defaultBlockState());
        blocks.put(new BlockPos(1, 0, 0).asLong(), Blocks.AIR.defaultBlockState());
        SchematicSnapshot snap = new SchematicSnapshot("t", new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), blocks, Map.of());
        Predicate<BlockPos> reserved = TempSupports.reservedBy(snap);

        assertTrue(reserved.test(new BlockPos(0, 0, 0)), "the schematic wants stone there");
        assertFalse(reserved.test(new BlockPos(1, 0, 0)), "the schematic wants air there");
        assertTrue(reserved.test(new BlockPos(2, 0, 0)), "unknown inside the box: may be wanted");
        assertFalse(reserved.test(new BlockPos(3, 0, 0)), "outside the box");
        assertFalse(reserved.test(new BlockPos(0, 1, 0)), "above the box");
    }

    @Test
    void onlyTargetsThatStandOnTheirOwnGetASupport() {
        TempSupports supports = supports(_ -> false);
        World world = new World();
        assertTrue(supports.allowedFor(Blocks.STONE.defaultBlockState(), AT, world));
        assertTrue(supports.allowedFor(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), AT, world));
        assertFalse(supports.allowedFor(Blocks.TORCH.defaultBlockState(), AT, world), "a torch would drop with its support");
        assertFalse(supports.allowedFor(Blocks.CARPET.white().defaultBlockState(), AT, world));
        assertFalse(supports.allowedFor(Blocks.OAK_DOOR.defaultBlockState(), AT, world));
        assertFalse(supports.allowedFor(Blocks.LADDER.defaultBlockState(), AT, world));
    }

    @Test
    void theSupportPositionMustBeFreeLoadedAndNotReserved() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        World world = new World();
        assertFalse(supports(AT::equals).allowedFor(stone, AT, world), "reserved by the schematic");

        world.states.put(AT, Blocks.COBBLESTONE.defaultBlockState());
        assertFalse(supports(_ -> false).allowedFor(stone, AT, world), "occupied");

        world.states.put(AT, Blocks.SHORT_GRASS.defaultBlockState());
        assertTrue(supports(_ -> false).allowedFor(stone, AT, world), "replaceable is fine");

        world.loaded = false;
        assertFalse(supports(_ -> false).allowedFor(stone, AT, world), "not loaded");
    }

    @Test
    void theFirstWhitelistedBlockInTheInventoryIsTaken() {
        TempSupports supports = supports(_ -> false);
        assertEquals(Optional.empty(), supports.pick(new FakeInventory()));
        assertEquals(Optional.of(Blocks.COBBLESTONE), supports.pick(new FakeInventory().put(3, Items.COBBLESTONE, 5)));
        assertEquals(Optional.of(Blocks.DIRT),
            supports.pick(new FakeInventory().put(3, Items.COBBLESTONE, 5).put(4, Items.DIRT, 1)));
    }

    private static TempSupports supports(Predicate<BlockPos> reserved) {
        return new TempSupports(List.of(Blocks.DIRT, Blocks.COBBLESTONE), reserved, _ -> true, _ -> {
        });
    }

    private static final class World implements WorldView {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        boolean loaded = true;

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isChunkLoaded(BlockPos pos) {
            return loaded;
        }

        @Override
        public Optional<ContainerType> containerAt(BlockPos pos) {
            return Optional.empty();
        }
    }
}
