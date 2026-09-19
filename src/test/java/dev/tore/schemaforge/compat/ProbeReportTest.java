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

import dev.tore.schemaforge.compat.ProbeReport.Line;
import dev.tore.schemaforge.compat.ProbeReport.Section;
import dev.tore.schemaforge.compat.ProbeReport.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** P0-05: status aggregation of the doctor report. */
class ProbeReportTest {
    @Test
    void allOkReportIsOk() {
        ProbeReport report = new ProbeReport(List.of(
            new Section("Minecraft", List.of(Line.ok("Minecraft", "26.2"))),
            new Section("Empty", List.of())
        ));
        assertEquals(Status.OK, report.status());
        assertEquals(0, report.problemCount());
    }

    @Test
    void worstStatusWins() {
        Section baritone = new Section("Baritone", List.of(Line.missing("Baritone", "missing")));
        Section litematica = new Section("Litematica", List.of(Line.ok("Litematica", "0.28.8"), Line.fail("x", "y"), Line.ok("z", "found")));
        ProbeReport report = new ProbeReport(List.of(baritone, litematica));

        assertEquals(Status.MISSING, baritone.status());
        assertEquals(Status.FAIL, litematica.status());
        assertEquals(Status.FAIL, report.status());
        assertEquals(2, report.problemCount());
    }
}
