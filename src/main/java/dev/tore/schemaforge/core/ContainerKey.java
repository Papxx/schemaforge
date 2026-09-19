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

import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * How a container is addressed in the {@link ContainerIndex} (P4-02): which half of a double chest counts,
 * where an ender chest lives, and what the index file for a world is called (ARCHITECTURE.md §6).
 */
public final class ContainerKey {
    /**
     * Ender chests hold the same items wherever they stand, so they all share one entry. The y is below any world,
     * which no real block can have, so the sentinel can never collide with a chest someone actually placed.
     */
    public static final BlockPos ENDER_CHEST = new BlockPos(0, Integer.MIN_VALUE, 0);

    /** Name for a world without a server, e.g. singleplayer. */
    public static final String LOCAL = "local";

    private ContainerKey() {
    }

    /**
     * The half of a double chest both halves agree on, so opening either one updates the same entry (AK2).
     * Lowest x, then y, then z - any total order works as long as it is the same from both sides.
     */
    public static BlockPos canonical(BlockPos a, Optional<BlockPos> other) {
        if (other.isEmpty()) return a.immutable();
        BlockPos b = other.get();
        if (a.getX() != b.getX()) return (a.getX() < b.getX() ? a : b).immutable();
        if (a.getY() != b.getY()) return (a.getY() < b.getY() ? a : b).immutable();
        return (a.getZ() <= b.getZ() ? a : b).immutable();
    }

    /** {@code containers-<serverHash>-<dimension>.json}; the file name never carries the raw server address. */
    public static String fileName(String serverHash, String dimension) {
        return "containers-" + serverHash + "-" + sanitize(dimension) + ".json";
    }

    /** Short, stable fingerprint of a server address; {@link #LOCAL} when there is none. */
    public static String serverHash(Optional<String> address) {
        if (address.isEmpty() || address.get().isBlank()) return LOCAL;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(address.get().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sanitize(String name) {
        // Checked before replacing: whitespace would turn into underscores and no longer look blank.
        if (name.isBlank()) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
