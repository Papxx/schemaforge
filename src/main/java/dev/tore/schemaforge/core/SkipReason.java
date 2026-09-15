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
