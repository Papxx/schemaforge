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

import dev.tore.schemaforge.compat.EasyPlaceProtocol;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5-06: the hit vector layout of the EasyPlace protocol. Expected values are built by hand from the documented layout
 * (ARCHITECTURE.md §4, read from Litematica 0.28.8) and vanilla enum orders, not from the implementation.
 */
class AccuratePlacementTest {
    private static final Vec3 HIT = new Vec3(10.5, 64.25, -3.5);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void withoutProtocolTheHitIsSentAsItIs() {
        assertSame(HIT, AccuratePlacement.encode(EasyPlaceProtocol.NONE, stairs(Direction.EAST, Half.BOTTOM), HIT));
    }

    @Test
    void onlyXChangesAndOnlyByAWholeNumber() {
        for (EasyPlaceProtocol proto : new EasyPlaceProtocol[]{EasyPlaceProtocol.V2_CARPET, EasyPlaceProtocol.V3_SERVUX}) {
            Vec3 sent = AccuratePlacement.encode(proto, stairs(Direction.NORTH, Half.TOP), HIT);
            assertEquals(HIT.y, sent.y, proto.name());
            assertEquals(HIT.z, sent.z, proto.name());
            double shift = sent.x - HIT.x;
            assertTrue(shift >= 2 && shift == Math.rint(shift), proto + " shift " + shift);
        }
    }

    @Test
    void v2CarriesFacingPlusSixteenForTheTopHalf() {
        // code = facing (3D data value) + 16 for HALF=TOP; x' = x + 2 + 2·code
        assertEquals(HIT.x + 2 + 2 * Direction.EAST.get3DDataValue(),
            AccuratePlacement.encode(EasyPlaceProtocol.V2_CARPET, stairs(Direction.EAST, Half.BOTTOM), HIT).x);
        assertEquals(HIT.x + 2 + 2 * (Direction.NORTH.get3DDataValue() + 16),
            AccuratePlacement.encode(EasyPlaceProtocol.V2_CARPET, stairs(Direction.NORTH, Half.TOP), HIT).x);
    }

    @Test
    void v2CarriesTheAxisOfPillarsAndTheRepeaterDelay() {
        BlockState logZ = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        assertEquals(HIT.x + 2 + 2 * Direction.Axis.Z.ordinal(), AccuratePlacement.encode(EasyPlaceProtocol.V2_CARPET, logZ, HIT).x);
        BlockState logX = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        assertEquals(HIT.x + 2, AccuratePlacement.encode(EasyPlaceProtocol.V2_CARPET, logX, HIT).x, "axis X is code 0, still sent");

        BlockState repeater = Blocks.REPEATER.defaultBlockState()
            .setValue(RepeaterBlock.FACING, Direction.SOUTH).setValue(RepeaterBlock.DELAY, 2);
        assertEquals(HIT.x + 2 + 2 * (Direction.SOUTH.get3DDataValue() + 2 * 16),
            AccuratePlacement.encode(EasyPlaceProtocol.V2_CARPET, repeater, HIT).x);
    }

    @Test
    void v2LeavesBlocksWithoutOrientationAlone() {
        assertEquals(HIT, AccuratePlacement.encode(EasyPlaceProtocol.V2_CARPET, Blocks.STONE.defaultBlockState(), HIT));
    }

    @Test
    void v3PacksFacingFirstThenTheWhitelistedPropertiesByName() {
        // facing: 3 bits from bit 1 · half: 2 values → 1 bit · shape: 5 values → 3 bits · waterlogged: not sent
        BlockState state = stairs(Direction.EAST, Half.BOTTOM).setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT);
        int value = Direction.EAST.get3DDataValue() << 1
            | Half.BOTTOM.ordinal() << 4
            | StairsShape.OUTER_LEFT.ordinal() << 5;
        assertEquals(HIT.x + 2 + value, AccuratePlacement.encode(EasyPlaceProtocol.V3_SERVUX, state, HIT).x);
    }

    @Test
    void v3SendsTheSignRotationAsASixteenValueField() {
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 11);
        // rotation: 16 values → 4 bits from bit 1; waterlogged is not on the whitelist
        assertEquals(HIT.x + 2 + (11 << 1), AccuratePlacement.encode(EasyPlaceProtocol.V3_SERVUX, sign, HIT).x);
    }

    @Test
    void v3LeavesBlocksWithoutWhitelistedPropertiesAlone() {
        assertEquals(HIT, AccuratePlacement.encode(EasyPlaceProtocol.V3_SERVUX, Blocks.STONE.defaultBlockState(), HIT));
        assertEquals(HIT, AccuratePlacement.encode(EasyPlaceProtocol.V3_SERVUX, Blocks.OAK_LEAVES.defaultBlockState(), HIT));
    }

    @Test
    void whichProtocolCarriesWhichRotation() {
        BlockState stairs = stairs(Direction.EAST, Half.BOTTOM);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
        BlockState rail = Blocks.RAIL.defaultBlockState();

        assertFalse(AccuratePlacement.carriesRotation(EasyPlaceProtocol.NONE, stairs));
        assertTrue(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V2_CARPET, stairs));
        assertFalse(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V2_CARPET, door), "the hinge would be read from the shifted x");
        assertFalse(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V2_CARPET, sign), "no direction property");
        assertFalse(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V2_CARPET, rail));
        assertTrue(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V3_SERVUX, door));
        assertTrue(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V3_SERVUX, sign));
        assertTrue(AccuratePlacement.carriesRotation(EasyPlaceProtocol.V3_SERVUX, rail));
    }

    private static BlockState stairs(Direction facing, Half half) {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }
}
