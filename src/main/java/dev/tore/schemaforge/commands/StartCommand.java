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
import dev.tore.schemaforge.modules.BuildResume;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Optional;

/**
 * {@code .sf start [placement]} (P2-07): picks the placement, writes it to the module setting and (re)starts the
 * SchemaPrinter module. Everything else happens in the module's activation.
 * P3-04: a usable checkpoint is offered instead of starting; running the command again starts over.
 */
public final class StartCommand {
    /** Placement whose checkpoint was offered by the last {@code .sf start}; a second call starts over. */
    private static String offeredFor;

    private StartCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("start")
            .executes(_ -> run(Optional.empty()))
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, String>argument("placement", StringArgumentType.greedyString())
                .suggests((_, builder) -> SharedSuggestionProvider.suggest(LitematicaAdapter.placementNames().stream().distinct(), builder))
                .executes(ctx -> run(Optional.of(StringArgumentType.getString(ctx, "placement")))));
    }

    /**
     * False when the command only offered a checkpoint and should not start yet (P3-04).
     * A checkpoint from other settings is dropped with a warning (AK2); asking the same placement twice starts over.
     */
    private static boolean checkpointHandled(SchemaPrinter printer, String placement) {
        BuildResume resume = Modules.get().get(BuildResume.class);
        switch (resume.check(SchemaPrinter.folder(), placement, printer.currentPlanConfig())) {
            case BuildResume.Resume.None _ -> {
                return true;
            }
            case BuildResume.Resume.Stale _ -> {
                printer.warning("The checkpoint for '%s' was written with other settings; starting over.", placement);
                BuildResume.discard(SchemaPrinter.folder(), placement);
                offeredFor = null;
                return true;
            }
            case BuildResume.Resume.Usable(var checkpoint) -> {
                if (placement.equals(offeredFor)) {
                    printer.info("Starting over; the checkpoint is gone.");
                    BuildResume.discard(SchemaPrinter.folder(), placement);
                    offeredFor = null;
                    return true;
                }
                offeredFor = placement;
                printer.info("Checkpoint for '%s': cluster %d, %d blocks placed. Continue with .sf resume, or run .sf start again to start over.",
                    placement, checkpoint.clusterIndex(), checkpoint.placedCount());
                return false;
            }
        }
    }

    /** The checkpoint {@code .sf resume} should continue from, or empty (P3-04). */
    public static Optional<Integer> offeredCluster(SchemaPrinter printer) {
        if (offeredFor == null) return Optional.empty();
        if (!(Modules.get().get(BuildResume.class)
            .check(SchemaPrinter.folder(), offeredFor, printer.currentPlanConfig()) instanceof BuildResume.Resume.Usable(var checkpoint))) {
            return Optional.empty();
        }
        return Optional.of(checkpoint.clusterIndex());
    }

    /** Forgets the offer once it was taken or the build started some other way. */
    public static void clearOffer() {
        offeredFor = null;
    }

    private static int run(Optional<String> requested) {
        SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
        return switch (PlacementChoice.resolve(requested, ".sf start")) {
            case PlacementChoice.Result.Error(String message) -> {
                printer.error(message);
                yield 0;
            }
            case PlacementChoice.Result.Chosen chosen -> {
                if (chosen.duplicates() > 1) {
                    printer.warning("%d placements are named '%s'; printing the first one.", chosen.duplicates(), chosen.name());
                }
                printer.placementSetting().set(chosen.name());
                if (!checkpointHandled(printer, chosen.name())) yield Command.SINGLE_SUCCESS;
                // A running build is replaced by the new one.
                printer.disable();
                printer.enable();
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
