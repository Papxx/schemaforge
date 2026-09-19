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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Text of {@code .sf status} as plain lines (P2-07, AK2), without chat or Minecraft.
 * Colours and sending live in {@code commands/StatusCommand}.
 */
public final class StatusReport {
    private StatusReport() {
    }

    /**
     * @param status  the running or last finished run, empty if the module never ran in this session
     * @param running false shows the status as the result of a finished or stopped run
     */
    public static List<String> lines(Optional<BuildSession.Status> status, boolean running) {
        if (status.isEmpty()) return List.of("Idle. Start a build with .sf start [placement].");

        BuildSession.Status s = status.get();
        String state = running ? s.state().toString() : s.state() == BuildSession.State.DONE ? "DONE" : "STOPPED";
        return List.of(
            String.format(Locale.ROOT, "%s - %s (round %d/%d)", state, s.placement(), s.round(), BuildSession.MAX_ROUNDS),
            String.format(Locale.ROOT, "Cluster %d/%d - %d blocks placed - %.1f blocks/min",
                s.clusterIndex(), s.clusterCount(), s.placed(), s.blocksPerMinute()),
            String.format(Locale.ROOT, "%d left to place - %d mismatched", s.remaining(), s.mismatched()));
    }
}
