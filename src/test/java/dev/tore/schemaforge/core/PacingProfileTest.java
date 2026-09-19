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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P5-05: what each profile stands for and when it warns. */
class PacingProfileTest {
    private static final PacingProfile.Pacing CUSTOM = new PacingProfile.Pacing(3, 5, true);

    @Test
    void vanillaLegitIsOneBlockPerTickWithARealRotation() {
        PacingProfile.Pacing pacing = PacingProfile.VANILLA_LEGIT.resolve(CUSTOM);
        assertEquals(new PacingProfile.Pacing(1, 1, false), pacing, "the custom settings do not leak in");
    }

    @Test
    void fastPlacesSeveralBlocksPerTickWithSpoofedRotation() {
        PacingProfile.Pacing pacing = PacingProfile.FAST.resolve(CUSTOM);
        assertTrue(pacing.blocksPerTick() > 1);
        assertEquals(1, pacing.tickInterval());
        assertTrue(pacing.rotationSpoof());
    }

    @Test
    void customTakesTheSettingsAsTheyAre() {
        assertEquals(CUSTOM, PacingProfile.CUSTOM.resolve(CUSTOM));
    }

    @Test
    void onlyFastWarns() {
        assertTrue(PacingProfile.FAST.warning().orElseThrow().contains("Anti-cheat"));
        assertFalse(PacingProfile.VANILLA_LEGIT.warning().isPresent());
        assertFalse(PacingProfile.CUSTOM.warning().isPresent());
    }
}
