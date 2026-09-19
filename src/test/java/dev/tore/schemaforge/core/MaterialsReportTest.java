package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-06: the columns of {@code .sf materials} and what counts as missing. */
class MaterialsReportTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyColumnComesFromItsOwnSource() {
        List<MaterialsReport.Row> rows = MaterialsReport.rows(
            Map.of(Items.STONE, 500),
            Map.of(Items.STONE, 120),
            item -> item == Items.STONE ? 64 : 0,
            item -> item == Items.STONE ? 30 : 0);

        MaterialsReport.Row row = rows.getFirst();
        assertEquals(500, row.total());
        assertEquals(120, row.upcoming());
        assertEquals(64, row.inventory());
        assertEquals(30, row.containers());
        assertEquals(26, row.missing(), "120 needed, 64 carried, 30 in chests");
    }

    @Test
    void whatIsCoveredIsNotMissing() {
        MaterialsReport.Row covered = MaterialsReport.rows(
            Map.of(Items.STONE, 500), Map.of(Items.STONE, 64),
            item -> 64, item -> 0).getFirst();
        assertEquals(0, covered.missing());

        MaterialsReport.Row spare = MaterialsReport.rows(
            Map.of(Items.STONE, 500), Map.of(Items.STONE, 10),
            item -> 999, item -> 0).getFirst();
        assertEquals(0, spare.missing(), "never negative");
    }

    @Test
    void itemsOnlyTheUpcomingClustersNeedStillGetARow() {
        List<MaterialsReport.Row> rows = MaterialsReport.rows(
            Map.of(Items.STONE, 10), Map.of(Items.TORCH, 4), item -> 0, item -> 0);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.item() == Items.TORCH));
    }

    @Test
    void missingRowsComeFirst() {
        List<MaterialsReport.Row> rows = MaterialsReport.rows(
            Map.of(Items.STONE, 900, Items.TORCH, 10),
            Map.of(Items.STONE, 100, Items.TORCH, 10),
            item -> item == Items.STONE ? 999 : 0,
            item -> 0);

        assertEquals(Items.TORCH, rows.getFirst().item(), "the one that is short is the one to act on");
    }

    @Test
    void theTableHasAHeaderAndASummary() {
        List<String> lines = MaterialsReport.lines(MaterialsReport.rows(
            Map.of(Items.STONE, 500), Map.of(Items.STONE, 120), item -> 64, item -> 30));

        assertTrue(lines.getFirst().startsWith("item"), lines.getFirst());
        assertTrue(lines.getFirst().contains("total") && lines.getFirst().contains("next")
            && lines.getFirst().contains("inv") && lines.getFirst().contains("chests"), lines.getFirst());
        assertTrue(lines.get(1).contains("stone") && lines.get(1).contains("missing 26"), lines.get(1));
        assertTrue(lines.getLast().contains("1 item type not covered"), lines.getLast());
    }

    @Test
    void aCoveredBuildSaysSo() {
        List<String> lines = MaterialsReport.lines(MaterialsReport.rows(
            Map.of(Items.STONE, 500), Map.of(Items.STONE, 10), item -> 64, item -> 0));
        assertTrue(lines.getLast().contains("covered"), lines.getLast());
    }

    @Test
    void longListsAreCutOff() {
        Map<Item, Integer> many = new java.util.LinkedHashMap<>();
        java.util.List<Item> items = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .limit(MaterialsReport.MAX_ROWS + 5).toList();
        items.forEach(item -> many.put(item, 1));

        List<String> lines = MaterialsReport.lines(MaterialsReport.rows(many, Map.of(), item -> 0, item -> 0));

        // header + MAX_ROWS + "... n more types" + summary
        assertEquals(1 + MaterialsReport.MAX_ROWS + 2, lines.size());
        assertTrue(lines.get(lines.size() - 2).contains("5 more types"), lines.get(lines.size() - 2));
    }

    @Test
    void nothingToBuildSaysSo() {
        assertEquals(List.of("Nothing to build."), MaterialsReport.lines(List.of()));
    }
}
