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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tore.schemaforge.core.StatusReport;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** {@code .sf status} (P2-07, AK2): state, cluster, placed blocks, rate and mismatched blocks of the current run. */
public final class StatusCommand {
    private StatusCommand() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("status").executes(_ -> {
            SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
            List<String> lines = StatusReport.lines(printer.lastStatus(), printer.session().isPresent());
            MutableComponent text = Component.empty();
            for (int i = 0; i < lines.size(); i++) {
                text.append(Component.literal((i == 0 ? "" : "\n") + lines.get(i)));
            }
            printer.info(text);
            return Command.SINGLE_SUCCESS;
        });
    }
}
