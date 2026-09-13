package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Main module holding all printer settings and the build state machine.
 * Shell from P0-04; settings and behavior are implemented in P2-07, which also registers the module.
 */
public final class SchemaPrinter extends Module {
    public SchemaPrinter() {
        super(SchemaForgeAddon.CATEGORY, "schema-printer", "Prints the active Litematica placement into the world.");
    }
}
