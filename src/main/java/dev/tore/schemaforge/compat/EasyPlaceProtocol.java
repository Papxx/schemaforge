package dev.tore.schemaforge.compat;

/**
 * Accurate block placement protocol available for the current session.
 * Detected in P0-05 via {@link LitematicaAdapter#detectedProtocol()}; encoded by {@code core.AccuratePlacement} (P5-06).
 */
public enum EasyPlaceProtocol {
    NONE,
    V2_CARPET,
    V3_SERVUX
}
