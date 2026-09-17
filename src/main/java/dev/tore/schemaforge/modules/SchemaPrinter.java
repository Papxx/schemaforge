package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.BaritoneBridge;
import dev.tore.schemaforge.core.ActionBudget;
import dev.tore.schemaforge.core.AdditiveOnlyGuard;
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
    private final AdditiveOnlyGuard guard = new AdditiveOnlyGuard(BaritoneBridge.BREAK_SETTINGS);

    public SchemaPrinter() {
        super(SchemaForgeAddon.CATEGORY, "schema-printer", "Prints the active Litematica placement into the world.");
    }

    /** Forbids Baritone to break blocks for the whole run (P2-06); additiveOnly is fixed until the setting exists (P2-07). */
    @Override
    public void onActivate() {
        guard.engage(true);
        if (guard.engaged()) SchemaForgeAddon.LOG.info("Additive only: Baritone may not break blocks until the printer stops");
    }

    /** Restores Baritone's break settings (P2-06). */
    @Override
    public void onDeactivate() {
        if (guard.engaged()) SchemaForgeAddon.LOG.info("Additive only: Baritone break settings restored");
        guard.release();
    }

    /** Refills the packet budget before anything else runs in this tick (P2-01). */
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        budget.resetTick();
    }
}
