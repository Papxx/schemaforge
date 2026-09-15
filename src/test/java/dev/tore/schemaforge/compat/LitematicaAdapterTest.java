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
