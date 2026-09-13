package dev.tore.schemaforge.compat;

/**
 * Result of {@link VersionProbe}, one {@link Section} per checked mod.
 * Shell from P0-04; fields and chat rendering are defined in P0-05.
 */
public final class ProbeReport {
    private ProbeReport() {
        throw new UnsupportedOperationException("P0-05");
    }

    /**
     * One report section, e.g. the Litematica signature checks.
     * Shell from P0-04; filled in P0-05.
     */
    public static final class Section {
        private Section() {
            throw new UnsupportedOperationException("P0-05");
        }
    }
}
