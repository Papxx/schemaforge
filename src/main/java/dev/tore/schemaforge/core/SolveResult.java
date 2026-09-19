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

import net.minecraft.core.BlockPos;

/** Outcome of {@link PlacementSolver#solve}. Produced from P2-02 on. */
public sealed interface SolveResult permits SolveResult.Ok, SolveResult.NeedsSupport, SolveResult.Unsupported {
    record Ok(PlacementPlan plan) implements SolveResult {
    }

    record NeedsSupport(BlockPos missingSupport) implements SolveResult {
    }

    record Unsupported(String reason) implements SolveResult {
    }
}
