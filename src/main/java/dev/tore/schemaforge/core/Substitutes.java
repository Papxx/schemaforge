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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses the {@code substitutes} setting (P2-07): one line per target block, {@code a->b,c} meaning the world may hold
 * b or c instead of a. Ids without a namespace are {@code minecraft}. Bad lines are skipped and reported, so one typo
 * does not cost the whole setting.
 */
public final class Substitutes {
    public record Parsed(Map<Block, List<Block>> map, List<String> errors) {
    }

    private Substitutes() {
    }

    public static Parsed parse(List<String> lines) {
        Map<Block, List<Block>> map = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (String line : lines) {
            String text = line.trim();
            if (text.isEmpty()) continue;
            String[] sides = text.split("->", -1);
            if (sides.length != 2) {
                errors.add("'" + text + "': expected <block>-><block>[,<block>]");
                continue;
            }
            Optional<Block> target = block(sides[0]);
            if (target.isEmpty()) {
                errors.add("'" + text + "': unknown block '" + sides[0].trim() + "'");
                continue;
            }
            List<Block> replacements = new ArrayList<>();
            boolean bad = false;
            for (String name : sides[1].split(",", -1)) {
                Optional<Block> replacement = block(name);
                if (replacement.isEmpty()) {
                    errors.add("'" + text + "': unknown block '" + name.trim() + "'");
                    bad = true;
                    break;
                }
                replacements.add(replacement.get());
            }
            if (bad) continue;
            if (replacements.isEmpty()) {
                errors.add("'" + text + "': no replacement block");
                continue;
            }
            map.computeIfAbsent(target.get(), _ -> new ArrayList<>()).addAll(replacements);
        }
        return new Parsed(Map.copyOf(map), List.copyOf(errors));
    }

    /** Air means "not a block id here": {@code minecraft:air} is what the registry returns for anything unknown. */
    private static Optional<Block> block(String name) {
        Identifier id = Identifier.tryParse(name.trim().toLowerCase());
        if (id == null) return Optional.empty();
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block == Blocks.AIR && !"minecraft:air".equals(id.toString()) ? Optional.empty() : Optional.of(block);
    }
}
