package dev.tore.schemaforge.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tore.schemaforge.SchemaForgeAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Learned container contents plus persistence (P4-01, file layout in ARCHITECTURE.md §6).
 * Only remembers what was seen; opening containers and filling this index is P4-02/P4-03.
 * Not thread-safe: client thread only.
 */
public final class ContainerIndex {
    /**
     * @param lastSeenEpochMs when the contents were last looked at
     * @param stale           true once the entry is too old to be trusted; such sources are tried last
     */
    public record Entry(BlockPos pos, ContainerType type, long lastSeenEpochMs, Map<Item, Integer> items, boolean stale) {
        public Entry {
            items = Map.copyOf(items);
        }

        public int count(Item item) {
            return items.getOrDefault(item, 0);
        }
    }

    /** Schema version, first field of the file (ARCHITECTURE.md §6). */
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Insertion ordered, so a saved file keeps a stable order between runs. */
    private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
    private final LongSupplier clockMs;

    public ContainerIndex() {
        this(System::currentTimeMillis);
    }

    /** @param clockMs wall clock; a test can hand in its own to age entries without waiting */
    public ContainerIndex(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    /** Records what a container holds right now; an entry for the same position is replaced and is no longer stale. */
    public void learn(BlockPos pos, ContainerType type, Map<Item, Integer> items) {
        entries.put(pos.immutable(), new Entry(pos.immutable(), type, clockMs.getAsLong(), items, false));
    }

    /** Every container known to hold {@code item}, nearest first, stale ones last. */
    public List<Entry> sourcesFor(Item item, Vec3 from) {
        return entries.values().stream()
            .filter(entry -> entry.count(item) > 0)
            .sorted(Comparator.comparing(Entry::stale)
                .thenComparingDouble(entry -> Vec3.atCenterOf(entry.pos()).distanceToSqr(from)))
            .toList();
    }

    /** Marks everything last seen longer than {@code olderThan} ago as stale. */
    public void markStale(Duration olderThan) {
        long cutoff = clockMs.getAsLong() - olderThan.toMillis();
        entries.replaceAll((_, entry) -> entry.lastSeenEpochMs() <= cutoff && !entry.stale()
            ? new Entry(entry.pos(), entry.type(), entry.lastSeenEpochMs(), entry.items(), true)
            : entry);
    }

    /** All entries in the order they were learned. */
    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public Optional<Entry> at(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos.immutable()));
    }

    /** Drops one container, e.g. after finding it gone or empty (P4-04). */
    public boolean forget(BlockPos pos) {
        return entries.remove(pos.immutable()) != null;
    }

    public int size() {
        return entries.size();
    }

    /** Writes the index, creating the directory; a write error is logged, never thrown. */
    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(toDto()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SchemaForgeAddon.LOG.warn("Could not write container index {}", file, e);
        }
    }

    /** An empty index if the file is missing, unreadable, broken or written by another schema version. */
    public static ContainerIndex load(Path file) {
        return load(file, System::currentTimeMillis);
    }

    public static ContainerIndex load(Path file, LongSupplier clockMs) {
        ContainerIndex index = new ContainerIndex(clockMs);
        if (!Files.isRegularFile(file)) return index;
        try {
            FileDto dto = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileDto.class);
            if (dto == null || dto.v != VERSION || dto.containers == null) {
                SchemaForgeAddon.LOG.warn("Ignoring container index {} (version mismatch or empty)", file);
                return index;
            }
            for (EntryDto entry : dto.containers) index.add(entry);
            // RuntimeException also covers Gson's own JsonSyntaxException for a file that is not this format at all.
        } catch (IOException | RuntimeException e) {
            SchemaForgeAddon.LOG.warn("Could not read container index {}", file, e);
        }
        return index;
    }

    /** Skips an entry whose position, type or every item is unknown, rather than failing the whole file. */
    private void add(EntryDto dto) {
        if (dto == null || dto.pos == null || dto.pos.length != 3 || dto.type == null) return;
        ContainerType type;
        try {
            type = ContainerType.valueOf(dto.type);
        } catch (IllegalArgumentException _) {
            SchemaForgeAddon.LOG.warn("Unknown container type '{}' in the index", dto.type);
            return;
        }
        Map<Item, Integer> items = new LinkedHashMap<>();
        if (dto.items != null) {
            dto.items.forEach((id, count) -> item(id).ifPresent(i -> items.put(i, count)));
        }
        BlockPos pos = new BlockPos(dto.pos[0], dto.pos[1], dto.pos[2]);
        entries.put(pos, new Entry(pos, type, dto.lastSeen, items, dto.stale));
    }

    /** Air stands for "unknown id" here: that is what the registry returns for anything it does not know. */
    private static Optional<Item> item(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return Optional.empty();
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (item == Items.AIR && !"minecraft:air".equals(key.toString())) {
            SchemaForgeAddon.LOG.warn("Unknown item '{}' in the container index", id);
            return Optional.empty();
        }
        return Optional.of(item);
    }

    private FileDto toDto() {
        FileDto dto = new FileDto();
        dto.v = VERSION;
        dto.containers = new ArrayList<>();
        for (Entry entry : entries.values()) {
            EntryDto out = new EntryDto();
            out.pos = new int[]{entry.pos().getX(), entry.pos().getY(), entry.pos().getZ()};
            out.type = entry.type().name();
            out.lastSeen = entry.lastSeenEpochMs();
            out.stale = entry.stale();
            out.items = new LinkedHashMap<>();
            entry.items().forEach((item, count) -> out.items.put(BuiltInRegistries.ITEM.getKey(item).toString(), count));
            dto.containers.add(out);
        }
        return dto;
    }

    /** Wire format; field names are the file's keys, so renaming them changes the format. */
    private static final class FileDto {
        int v;
        List<EntryDto> containers;
    }

    private static final class EntryDto {
        int[] pos;
        String type;
        long lastSeen;
        boolean stale;
        Map<String, Integer> items;
    }
}
