package dev.tore.schemaforge.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Log of blocks placed by SchemaForge (P2-03), read back for undo (P5-01) and temporary blocks (P5-02).
 * Each entry is kept in memory and, if a file is set, appended as one JSON line (format in ARCHITECTURE.md §4/§6).
 * After the first write error the file is no longer touched; {@link #writeError()} reports it. Client thread only.
 */
public final class PlacementLog {
    public static final int VERSION = 1;

    public record Entry(BlockPos pos, BlockState block, boolean temp, long t) {
    }

    private final Optional<Path> file;
    private final List<Entry> entries = new ArrayList<>();
    private Optional<IOException> writeError = Optional.empty();

    private PlacementLog(Optional<Path> file) {
        this.file = file;
    }

    public static PlacementLog inMemory() {
        return new PlacementLog(Optional.empty());
    }

    /** Appends to {@code file}; missing parent directories are created on the first write. */
    public static PlacementLog toFile(Path file) {
        return new PlacementLog(Optional.of(file));
    }

    public void append(BlockPos pos, BlockState block, boolean temp) {
        Entry entry = new Entry(pos.immutable(), block, temp, System.currentTimeMillis());
        entries.add(entry);
        if (file.isEmpty() || writeError.isPresent()) return;
        try {
            Path path = file.get();
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, toJson(entry) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            writeError = Optional.of(e);
        }
    }

    /** Entries of this session, oldest first. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** First write error; entries after it are only kept in memory. */
    public Optional<IOException> writeError() {
        return writeError;
    }

    /** One log line; {@code "v"} is the first field (ARCHITECTURE.md §6). */
    static String toJson(Entry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        JsonArray pos = new JsonArray();
        pos.add(entry.pos().getX());
        pos.add(entry.pos().getY());
        pos.add(entry.pos().getZ());
        json.add("pos", pos);
        json.addProperty("block", BlockStateParser.serialize(entry.block()));
        json.addProperty("temp", entry.temp());
        json.addProperty("t", entry.t());
        return json.toString();
    }
}
