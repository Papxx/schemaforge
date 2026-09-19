package dev.tore.schemaforge;

import com.mojang.logging.LogUtils;
import dev.tore.schemaforge.commands.SfCommand;
import dev.tore.schemaforge.modules.BuildResume;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Addon entry point. Holds the shared logger, module category and HUD group.
 * Modules, commands and HUD elements are registered by the tickets that implement them
 * (SfCommand: P0-05, SchemaPrinter: P2-07 and BuildResume: P3-04 – registered, BuildProgressHud: P3-05,
 * ContainerRestock: P4-04), so no unfinished shell is reachable in-game.
 */
public class SchemaForgeAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("SchemaForge");
    public static final HudGroup HUD_GROUP = new HudGroup("SchemaForge");

    @Override
    public void onInitialize() {
        LOG.info("Initializing SchemaForge");

        Commands.add(new SfCommand());
        Modules.get().add(new SchemaPrinter());
        Modules.get().add(new BuildResume());
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
