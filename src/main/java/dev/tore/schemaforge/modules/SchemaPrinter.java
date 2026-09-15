package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.core.ActionBudget;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Main module holding all printer settings and the build state machine.
 * Shell from P0-04; the tick hook exists since P2-01, settings and behavior follow in P2-07, which also registers the module.
 */
public final class SchemaPrinter extends Module {
    /** One action per tick until the blocksPerTick setting exists (P2-07). */
    private final ActionBudget budget = new ActionBudget(() -> 1);

    public SchemaPrinter() {
        super(SchemaForgeAddon.CATEGORY, "schema-printer", "Prints the active Litematica placement into the world.");
    }

    /** Refills the packet budget before anything else runs in this tick (P2-01). */
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        budget.resetTick();
    }
}
