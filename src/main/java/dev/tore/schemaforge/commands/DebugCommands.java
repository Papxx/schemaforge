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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.tore.schemaforge.compat.BaritoneBridge;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Internal test commands (P3-01 AK1). {@code .sf debug goto <x> <y> <z>} hands a target to the
 * {@link BaritoneBridge}; {@code .sf debug stopgoto} cancels it. Not meant for normal use.
 */
public final class DebugCommands {
    private static final String PREFIX = "SchemaForge";

    private DebugCommands() {
    }

    public static LiteralArgumentBuilder<ClientSuggestionProvider> build() {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal("debug")
            .then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("goto")
                .then(coordinate("x").then(coordinate("y").then(coordinate("z")
                    .executes(ctx -> gotoPos(new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"))))))))
            .then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("stopgoto")
                .executes(_ -> {
                    if (!available()) return 0;
                    BaritoneBridge.stop();
                    message("Pathing cancelled.", ChatFormatting.GRAY);
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static RequiredArgumentBuilder<ClientSuggestionProvider, Integer> coordinate(String name) {
        return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer());
    }

    private static int gotoPos(BlockPos target) {
        if (!available()) return 0;
        BaritoneBridge.gotoBlock(target);
        message("Walking to " + target.getX() + " " + target.getY() + " " + target.getZ()
            + " (pathing: " + BaritoneBridge.isPathing() + "). Cancel with .sf debug stopgoto.", ChatFormatting.GRAY);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean available() {
        if (!BaritoneBridge.isPresent()) {
            message("Baritone is missing (see .sf doctor).", ChatFormatting.RED);
            return false;
        }
        if (MeteorClient.mc.player == null) {
            message("Join a world first.", ChatFormatting.RED);
            return false;
        }
        return true;
    }

    private static void message(String text, ChatFormatting colour) {
        ChatUtils.sendMsg(PREFIX, Component.literal(text).withStyle(colour));
    }
}
