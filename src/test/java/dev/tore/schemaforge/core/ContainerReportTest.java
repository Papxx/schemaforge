package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-02: the lines of {@code .sf containers}. */
class ContainerReportTest {
    private static final Vec3 PLAYER = new Vec3(0, 64, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anEmptyIndexSaysSoAndMentionsTheModuleWhenItIsOff() {
        assertTrue(ContainerReport.lines(List.of(), PLAYER, true).getFirst().contains("empty"));
        assertTrue(ContainerReport.lines(List.of(), PLAYER, false).getFirst().contains("container-restock"));
    }

    @Test
    void containersAreListedNearestFirstWithStaleOnesLast() {
        List<ContainerIndex.Entry> entries = List.of(
            entry(new BlockPos(50, 64, 0), ContainerType.CHEST, false, Map.of(Items.STONE, 10)),
            entry(new BlockPos(3, 64, 0), ContainerType.BARREL, true, Map.of(Items.STONE, 20)),
            entry(new BlockPos(9, 64, 0), ContainerType.CHEST, false, Map.of(Items.STONE, 30)));

        List<String> lines = ContainerReport.lines(entries, PLAYER, true);

        assertEquals("Container index: 3 containers", lines.getFirst());
        assertTrue(lines.get(1).startsWith("chest 9 64 0"), lines.get(1));
        assertTrue(lines.get(2).startsWith("chest 50 64 0"), lines.get(2));
        assertTrue(lines.get(3).startsWith("barrel 3 64 0"), "stale goes last even though it is nearest");
        assertTrue(lines.get(3).contains("(stale)"), lines.get(3));
    }

    @Test
    void anEnderChestIsNamedWithoutItsSentinelPosition() {
        String line = ContainerReport.describe(
            entry(ContainerKey.ENDER_CHEST, ContainerType.ENDER_CHEST, false, Map.of(Items.STONE, 64)), PLAYER);

        assertEquals("ender chest: 64x stone", line);
    }

    @Test
    void onlyTheFourBiggestItemKindsAreNamedPerContainer() {
        Map<Item, Integer> items = new LinkedHashMap<>();
        items.put(Items.TORCH, 1);
        items.put(Items.STONE, 64);
        items.put(Items.OAK_PLANKS, 32);
        items.put(Items.RAIL, 16);
        items.put(Items.DIRT, 8);

        String line = ContainerReport.describe(entry(new BlockPos(1, 64, 0), ContainerType.CHEST, false, items), PLAYER);

        assertTrue(line.contains("64x stone, 32x oak_planks, 16x rail, 8x dirt +1 more"), line);
    }

    @Test
    void anEmptyContainerIsMarkedAsEmpty() {
        String line = ContainerReport.describe(entry(new BlockPos(1, 64, 0), ContainerType.CHEST, false, Map.of()), PLAYER);
        assertTrue(line.endsWith(": empty"), line);
    }

    @Test
    void longListsAreCutOff() {
        List<ContainerIndex.Entry> many = java.util.stream.IntStream.range(0, ContainerReport.MAX_CONTAINERS + 5)
            .mapToObj(i -> entry(new BlockPos(i, 64, 0), ContainerType.CHEST, false, Map.of(Items.STONE, 1)))
            .toList();

        List<String> lines = ContainerReport.lines(many, PLAYER, true);

        assertEquals(1 + ContainerReport.MAX_CONTAINERS + 1, lines.size());
        assertEquals("... 5 more", lines.getLast());
    }

    private static ContainerIndex.Entry entry(BlockPos pos, ContainerType type, boolean stale,
                                              Map<Item, Integer> items) {
        return new ContainerIndex.Entry(pos, type, 1L, items, stale);
    }
}
