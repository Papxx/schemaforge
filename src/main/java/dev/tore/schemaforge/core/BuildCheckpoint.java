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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.tore.schemaforge.SchemaForgeAddon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * One saved build position (P3-04, ARCHITECTURE.md §6): where the run was and which settings it ran with.
 * Written every few seconds and when the module stops, so a disconnect does not lose the progress.
 *
 * @param clusterIndex   1-based cluster the run was working on when it was saved
 * @param planConfigHash fingerprint of the plan settings; a different one means the checkpoint does not fit
 */
public record BuildCheckpoint(int v, int clusterIndex, int placedCount, long startedAt, String planConfigHash) {
    /** Schema version, first field of the file (ARCHITECTURE.md §6). */
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public BuildCheckpoint(int clusterIndex, int placedCount, long startedAt, String planConfigHash) {
        this(VERSION, clusterIndex, placedCount, startedAt, planConfigHash);
    }

    /**
     * Stable fingerprint of everything that shapes the plan. Built from registry names rather than
     * {@link Object#hashCode()}, which is identity-based for blocks and therefore differs between game starts.
     */
    public static String fingerprint(PlanConfig config, String placement) {
        String canonical = placement + '\n' + config.fingerprint();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM; if it is missing the canonical text itself still identifies the config.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Writes the checkpoint, creating the directory; a write error is logged, never thrown. */
    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SchemaForgeAddon.LOG.warn("Could not write checkpoint {}", file, e);
        }
    }

    /** Empty if the file is missing, unreadable, not valid JSON or written by another schema version. */
    public static Optional<BuildCheckpoint> load(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            BuildCheckpoint checkpoint = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), BuildCheckpoint.class);
            if (checkpoint == null || checkpoint.v() != VERSION) return Optional.empty();
            return Optional.of(checkpoint);
        } catch (IOException | JsonSyntaxException e) {
            SchemaForgeAddon.LOG.warn("Could not read checkpoint {}", file, e);
            return Optional.empty();
        }
    }

    /** Removes the checkpoint file; a missing file is not an error. */
    public static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            SchemaForgeAddon.LOG.warn("Could not delete checkpoint {}", file, e);
        }
    }

    /** True if this checkpoint was written for the same placement and the same plan settings (AK2). */
    public boolean fits(PlanConfig config, String placement) {
        return planConfigHash.equals(fingerprint(config, placement));
    }
}
