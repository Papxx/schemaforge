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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * What the Printer actually has to do for one placement.
 * {@code requiresRealRotation} is true for rotation-dependent blocks without accurate placement.
 * Produced by the PlacementSolver in P2-02.
 *
 * @param hitVec       the real point on the clicked face; look direction, reach and sight are checked against it
 * @param packetHitVec what goes into the use packet: {@code hitVec}, or with accurate placement the encoded state (P5-06)
 */
public record PlacementPlan(
    BlockPos clickPos,
    Direction clickFace,
    Vec3 hitVec,
    float yaw,
    float pitch,
    boolean requiresRealRotation,
    Item handItem,
    boolean sneak,
    Vec3 packetHitVec
) {
    /** A plan that sends the real hit point. */
    public PlacementPlan(BlockPos clickPos, Direction clickFace, Vec3 hitVec, float yaw, float pitch,
                         boolean requiresRealRotation, Item handItem, boolean sneak) {
        this(clickPos, clickFace, hitVec, yaw, pitch, requiresRealRotation, handItem, sneak, hitVec);
    }
}
