/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.tore.schemaforge;

import com.mojang.logging.LogUtils;
import dev.tore.schemaforge.commands.SfCommand;
import dev.tore.schemaforge.hud.BuildProgressHud;
import dev.tore.schemaforge.modules.BuildResume;
import dev.tore.schemaforge.modules.ContainerRestock;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Addon entry point. Holds the shared logger, module category and HUD group.
 * Modules, commands and HUD elements are registered by the tickets that implement them
 * (SfCommand: P0-05, SchemaPrinter: P2-07, BuildResume: P3-04, BuildProgressHud: P3-05 and
 * ContainerRestock: P4-02 – all registered), so no unfinished shell is reachable in-game.
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
        Modules.get().add(new ContainerRestock());
        Hud.get().register(BuildProgressHud.INFO);
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
