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

import dev.tore.schemaforge.core.MaterialRules.Requirement;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Material rules from P1-02 (Litematica material-list parity), on the block classes of the test schematic (P1-04). */
class MaterialRulesTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fullBlockCostsOneItem() {
        assertEquals(List.of(new Requirement(Items.STONE, 1)), MaterialRules.required(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void airCostsNothing() {
        assertEquals(List.of(), MaterialRules.required(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void doubleSlabCostsTwo() {
        assertEquals(List.of(new Requirement(Items.OAK_SLAB, 2)),
            MaterialRules.required(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)));
        assertEquals(List.of(new Requirement(Items.OAK_SLAB, 1)),
            MaterialRules.required(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)));
    }

    @Test
    void doorAndBedAreCountedOnce() {
        assertEquals(List.of(new Requirement(Items.OAK_DOOR, 1)),
            MaterialRules.required(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
        assertEquals(List.of(), MaterialRules.required(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        assertEquals(List.of(), MaterialRules.required(Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD)));
    }

    @Test
    void wallTorchNeedsTorch() {
        assertEquals(List.of(new Requirement(Items.TORCH, 1)), MaterialRules.required(Blocks.WALL_TORCH.defaultBlockState()));
    }

    @Test
    void onlyWaterSourceNeedsBucket() {
        assertEquals(List.of(new Requirement(Items.WATER_BUCKET, 1)), MaterialRules.required(Blocks.WATER.defaultBlockState()));
        assertEquals(List.of(), MaterialRules.required(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3)));
    }

    @Test
    void layeredBlocksCountLayers() {
        assertEquals(List.of(new Requirement(Items.SNOW, 5)),
            MaterialRules.required(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5)));
    }

    @Test
    void filledContainersNeedBaseAndContent() {
        assertEquals(List.of(new Requirement(Items.FLOWER_POT, 1), new Requirement(Items.POPPY, 1)),
            MaterialRules.required(Blocks.POTTED_POPPY.defaultBlockState()));
        assertEquals(List.of(new Requirement(Items.CAULDRON, 1), new Requirement(Items.POTION, 2)),
            MaterialRules.required(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2)));
    }

    @Test
    void totalsSumAcrossStates() {
        Map<net.minecraft.world.item.Item, Integer> totals = MaterialRules.totals(List.of(
            Blocks.STONE.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState()
        ));
        assertEquals(Map.of(Items.STONE, 2, Items.OAK_SLAB, 3), totals);
    }
}
