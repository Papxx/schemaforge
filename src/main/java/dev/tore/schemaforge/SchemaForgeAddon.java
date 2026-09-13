package dev.tore.schemaforge;

import com.mojang.logging.LogUtils;
import dev.tore.schemaforge.commands.CommandExample;
import dev.tore.schemaforge.hud.HudExample;
import dev.tore.schemaforge.modules.ModuleExample;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Einstiegspunkt des Addons. Registriert Module, Commands und HUD-Elemente.
 * Die hier noch registrierten Example-Klassen stammen aus dem Meteor-Addon-Template
 * und werden in P0-04 durch die Huellen aus docs/ARCHITECTURE.md ersetzt.
 */
public class SchemaForgeAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("SchemaForge");
    public static final HudGroup HUD_GROUP = new HudGroup("SchemaForge");

    @Override
    public void onInitialize() {
        LOG.info("Initializing SchemaForge");

        // Modules
        Modules.get().add(new ModuleExample());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.tore.schemaforge";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Papxx", "schemaforge");
    }
}
