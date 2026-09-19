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
package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.core.view.SafetyView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

import java.util.OptionalDouble;

/** {@link SafetyView} over the local player and the level around it (P3-03); client thread only. */
public final class McSafetyView implements SafetyView {
    private final LocalPlayer player;
    private final ClientLevel level;

    public McSafetyView(LocalPlayer player, ClientLevel level) {
        this.player = player;
        this.level = level;
    }

    @Override
    public float health() {
        return player.getHealth();
    }

    @Override
    public int food() {
        return player.getFoodData().getFoodLevel();
    }

    /** Spectators are ignored: they cannot interfere with the build and are often staff just looking. */
    @Override
    public OptionalDouble nearestOtherPlayer() {
        return level.players().stream()
            .filter(other -> other != player && !other.isSpectator())
            .mapToDouble(this::distanceTo)
            .min();
    }

    private double distanceTo(AbstractClientPlayer other) {
        return player.position().distanceTo(other.position());
    }
}
