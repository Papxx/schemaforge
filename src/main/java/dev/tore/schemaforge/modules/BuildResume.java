package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Checkpoint persistence.
 * Shell from P0-04; settings and behavior are implemented in P3-04, which also registers the module.
 */
public final class BuildResume extends Module {
    public BuildResume() {
        super(SchemaForgeAddon.CATEGORY, "build-resume", "Saves build checkpoints and resumes after a disconnect.");
    }
}
