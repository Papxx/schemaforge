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

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lines of the build progress HUD (P3-05), without Minecraft rendering: percent done, blocks per minute, ETA,
 * state and the three biggest shortages. Drawing them is {@code hud/BuildProgressHud}.
 */
public final class ProgressReport {
    /** Shortages listed in the HUD; more are summed up in the last line. */
    public static final int TOP_SHORTAGES = 3;

    private ProgressReport() {
    }

    /**
     * @param status    the running or last finished run, empty if the module never ran
     * @param shortages missing items, biggest first is not required - this sorts them
     * @param running   false marks a finished or stopped run
     */
    public static List<String> lines(Optional<BuildSession.Status> status, List<MaterialManager.Shortage> shortages,
                                     boolean running) {
        if (status.isEmpty()) return List.of("SchemaForge: idle");

        BuildSession.Status s = status.get();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%s %s %d%%", state(s, running), s.placement(), percent(s)));
        lines.add(String.format(Locale.ROOT, "%d placed, %d left - %.0f/min - ETA %s",
            s.placed(), s.remaining(), s.blocksPerMinute(), eta(s)));
        if (s.mismatched() > 0) lines.add(s.mismatched() + " mismatched");
        addShortages(lines, shortages);
        return List.copyOf(lines);
    }

    /** Placed out of everything this run still knows about; 100 only when nothing is left. */
    public static int percent(BuildSession.Status status) {
        int total = status.placed() + status.remaining();
        if (total == 0) return 100;
        return (int) Math.floor(status.placed() * 100.0 / total);
    }

    /** Remaining blocks at the current rate, or {@code ?} while there is no rate yet. */
    public static String eta(BuildSession.Status status) {
        if (status.remaining() == 0) return "-";
        if (status.blocksPerMinute() <= 0) return "?";
        long minutes = Math.round(status.remaining() / status.blocksPerMinute());
        if (minutes < 60) return minutes + "m";
        return String.format(Locale.ROOT, "%dh%02dm", minutes / 60, minutes % 60);
    }

    private static String state(BuildSession.Status status, boolean running) {
        if (running) return status.state().toString();
        return status.state() == BuildSession.State.DONE ? "DONE" : "STOPPED";
    }

    private static void addShortages(List<String> lines, List<MaterialManager.Shortage> shortages) {
        if (shortages.isEmpty()) return;
        List<MaterialManager.Shortage> sorted = shortages.stream()
            .sorted((a, b) -> Integer.compare(b.missing(), a.missing()))
            .toList();
        String top = sorted.stream()
            .limit(TOP_SHORTAGES)
            .map(s -> s.missing() + "x " + BuiltInRegistries.ITEM.getKey(s.item()).getPath())
            .reduce((a, b) -> a + ", " + b)
            .orElseThrow();
        int rest = sorted.size() - TOP_SHORTAGES;
        lines.add("missing: " + top + (rest > 0 ? " +" + rest + " more" : ""));
    }
}
