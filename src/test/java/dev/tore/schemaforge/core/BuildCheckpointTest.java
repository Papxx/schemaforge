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
package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3-04: the checkpoint file, its schema version and the fingerprint that decides whether it still fits. */
class BuildCheckpointTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void savedCheckpointComesBackUnchanged(@TempDir Path dir) {
        Path file = dir.resolve("sub").resolve("resume-test.json");
        BuildCheckpoint written = new BuildCheckpoint(7, 340, 1_700_000_000_000L, "abcdef0123456789");
        written.save(file);

        assertEquals(Optional.of(written), BuildCheckpoint.load(file));
    }

    @Test
    void fileStartsWithTheSchemaVersion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("resume-test.json");
        new BuildCheckpoint(1, 1, 1L, "hash").save(file);

        String json = Files.readString(file);
        assertTrue(json.replaceAll("\\s", "").startsWith("{\"v\":1,"), json);
        for (String field : List.of("clusterIndex", "placedCount", "startedAt", "planConfigHash")) {
            assertTrue(json.contains('"' + field + '"'), field + " missing from " + json);
        }
    }

    @Test
    void missingBrokenOrForeignVersionReadsAsNothing(@TempDir Path dir) throws Exception {
        assertEquals(Optional.empty(), BuildCheckpoint.load(dir.resolve("nope.json")));

        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{not json");
        assertEquals(Optional.empty(), BuildCheckpoint.load(broken));

        Path future = dir.resolve("future.json");
        Files.writeString(future, "{\"v\":2,\"clusterIndex\":1,\"placedCount\":0,\"startedAt\":0,\"planConfigHash\":\"x\"}");
        assertEquals(Optional.empty(), BuildCheckpoint.load(future));
    }

    @Test
    void deletingIsSafeWhenThereIsNoFile(@TempDir Path dir) {
        BuildCheckpoint.delete(dir.resolve("nope.json"));
        Path file = dir.resolve("resume-test.json");
        new BuildCheckpoint(1, 1, 1L, "hash").save(file);
        BuildCheckpoint.delete(file);
        assertFalse(Files.exists(file));
    }

    @Test
    void fingerprintIgnoresOrderButNotContent() {
        PlanConfig a = PlanConfig.defaults();
        PlanConfig reordered = new PlanConfig(5, Direction.Axis.Y, true, true, true,
            Set.of(), Set.of(Blocks.TALL_GRASS, Blocks.SHORT_GRASS), Set.of(Blocks.TNT),
            Map.of(), Set.of("waterlogged"));
        assertEquals(BuildCheckpoint.fingerprint(a, "house"), BuildCheckpoint.fingerprint(reordered, "house"),
            "the same blocks in another order are the same settings");

        assertFalse(BuildCheckpoint.fingerprint(a, "house").equals(BuildCheckpoint.fingerprint(a, "tower")),
            "another placement is another build");
    }

    @Test
    void changedPlanSettingsMakeTheCheckpointNotFit() {
        PlanConfig config = PlanConfig.defaults();
        BuildCheckpoint checkpoint = new BuildCheckpoint(3, 10, 1L, BuildCheckpoint.fingerprint(config, "house"));
        assertTrue(checkpoint.fits(config, "house"));

        PlanConfig otherCluster = new PlanConfig(8, config.layerAxis(), config.layerAscending(), config.additiveOnly(),
            config.ignoreAir(), config.skipIfWorldIs(), config.treatAsAir(), config.neverPlace(),
            config.substitutes(), config.ignoreProperties());
        assertFalse(checkpoint.fits(otherCluster, "house"), "cluster size changes the plan");

        PlanConfig otherSubstitutes = new PlanConfig(config.clusterSize(), config.layerAxis(), config.layerAscending(),
            config.additiveOnly(), config.ignoreAir(), config.skipIfWorldIs(), config.treatAsAir(), config.neverPlace(),
            Map.of(Blocks.STONE, List.of(Blocks.ANDESITE)), config.ignoreProperties());
        assertFalse(checkpoint.fits(otherSubstitutes, "house"), "a new substitute changes the plan");
    }

    @Test
    void fingerprintSurvivesAnotherSetImplementation() {
        PlanConfig immutable = PlanConfig.defaults();
        Set<Block> mutableAir = new LinkedHashSet<>(List.of(Blocks.TALL_GRASS, Blocks.SHORT_GRASS));
        PlanConfig copied = new PlanConfig(immutable.clusterSize(), immutable.layerAxis(), immutable.layerAscending(),
            immutable.additiveOnly(), immutable.ignoreAir(),
            new HashSet<>(immutable.skipIfWorldIs()), mutableAir, new HashSet<>(immutable.neverPlace()),
            new HashMap<>(immutable.substitutes()), new HashSet<>(immutable.ignoreProperties()));

        assertEquals(BuildCheckpoint.fingerprint(immutable, "house"), BuildCheckpoint.fingerprint(copied, "house"),
            "the collection type must not show up in the fingerprint");
    }
}
