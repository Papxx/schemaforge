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

/** Why a {@link BlockTask} of kind {@link TaskKind#SKIP} is skipped; {@link #NONE} for every other kind. Added in P1-03. */
public enum SkipReason {
    NONE,
    /** World state unknown; the task is re-evaluated once the chunk is loaded. */
    CHUNK_NOT_LOADED,
    /** Target block is listed in {@code PlanConfig.neverPlace}. */
    NEVER_PLACE,
    /** World block is listed in {@code PlanConfig.skipIfWorldIs}. */
    WORLD_FILTER,
    /** A wrong block stands in the way and breaking is forbidden by {@code additiveOnly}. */
    MISMATCH_ADDITIVE_ONLY
}
