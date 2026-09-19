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

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Baritone is compileOnly, so the test runtime is exactly the "Baritone missing" case (P3-01 AK2). */
class BaritoneBridgeTest {
    @Test
    void baritoneIsNotOnTestClasspath() {
        assertTrue(SignatureCheck.load(getClass().getClassLoader(), "baritone.api.BaritoneAPI").isEmpty());
    }

    @Test
    void withoutBaritoneNothingIsPresent() {
        assertFalse(BaritoneBridge.isPresent());
    }

    @Test
    void withoutBaritoneNavigationDoesNothing() {
        BlockPos target = new BlockPos(1, 2, 3);
        assertDoesNotThrow(() -> BaritoneBridge.gotoNear(target, 3));
        assertDoesNotThrow(() -> BaritoneBridge.gotoBlock(target));
        assertDoesNotThrow(BaritoneBridge::stop);
        assertFalse(assertDoesNotThrow(BaritoneBridge::isPathing));
    }
}
