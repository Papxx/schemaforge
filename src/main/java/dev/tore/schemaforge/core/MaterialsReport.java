package dev.tore.schemaforge.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Text of {@code .sf materials} (P4-06): per item what the whole schematic needs, what the next clusters need,
 * what the inventory holds and what the known containers hold. Plain lines, no chat and no Minecraft rendering.
 */
public final class MaterialsReport {
    /** Items listed before the rest is summed up. */
    public static final int MAX_ROWS = 30;

    /** One line of the table; {@code missing} is what neither inventory nor containers can cover. */
    public record Row(Item item, int total, int upcoming, int inventory, int containers) {
        /** What the upcoming clusters need beyond the inventory and every known container. */
        public int missing() {
            return Math.max(0, upcoming - inventory - containers);
        }
    }

    private MaterialsReport() {
    }

    /**
     * @param total      demand of the whole schematic, as {@code .sf preview} shows it
     * @param upcoming   demand of the next clusters, as the restock uses it
     * @param inventory  how many of an item the player carries
     * @param containers how many the container index knows about
     */
    public static List<Row> rows(Map<Item, Integer> total, Map<Item, Integer> upcoming,
                                 ToIntFunction<Item> inventory, ToIntFunction<Item> containers) {
        Set<Item> items = new LinkedHashSet<>(total.keySet());
        items.addAll(upcoming.keySet());
        List<Row> rows = new ArrayList<>(items.size());
        for (Item item : items) {
            rows.add(new Row(item, total.getOrDefault(item, 0), upcoming.getOrDefault(item, 0),
                inventory.applyAsInt(item), containers.applyAsInt(item)));
        }
        // What is missing first, then the biggest demand: the rows that need a decision come first.
        rows.sort(Comparator.comparingInt(Row::missing).reversed()
            .thenComparing(Comparator.comparingInt(Row::total).reversed())
            .thenComparing(row -> BuiltInRegistries.ITEM.getKey(row.item()).toString()));
        return List.copyOf(rows);
    }

    /** The table as lines, header first; at most {@link #MAX_ROWS} items plus a summary line. */
    public static List<String> lines(List<Row> rows) {
        if (rows.isEmpty()) return List.of("Nothing to build.");

        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%-24s %8s %8s %8s %8s", "item", "total", "next", "inv", "chests"));
        for (Row row : rows.stream().limit(MAX_ROWS).toList()) {
            lines.add(String.format(Locale.ROOT, "%-24s %8d %8d %8d %8d%s",
                name(row.item()), row.total(), row.upcoming(), row.inventory(), row.containers(),
                row.missing() > 0 ? "  missing " + row.missing() : ""));
        }
        int rest = rows.size() - MAX_ROWS;
        if (rest > 0) lines.add("... " + rest + " more types");
        int missingTypes = (int) rows.stream().filter(row -> row.missing() > 0).count();
        lines.add(missingTypes == 0
            ? "Everything the next clusters need is covered."
            : missingTypes + " item type" + (missingTypes == 1 ? "" : "s") + " not covered by inventory or chests.");
        return List.copyOf(lines);
    }

    private static String name(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return path.length() <= 24 ? path : path.substring(0, 23) + "…";
    }
}
