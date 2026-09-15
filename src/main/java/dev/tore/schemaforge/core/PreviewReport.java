package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Text of {@code .sf preview} (P1-05): size, task and cluster counts, material list and warnings.
 * Pure data; colours and chat output live in {@code commands/PreviewCommand}.
 *
 * @param lines one chat line each, in display order
 */
public record PreviewReport(List<Line> lines) {
    /** Material rows shown at most; the rest is folded into one "… more" line. */
    public static final int MAX_MATERIAL_ROWS = 30;
    /** Reasons listed in the unsupported-blocks warning. */
    private static final int MAX_UNSUPPORTED_GROUPS = 4;

    public enum Level { HEADER, INFO, WARNING }

    public record Line(String text, Level level) {
    }

    /**
     * World facts the report is checked against.
     *
     * @param minBuildY      lowest buildable Y (inclusive)
     * @param maxBuildY      highest buildable Y (inclusive)
     * @param renderDistance effective render distance in chunks
     * @param inventoryCount items of a kind the player carries
     */
    public record Environment(int minBuildY, int maxBuildY, int renderDistance, ToIntFunction<Item> inventoryCount) {
    }

    public PreviewReport {
        lines = List.copyOf(lines);
    }

    /**
     * Builds the report. Totals come from the whole snapshot (same numbers as Litematica's material list);
     * "missing" only counts what the planned PLACE/FLUID tasks still need beyond the inventory.
     */
    public static PreviewReport of(SchematicSnapshot snap, List<Cluster> clusters, Environment env) {
        List<Line> lines = new ArrayList<>();
        BlockPos min = snap.min();
        BlockPos max = snap.max();
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        lines.add(header("Preview '%s': %d x %d x %d, %s to %s".formatted(
            snap.placementName(), sizeX, sizeY, sizeZ, format(min), format(max))));

        Map<TaskKind, Integer> kinds = new EnumMap<>(TaskKind.class);
        Map<SkipReason, Integer> skips = new EnumMap<>(SkipReason.class);
        Map<Item, Integer> needed = new HashMap<>();
        Map<String, Integer> unsupported = new HashMap<>();
        for (Cluster cluster : clusters) {
            for (BlockTask task : cluster.tasks()) {
                kinds.merge(task.kind(), 1, Integer::sum);
                if (task.kind() == TaskKind.SKIP) skips.merge(task.skipReason(), 1, Integer::sum);
                if (task.kind() == TaskKind.PLACE || task.kind() == TaskKind.FLUID) {
                    for (MaterialRules.Requirement r : MaterialRules.required(task.target())) needed.merge(r.item(), r.count(), Integer::sum);
                }
                if (task.kind() == TaskKind.PLACE) {
                    PlacementSolver.unsupportedReason(task.target()).ifPresent(reason -> unsupported.merge(reason, 1, Integer::sum));
                }
            }
        }

        long schematicBlocks = snap.blocks().values().stream().filter(s -> !s.isAir()).count();
        lines.add(info("Blocks: %d in schematic, %d to place, %d fluids, %d to break, %d skipped".formatted(schematicBlocks,
            count(kinds, TaskKind.PLACE), count(kinds, TaskKind.FLUID), count(kinds, TaskKind.BREAK), count(kinds, TaskKind.SKIP))));
        lines.add(info("Clusters: " + clusters.size()));
        if (!skips.isEmpty()) {
            lines.add(warning("Skipped: " + skips.entrySet().stream()
                .map(e -> e.getValue() + " " + describe(e.getKey()))
                .collect(Collectors.joining(", "))));
        }

        addMaterials(lines, snap.materialTotals(), needed, env.inventoryCount());
        addWarnings(lines, snap, sizeX, sizeY, sizeZ, env);
        if (!unsupported.isEmpty()) lines.add(warning(unsupportedLine(unsupported)));
        return new PreviewReport(lines);
    }

