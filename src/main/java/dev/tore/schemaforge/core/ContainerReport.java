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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Text of {@code .sf containers} (P4-02) as plain lines; chat colours live in {@code commands/ContainersCommand}. */
public final class ContainerReport {
    /** Containers listed before the rest is summed up. */
    public static final int MAX_CONTAINERS = 20;

    /** Item kinds named per container. */
    public static final int ITEMS_PER_CONTAINER = 4;

    private ContainerReport() {
    }

    /**
     * @param from    where the player stands; entries are listed nearest first
     * @param running false adds the hint that the module has to be on for learning to happen
     */
    public static List<String> lines(List<ContainerIndex.Entry> entries, Vec3 from, boolean running) {
        List<String> lines = new ArrayList<>();
        if (entries.isEmpty()) {
            lines.add("Container index: empty. Open a container to learn it" + (running ? "." : ", with container-restock on."));
            return List.copyOf(lines);
        }

        lines.add("Container index: " + entries.size() + " container" + (entries.size() == 1 ? "" : "s")
            + (running ? "" : " (container-restock is off, nothing is learned right now)"));
        List<ContainerIndex.Entry> sorted = entries.stream()
            .sorted(Comparator.comparing(ContainerIndex.Entry::stale)
                .thenComparingDouble(entry -> distance(entry, from)))
            .toList();
        for (ContainerIndex.Entry entry : sorted.stream().limit(MAX_CONTAINERS).toList()) {
            lines.add(describe(entry, from));
        }
        int rest = sorted.size() - MAX_CONTAINERS;
        if (rest > 0) lines.add("... " + rest + " more");
        return List.copyOf(lines);
    }

    /** One container: where it is, how far, what it holds. */
    public static String describe(ContainerIndex.Entry entry, Vec3 from) {
        String where = entry.pos().equals(ContainerKey.ENDER_CHEST)
            ? "ender chest"
            : String.format(Locale.ROOT, "%s %d %d %d (%.0fm)", name(entry.type()),
                entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(), Math.sqrt(distance(entry, from)));
        return where + (entry.stale() ? " (stale)" : "") + ": " + items(entry.items());
    }

    private static String items(Map<Item, Integer> items) {
        if (items.isEmpty()) return "empty";
        List<Map.Entry<Item, Integer>> sorted = items.entrySet().stream()
            .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
            .toList();
        String named = sorted.stream()
            .limit(ITEMS_PER_CONTAINER)
            .map(e -> e.getValue() + "x " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath())
            .reduce((a, b) -> a + ", " + b)
            .orElseThrow();
        int rest = sorted.size() - ITEMS_PER_CONTAINER;
        return named + (rest > 0 ? " +" + rest + " more" : "");
    }

    /** Squared distance; an ender chest is always right here, wherever "here" is. */
    private static double distance(ContainerIndex.Entry entry, Vec3 from) {
        BlockPos pos = entry.pos();
        if (pos.equals(ContainerKey.ENDER_CHEST)) return 0;
        return Vec3.atCenterOf(pos).distanceToSqr(from);
    }

    private static String name(ContainerType type) {
        return type.name().toLowerCase().replace('_', ' ');
    }
}
