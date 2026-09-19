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

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LavaCauldronBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreadeableBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which items a block state costs, following the rules of Litematica's material list (its {@code MaterialCache},
 * read via javap from 0.28.8 and implemented independently), so that {@link SchematicSnapshot#materialTotals()}
 * matches what the player sees in Litematica.
 * <p>
 * Litematica resolves the base item via the block's pick stack; this class uses {@link Block#asItem()}, which is the
 * same for every regular block. Blocks without an item (e.g. attached melon stems) cost nothing here. Added in P1-02.
 */
public final class MaterialRules {
    public record Requirement(Item item, int count) {
    }

    private MaterialRules() {
    }

    /** Items needed to place {@code state}; empty if it costs nothing, two entries for filled pots and cauldrons. */
    public static List<Requirement> required(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FlowerPotBlock pot && block != Blocks.FLOWER_POT) {
            return withBase(Items.FLOWER_POT, pot.getPotted().asItem(), 1);
        }
        if (block instanceof AbstractCauldronBlock && block != Blocks.CAULDRON) {
            return cauldron(state, block);
        }
        Item item = baseItem(state, block);
        if (item == Items.AIR) return List.of();
        return List.of(new Requirement(item, count(state, block)));
    }

    /** Sum of {@link #required} over all states, in first-seen order. */
    public static Map<Item, Integer> totals(Iterable<BlockState> states) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (BlockState state : states) {
            for (Requirement r : required(state)) totals.merge(r.item(), r.count(), Integer::sum);
        }
        return totals;
    }

    private static Item baseItem(BlockState state, Block block) {
        if (state.isAir()) return Items.AIR;
        if (block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON
            || block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
            return Items.AIR;
        }
        if (block == Blocks.FARMLAND) return Items.DIRT;
        // Only source blocks can be placed with a bucket.
        if (block == Blocks.WATER) return state.getValue(LiquidBlock.LEVEL) == 0 ? Items.WATER_BUCKET : Items.AIR;
        if (block == Blocks.LAVA) return state.getValue(LiquidBlock.LEVEL) == 0 ? Items.LAVA_BUCKET : Items.AIR;
        // Two-block structures cost one item, counted at the half the player places.
        if (block instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return Items.AIR;
        if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) return Items.AIR;
        if (block instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) return Items.AIR;
        return block.asItem();
    }

    private static int count(BlockState state, Block block) {
        if (block instanceof SlabBlock) return state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        if (block == Blocks.SNOW) return state.getValue(SnowLayerBlock.LAYERS);
        if (block instanceof TurtleEggBlock) return state.getValue(TurtleEggBlock.EGGS);
        if (block instanceof SeaPickleBlock) return state.getValue(SeaPickleBlock.PICKLES);
        if (block instanceof CandleBlock) return state.getValue(CandleBlock.CANDLES);
        if (block instanceof MultifaceSpreadeableBlock) return MultifaceBlock.availableFaces(state).size();
        return 1;
    }

    private static List<Requirement> cauldron(BlockState state, Block block) {
        if (block instanceof LavaCauldronBlock) return withBase(Items.CAULDRON, Items.LAVA_BUCKET, 1);
        if (block == Blocks.POWDER_SNOW_CAULDRON) return withBase(Items.CAULDRON, Items.POWDER_SNOW_BUCKET, 1);
        if (block == Blocks.WATER_CAULDRON) {
            // A full cauldron is one bucket; lower levels are filled with water bottles.
            int level = state.getValue(LayeredCauldronBlock.LEVEL);
            return switch (level) {
                case 1, 2 -> withBase(Items.CAULDRON, Items.POTION, level);
                case 3 -> withBase(Items.CAULDRON, Items.WATER_BUCKET, 1);
                default -> List.of(new Requirement(Items.CAULDRON, 1));
            };
        }
        return List.of(new Requirement(Items.CAULDRON, 1));
    }

    private static List<Requirement> withBase(Item base, Item content, int contentCount) {
        if (content == Items.AIR) return List.of(new Requirement(base, 1));
        return List.of(new Requirement(base, 1), new Requirement(content, contentCount));
    }
}
