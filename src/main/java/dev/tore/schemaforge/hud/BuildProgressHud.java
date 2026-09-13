package dev.tore.schemaforge.hud;

import dev.tore.schemaforge.SchemaForgeAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

/**
 * Build progress, blocks/min, ETA, state and top missing materials.
 * Shell from P0-04; implemented and registered in P3-05.
 */
public final class BuildProgressHud extends HudElement {
    public static final HudElementInfo<BuildProgressHud> INFO = new HudElementInfo<>(SchemaForgeAddon.HUD_GROUP, "build-progress", "Shows SchemaForge build progress.", BuildProgressHud::new);

    public BuildProgressHud() {
        super(INFO);
    }

    /** Implemented in P3-05. */
    @Override
    public void render(HudRenderer renderer) {
        throw new UnsupportedOperationException("P3-05");
    }
}
