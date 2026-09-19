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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.tore.schemaforge.compat.LitematicaAdapter;
import dev.tore.schemaforge.compat.McWorldView;
import dev.tore.schemaforge.core.Cluster;
import dev.tore.schemaforge.core.ContainerIndex;
import dev.tore.schemaforge.core.MaterialRules;
import dev.tore.schemaforge.core.MaterialsReport;
import dev.tore.schemaforge.core.PlanConfig;
import dev.tore.schemaforge.core.SchematicSnapshot;
import dev.tore.schemaforge.core.TaskKind;
import dev.tore.schemaforge.core.WorkPlanner;
import dev.tore.schemaforge.modules.ContainerRestock;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code .sf materials [placement]} (P4-06): item, demand of the whole schematic, demand of the next clusters,
 * inventory and what the container index knows. Read-only, sends no packets.
 */
public final class MaterialsCommand {
    private static final String PREFIX = "SchemaForge";

    private MaterialsCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("materials")
            .executes(_ -> run(Optional.empty()))
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("placement", StringArgumentType.greedyString())
                .suggests((_, builder) -> SharedSuggestionProvider.suggest(LitematicaAdapter.placementNames().stream().distinct(), builder))
                .executes(ctx -> run(Optional.of(StringArgumentType.getString(ctx, "placement")))));
    }

    private static int run(Optional<String> requested) {
        Minecraft mc = MeteorClient.mc;
        if (mc.level == null || mc.player == null) return error("Join a world first.");
        PlacementChoice.Result choice = PlacementChoice.resolve(requested, ".sf materials");
        if (choice instanceof PlacementChoice.Result.Error(String message)) return error(message);
        PlacementChoice.Result.Chosen chosen = (PlacementChoice.Result.Chosen) choice;

        Optional<SchematicSnapshot> snapshot = LitematicaAdapter.snapshot(chosen.name());
        if (snapshot.isEmpty()) return error("Could not read placement '" + chosen.name() + "'.");

        SchematicSnapshot snap = snapshot.get();
        ContainerRestock restock = Modules.get().get(ContainerRestock.class);
        List<Cluster> clusters = new WorkPlanner(PlanConfig.defaults())
            .plan(snap, new McWorldView(mc.level), mc.player.blockPosition());

        List<MaterialsReport.Row> rows = MaterialsReport.rows(
            snap.materialTotals(),
            upcoming(clusters, restock.lookaheadClusters()),
            item -> InvUtils.find(item).count(),
            item -> inContainers(restock.index(), item));
        print(MaterialsReport.lines(rows));
        return Command.SINGLE_SUCCESS;
    }

    /** Demand of the first {@code count} clusters; the same numbers the restock works with. */
    private static Map<Item, Integer> upcoming(List<Cluster> clusters, int count) {
        Map<Item, Integer> demand = new LinkedHashMap<>();
        for (Cluster cluster : clusters.stream().limit(count).toList()) {
            cluster.tasks().stream()
                .filter(task -> task.kind() == TaskKind.PLACE || task.kind() == TaskKind.FLUID)
                .forEach(task -> MaterialRules.required(task.target())
                    .forEach(requirement -> demand.merge(requirement.item(), requirement.count(), Integer::sum)));
        }
        return demand;
    }

    private static int inContainers(ContainerIndex index, Item item) {
        return index.entries().stream().mapToInt(entry -> entry.count(item)).sum();
    }

    private static void print(List<String> lines) {
        MutableComponent block = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            ChatFormatting style = i == 0 ? ChatFormatting.WHITE
                : lines.get(i).contains("missing") ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            block.append(Component.literal((i == 0 ? "" : "\n") + lines.get(i)).withStyle(style));
        }
        ChatUtils.sendMsg(PREFIX, block);
    }

    private static int error(String message) {
        ChatUtils.sendMsg(PREFIX, Component.literal(message).withStyle(ChatFormatting.RED));
        return 0;
    }
}
