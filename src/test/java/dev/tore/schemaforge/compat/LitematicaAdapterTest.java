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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Litematica is compileOnly, so the test runtime is exactly the "Litematica missing" case (P1-01 AK2). */
class LitematicaAdapterTest {
    @Test
    void litematicaIsNotOnTestClasspath() {
        assertTrue(SignatureCheck.load(getClass().getClassLoader(), "fi.dy.masa.litematica.Litematica").isEmpty());
    }

    @Test
    void withoutLitematicaNothingIsPresent() {
        assertFalse(LitematicaAdapter.isPresent());
        assertEquals(List.of(), assertDoesNotThrow(LitematicaAdapter::placementNames));
    }

    @Test
    void withoutLitematicaSnapshotIsEmpty() {
        assertTrue(assertDoesNotThrow(() -> LitematicaAdapter.snapshot("any")).isEmpty());
    }

    @Test
    void withoutLitematicaProbeReportsMissing() {
        ProbeReport.Section section = assertDoesNotThrow(LitematicaAdapter::probe);
        assertEquals(ProbeReport.Status.MISSING, section.status());
        assertTrue(assertDoesNotThrow(LitematicaAdapter::detectedProtocol).isEmpty());
    }
}
