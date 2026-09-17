package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** P2-05 AK1 (hotbar choice only in allowed slots), AK2 (shortage event with item and amount), plus slot parsing. */
class MaterialManagerTest {
    private static final HotbarSlots DEFAULT = HotbarSlots.parse("2-8");

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** AK1: slot 1 (index 0) is not allowed by "2-8", slot 4 (index 3) is. */
    @Test
    void picksAllowedSlotOverDisallowedOne() {
        MaterialManager materials = new MaterialManager(() -> DEFAULT, _ -> { });
        FakeInventory inv = new FakeInventory().put(0, Items.STONE, 64).put(3, Items.STONE, 64);
        assertEquals(new MaterialManager.Selection.Ready(3), materials.select(Items.STONE, inv));
    }

    @Test
    void prefersSelectedSlotWhenAllowed() {
        MaterialManager materials = new MaterialManager(() -> DEFAULT, _ -> { });
        FakeInventory inv = new FakeInventory().put(3, Items.STONE, 64).put(6, Items.STONE, 64);
        inv.selected = 6;
        assertEquals(new MaterialManager.Selection.Ready(6), materials.select(Items.STONE, inv));
        inv.selected = 0;
        assertEquals(new MaterialManager.Selection.Ready(3), materials.select(Items.STONE, inv));
    }

    @Test
    void swapsFromInventoryOrDisallowedSlotIntoEmptyAllowedSlot() {
        MaterialManager materials = new MaterialManager(() -> DEFAULT, _ -> { });
        FakeInventory inv = new FakeInventory().put(0, Items.STONE, 5).put(20, Items.STONE, 64);
        for (int i = 1; i <= 7; i++) if (i != 5) inv.put(i, Items.DIRT, 1);
        // Largest stack wins; the only empty allowed slot is index 5.
        assertEquals(new MaterialManager.Selection.Swap(20, 5), materials.select(Items.STONE, inv));
    }

    @Test
    void swapTargetAvoidsSlotsHoldingDemandedItems() {
        MaterialManager materials = new MaterialManager(() -> DEFAULT, _ -> { });
        materials.startCluster(cluster(Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState()));
        FakeInventory inv = new FakeInventory().put(30, Items.STONE, 64);
        for (int i = 1; i <= 7; i++) inv.put(i, Items.OAK_PLANKS, 64);
        inv.put(4, Items.DIRT, 64);
        assertEquals(new MaterialManager.Selection.Swap(30, 4), materials.select(Items.STONE, inv));

        inv.put(4, Items.OAK_PLANKS, 64);
        assertEquals(new MaterialManager.Selection.Swap(30, 1), materials.select(Items.STONE, inv));
    }

    @Test
    void changedSettingAppliesImmediately() {
        AtomicReference<HotbarSlots> setting = new AtomicReference<>(DEFAULT);
        MaterialManager materials = new MaterialManager(setting::get, _ -> { });
        FakeInventory inv = new FakeInventory().put(0, Items.STONE, 64);
        assertEquals(new MaterialManager.Selection.Swap(0, 1), materials.select(Items.STONE, inv));
        setting.set(HotbarSlots.parse("1-9"));
        assertEquals(new MaterialManager.Selection.Ready(0), materials.select(Items.STONE, inv));
    }

    /** AK2: missing item → one event with item and missing amount (demand minus inventory). */
    @Test
    void missingItemFiresEventWithAmountOncePerVisit() {
        List<MaterialManager.Shortage> events = new ArrayList<>();
        MaterialManager materials = new MaterialManager(() -> DEFAULT, events::add);
        BlockState stone = Blocks.STONE.defaultBlockState();
        materials.startCluster(cluster(stone, stone, stone));
        FakeInventory inv = new FakeInventory();

        MaterialManager.Shortage expected = new MaterialManager.Shortage(Items.STONE, 3);
        assertEquals(new MaterialManager.Selection.Missing(expected), materials.select(Items.STONE, inv));
        materials.select(Items.STONE, inv);
        assertEquals(List.of(expected), events);

        materials.startCluster(cluster(stone));
        materials.select(Items.STONE, inv);
        assertEquals(List.of(expected, new MaterialManager.Shortage(Items.STONE, 1)), events);
    }

    @Test
    void demandAndShortagesOfCluster() {
        List<MaterialManager.Shortage> events = new ArrayList<>();
        MaterialManager materials = new MaterialManager(() -> DEFAULT, events::add);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        Cluster c = cluster(stone, stone, stone, planks, planks);
        materials.startCluster(c);
        assertEquals(Map.of(Items.STONE, 3, Items.OAK_PLANKS, 2), materials.demand());

        FakeInventory inv = new FakeInventory().put(10, Items.STONE, 1).put(11, Items.OAK_PLANKS, 64);
        List<MaterialManager.Shortage> shortages = materials.checkShortages(inv);
        assertEquals(List.of(new MaterialManager.Shortage(Items.STONE, 2)), shortages);
        assertEquals(shortages, events);
    }

    @Test
    void hotbarSlotsParseKeyNumbers() {
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), HotbarSlots.parse("2-8").indices());
        assertEquals(Set.of(0, 2, 4, 5, 6), HotbarSlots.parse(" 1, 3,5-7 ").indices());
        assertThrows(IllegalArgumentException.class, () -> HotbarSlots.parse(""));
        assertThrows(IllegalArgumentException.class, () -> HotbarSlots.parse("0-3"));
        assertThrows(IllegalArgumentException.class, () -> HotbarSlots.parse("8-2"));
        assertThrows(IllegalArgumentException.class, () -> HotbarSlots.parse("a"));
    }

    /** One PLACE task per state, on a line along x. */
    private static Cluster cluster(BlockState... states) {
        List<BlockTask> tasks = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            tasks.add(new BlockTask(new BlockPos(i, 64, 0), states[i], Blocks.AIR.defaultBlockState(), TaskKind.PLACE,
                WorkPlanner.PRIORITY_FULL_BLOCK, SkipReason.NONE));
        }
        tasks.add(new BlockTask(new BlockPos(0, 70, 0), Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
            TaskKind.SKIP, WorkPlanner.PRIORITY_SKIP, SkipReason.MISMATCH_ADDITIVE_ONLY));
        return new Cluster(0, BlockPos.ZERO, tasks);
    }
}
