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

import dev.tore.schemaforge.core.ContainerType;
import dev.tore.schemaforge.core.view.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** {@link WorldView} over a live level; client thread only. First used by {@code .sf preview} (P1-05). */
public final class McWorldView implements WorldView {
    private final Level level;

    public McWorldView(Level level) {
        this.level = level;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return level.getBlockState(pos);
    }

    @Override
    public boolean isChunkLoaded(BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public Optional<ContainerType> containerAt(BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof ChestBlock) return Optional.of(ContainerType.CHEST);
        if (block instanceof BarrelBlock) return Optional.of(ContainerType.BARREL);
        if (block instanceof ShulkerBoxBlock) return Optional.of(ContainerType.SHULKER);
        if (block instanceof EnderChestBlock) return Optional.of(ContainerType.ENDER_CHEST);
        return Optional.empty();
    }
}
