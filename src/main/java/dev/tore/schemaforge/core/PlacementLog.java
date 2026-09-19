/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.tore.schemaforge.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.tore.schemaforge.SchemaForgeAddon;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
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

    /**
     * Reads a log file back for undo (P5-01), oldest first. Lines that are broken, from another schema version or
     * name a block this game does not know are skipped: one bad line must not cost the whole log.
     */
    public static List<Entry> readFrom(Path file, HolderLookup<Block> blocks) {
        if (!Files.isRegularFile(file)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                parse(line, blocks).ifPresent(entries::add);
            }
        } catch (IOException e) {
            SchemaForgeAddon.LOG.warn("Could not read placement log {}", file, e);
        }
        return List.copyOf(entries);
    }

    private static Optional<Entry> parse(String line, HolderLookup<Block> blocks) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            if (json.get("v").getAsInt() != VERSION) return Optional.empty();
            JsonArray pos = json.getAsJsonArray("pos");
            BlockPos at = new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt());
            BlockState state = BlockStateParser.parseForBlock(blocks, json.get("block").getAsString(), false).blockState();
            return Optional.of(new Entry(at, state, json.get("temp").getAsBoolean(), json.get("t").getAsLong()));
        } catch (CommandSyntaxException | RuntimeException e) {
            SchemaForgeAddon.LOG.warn("Skipping unreadable placement log line: {}", line);
            return Optional.empty();
        }
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
