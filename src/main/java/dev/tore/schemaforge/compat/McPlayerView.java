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

import dev.tore.schemaforge.core.view.PlayerView;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** {@link PlayerView} over the local player (P2-03); client thread only, create a new one when the player changes. */
public final class McPlayerView implements PlayerView {
    /** Tolerance for a ray that stops exactly on the target face. */
    private static final double SIGHT_EPSILON = 1.0e-3;

    private final LocalPlayer player;
    private final double reachSetting;

    /** @param reachSetting upper bound from the settings; the player's own interaction range still applies */
    public McPlayerView(LocalPlayer player, double reachSetting) {
        this.player = player;
        this.reachSetting = reachSetting;
    }

    @Override
    public Vec3 eyePos() {
        return player.getEyePosition();
    }

    @Override
    public float yaw() {
        return player.getYRot();
    }

    @Override
    public float pitch() {
        return player.getXRot();
    }

    @Override
    public double reach() {
        return Math.min(reachSetting, player.blockInteractionRange());
    }

    /** True if no block outline lies between the eyes and {@code target} (a hit on the target face itself counts as seen). */
    @Override
    public boolean hasLineOfSight(Vec3 target) {
        Vec3 eye = eyePos();
        HitResult hit = player.level().clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getLocation().distanceTo(eye) >= eye.distanceTo(target) - SIGHT_EPSILON;
    }
}
