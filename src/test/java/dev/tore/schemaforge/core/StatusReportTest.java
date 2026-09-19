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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-07 AK2: {@code .sf status} shows state, cluster i/n, placed blocks, blocks per minute and mismatched blocks. */
class StatusReportTest {
    @Test
    void showsEverythingAkTwoAsksFor() {
        BuildSession.Status status = new BuildSession.Status(BuildSession.State.BUILDING, "house", 1, 3, 9, 42, 91.25, 2, 297);
        List<String> lines = StatusReport.lines(Optional.of(status), true);

        String text = String.join(" | ", lines);
        assertTrue(text.contains("BUILDING"), text);
        assertTrue(text.contains("house"), text);
        assertTrue(text.contains("Cluster 3/9"), text);
        assertTrue(text.contains("42 blocks placed"), text);
        assertTrue(text.contains("91.2 blocks/min") || text.contains("91.3 blocks/min"), text);
        assertTrue(text.contains("2 mismatched"), text);
        assertTrue(text.contains("297 left to place"), text);
        assertTrue(text.contains("round 1/" + BuildSession.MAX_ROUNDS), text);
    }

    @Test
    void withoutARunItPointsAtStart() {
        assertEquals(1, StatusReport.lines(Optional.empty(), false).size());
        assertTrue(StatusReport.lines(Optional.empty(), false).getFirst().contains(".sf start"));
    }

    @Test
    void aStoppedRunIsNotShownAsStillBuilding() {
        BuildSession.Status status = new BuildSession.Status(BuildSession.State.BUILDING, "house", 2, 3, 9, 42, 10, 0, 5);
        assertTrue(StatusReport.lines(Optional.of(status), false).getFirst().startsWith("STOPPED"));

        BuildSession.Status done = new BuildSession.Status(BuildSession.State.DONE, "house", 2, 9, 9, 100, 10, 0, 0);
        assertTrue(StatusReport.lines(Optional.of(done), false).getFirst().startsWith("DONE"));
    }
}
