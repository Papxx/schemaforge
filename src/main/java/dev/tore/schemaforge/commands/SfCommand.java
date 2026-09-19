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

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * Root of {@code .sf <sub>}; every sub-command lives in its own class added by its ticket.
 * Registered in P0-05 together with the first sub-command ({@link DoctorCommand}).
 */
public final class SfCommand extends Command {
    public SfCommand() {
        super("sf", "SchemaForge commands.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(DoctorCommand.build());
        builder.then(PreviewCommand.build());
        builder.then(StartCommand.build());
        builder.then(ControlCommands.pause());
        builder.then(ControlCommands.resume());
        builder.then(ControlCommands.stop());
        builder.then(StatusCommand.build());
        builder.then(ContainersCommand.build());
        builder.then(ScanCommand.build());
        builder.then(MaterialsCommand.build());
        builder.then(UndoCommand.build());
        builder.then(DebugCommands.build());
    }
}