    /** Largest groups first; at most {@link #MAX_UNSUPPORTED_GROUPS} reasons, so the line stays readable. */
    private static String unsupportedLine(Map<String, Integer> unsupported) {
        int total = unsupported.values().stream().mapToInt(Integer::intValue).sum();
        List<Map.Entry<String, Integer>> groups = unsupported.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .toList();
        String shown = groups.stream().limit(MAX_UNSUPPORTED_GROUPS)
            .map(e -> e.getValue() + " " + e.getKey())
            .collect(Collectors.joining(", "));
        String more = groups.size() > MAX_UNSUPPORTED_GROUPS ? ", …" : "";
        return "%d blocks the printer cannot place yet: %s%s".formatted(total, shown, more);
    }

    private static void addMaterials(List<Line> lines, Map<Item, Integer> totals, Map<Item, Integer> needed, ToIntFunction<Item> inventory) {
        Map<Item, Integer> missing = new HashMap<>();
        needed.forEach((item, n) -> {
            int lacking = n - inventory.applyAsInt(item);
            if (lacking > 0) missing.put(item, lacking);
        });

        int totalItems = totals.values().stream().mapToInt(Integer::intValue).sum();
        int missingItems = missing.values().stream().mapToInt(Integer::intValue).sum();
        lines.add(header("Materials: %d types, %d items, %d missing in inventory".formatted(totals.size(), totalItems, missingItems)));

        List<Map.Entry<Item, Integer>> rows = totals.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Item, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(e -> name(e.getKey())))
            .toList();
        for (Map.Entry<Item, Integer> row : rows.subList(0, Math.min(rows.size(), MAX_MATERIAL_ROWS))) {
            int lacking = missing.getOrDefault(row.getKey(), 0);
            String text = " %d x %s".formatted(row.getValue(), name(row.getKey()));
            lines.add(lacking > 0 ? warning(text + " (missing " + lacking + ")") : info(text));
        }
        if (rows.size() > MAX_MATERIAL_ROWS) lines.add(info(" … %d more types".formatted(rows.size() - MAX_MATERIAL_ROWS)));
    }

    private static void addWarnings(List<Line> lines, SchematicSnapshot snap, int sizeX, int sizeY, int sizeZ, Environment env) {
        if (snap.min().getY() < env.minBuildY()) {
            lines.add(warning("Below world bottom: min Y %d < %d".formatted(snap.min().getY(), env.minBuildY())));
        }
        if (snap.max().getY() > env.maxBuildY()) {
            lines.add(warning("Above build limit: max Y %d > %d".formatted(snap.max().getY(), env.maxBuildY())));
        }
        // Chunks are guaranteed around the player only within the render distance in every direction.
        int reachBlocks = env.renderDistance() * 2 * 16;
        if (Math.max(sizeX, sizeZ) > reachBlocks) {
            lines.add(warning("Wider than render distance (%d chunks, %d blocks across): the printer will have to travel"
                .formatted(env.renderDistance(), reachBlocks)));
        }
        long unknown = (long) sizeX * sizeY * sizeZ - snap.blocks().size();
        if (unknown > 0) {
            lines.add(warning("%d positions not loaded by Litematica yet and not counted above; move closer".formatted(unknown)));
        }
    }

    private static int count(Map<TaskKind, Integer> kinds, TaskKind kind) {
        return kinds.getOrDefault(kind, 0);
    }

    private static String describe(SkipReason reason) {
        return switch (reason) {
            case NONE -> "other";
            case CHUNK_NOT_LOADED -> "in unloaded chunks";
            case NEVER_PLACE -> "never placed (filter)";
            case WORLD_FILTER -> "kept by world filter";
            case MISMATCH_ADDITIVE_ONLY -> "wrong block in world (additive only)";
        };
    }

    private static String name(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String format(BlockPos pos) {
        return String.format(Locale.ROOT, "%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    private static Line header(String text) {
        return new Line(text, Level.HEADER);
    }

    private static Line info(String text) {
        return new Line(text, Level.INFO);
    }

    private static Line warning(String text) {
        return new Line(text, Level.WARNING);
    }
}
