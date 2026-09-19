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
package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.compat.ProbeReport.Line;
import dev.tore.schemaforge.compat.ProbeReport.Section;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects MC, Meteor, Baritone, Litematica and MaLiLib versions, the server brand,
 * the EasyPlace protocol and the Litematica MethodHandle checks into a {@link ProbeReport}.
 * Mods are looked up by id and class name only, so this runs with any soft dependency missing (P0-05).
 */
public final class VersionProbe {
    /** Meteor bundles its own Baritone fork; a standalone Baritone registers as {@code baritone}. */
    private static final List<String> BARITONE_IDS = List.of("baritone-meteor", "baritone");
    private static final String BARITONE_API = "baritone.api.BaritoneAPI";

    private VersionProbe() {
    }

    public static ProbeReport run() {
        return new ProbeReport(List.of(
            new Section("Minecraft", List.of(versionLine("Minecraft", "minecraft"))),
            new Section("Meteor", List.of(versionLine("Meteor Client", "meteor-client"))),
            new Section("Server", List.of(serverLine())),
            baritoneSection(),
            new Section("MaLiLib", List.of(versionLine("MaLiLib", "malilib"))),
            litematicaSection()
        ));
    }

    private static Section baritoneSection() {
        Optional<String> version = BARITONE_IDS.stream().map(VersionProbe::modVersion).flatMap(Optional::stream).findFirst();
        boolean apiPresent = SignatureCheck.load(VersionProbe.class.getClassLoader(), BARITONE_API).isPresent();

        if (version.isEmpty() && !apiPresent) {
            return new Section("Baritone", List.of(Line.missing("Baritone", "missing")));
        }
        return new Section("Baritone", List.of(
            version.map(v -> Line.ok("Baritone", v)).orElseGet(() -> Line.ok("Baritone", "unknown version (API found)")),
            apiPresent ? Line.ok("baritone.api", "found") : Line.fail("baritone.api", "class " + BARITONE_API + " not found")
        ));
    }

    private static Section litematicaSection() {
        Optional<String> version = modVersion("litematica");
        if (version.isEmpty()) {
            return new Section("Litematica", List.of(Line.missing("Litematica", "missing")));
        }
        Section probe = LitematicaAdapter.probe();
        List<Line> lines = new ArrayList<>(probe.lines().size() + 1);
        lines.add(Line.ok("Litematica", version.get()));
        // A probe reporting "missing" although Fabric loaded the mod means the main class moved.
        probe.lines().stream()
            .map(l -> l.status() == ProbeReport.Status.MISSING ? Line.fail("class Litematica", "not found") : l)
            .forEach(lines::add);
        return new Section("Litematica", lines);
    }

    private static Line serverLine() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return Line.ok("Server brand", "not connected");

        String brand = Optional.ofNullable(connection.serverBrand()).orElse("unknown");
        return Line.ok("Server brand", mc.hasSingleplayerServer() ? brand + " (singleplayer)" : brand);
    }

    private static Line versionLine(String label, String modId) {
        return modVersion(modId)
            .map(v -> Line.ok(label, v))
            .orElseGet(() -> Line.missing(label, "missing"));
    }

    private static Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(c -> c.getMetadata().getVersion().getFriendlyString());
    }
}
