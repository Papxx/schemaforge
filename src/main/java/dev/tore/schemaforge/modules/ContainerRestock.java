package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Restock settings.
 * Shell from P0-04; settings and behavior are implemented in P4-04, which also registers the module.
 */
public final class ContainerRestock extends Module {
    public ContainerRestock() {
        super(SchemaForgeAddon.CATEGORY, "container-restock", "Restocks missing build materials from known containers.");
    }
}
