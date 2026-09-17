package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-07: the substitutes setting, one line per target block. */
class SubstitutesTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void parsesNamespacedAndPlainIds() {
        Substitutes.Parsed parsed = Substitutes.parse(List.of(
            "stone->cobblestone,andesite",
            " minecraft:oak_log -> minecraft:birch_log "));

        assertEquals(Map.of(
            Blocks.STONE, List.of(Blocks.COBBLESTONE, Blocks.ANDESITE),
            Blocks.OAK_LOG, List.of(Blocks.BIRCH_LOG)), parsed.map());
        assertTrue(parsed.errors().isEmpty());
    }

    @Test
    void badLinesAreReportedAndSkippedWithoutLosingTheRest() {
        Substitutes.Parsed parsed = Substitutes.parse(List.of(
            "stone->cobblestone",
            "not_a_block->stone",
            "stone->no_such_block",
            "missing arrow",
            "stone->",
            ""));

        assertEquals(Map.of(Blocks.STONE, List.of(Blocks.COBBLESTONE)), parsed.map());
        assertEquals(4, parsed.errors().size(), parsed.errors().toString());
        assertTrue(parsed.errors().getFirst().contains("not_a_block"), parsed.errors().toString());
        assertTrue(parsed.errors().stream().anyMatch(e -> e.contains("expected")), parsed.errors().toString());
    }

    @Test
    void airIsAValidTargetButUnknownIdsAreNot() {
        assertEquals(Map.of(Blocks.AIR, List.of(Blocks.STONE)), Substitutes.parse(List.of("air->stone")).map());
        assertTrue(Substitutes.parse(List.of("stone->minecraft:definitely_not_here")).map().isEmpty());
    }
}
