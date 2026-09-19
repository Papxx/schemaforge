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
package dev.tore.schemaforge.commands;

import dev.tore.schemaforge.compat.LitematicaAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves the optional placement argument of {@code .sf preview} and {@code .sf start} (P2-07).
 * Shared so both commands report the same way; no packets, no side effects.
 */
public final class PlacementChoice {
    public sealed interface Result permits Result.Chosen, Result.Error {
        record Chosen(String name, List<String> allNames) implements Result {
            /** How often this name is loaded; only the first placement with it can be addressed. */
            public long duplicates() {
                return allNames.stream().filter(name::equals).count();
            }
        }

        record Error(String message) implements Result {
        }
    }

    private PlacementChoice() {
    }

    /**
     * @param requested name typed by the user, empty for "the only loaded placement"
     * @param command   name shown in the error message, for example {@code .sf start}
     */
    public static Result resolve(Optional<String> requested, String command) {
        if (!LitematicaAdapter.isPresent()) return new Result.Error("Litematica missing.");

        List<String> names = LitematicaAdapter.placementNames();
        if (names.isEmpty()) {
            return new Result.Error("No Litematica placements loaded (or Litematica incompatible, see .sf doctor).");
        }
        if (requested.isPresent()) {
            String name = requested.get();
            if (!names.contains(name)) return new Result.Error("Placement '" + name + "' not found. Loaded: " + describe(names));
            return new Result.Chosen(name, names);
        }
        if (names.size() > 1) {
            return new Result.Error("Several placements loaded, choose one with " + command + " <name>: " + describe(names));
        }
        return new Result.Chosen(names.getFirst(), names);
    }

    /** Distinct names in load order, repeated names as {@code name (2x)}. */
    public static String describe(List<String> names) {
        Map<String, Long> counts = names.stream().collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
            .map(e -> e.getValue() > 1 ? e.getKey() + " (" + e.getValue() + "x)" : e.getKey())
            .collect(Collectors.joining(", "));
    }
}
