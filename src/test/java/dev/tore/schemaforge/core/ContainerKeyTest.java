package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-02: which half of a double chest counts, and what the index file of a world is called. */
class ContainerKeyTest {
    @Test
    void bothHalvesOfADoubleChestPickTheSameEntry() {
        BlockPos west = new BlockPos(10, 64, 5);
        BlockPos east = new BlockPos(11, 64, 5);

        assertEquals(west, ContainerKey.canonical(west, Optional.of(east)));
        assertEquals(west, ContainerKey.canonical(east, Optional.of(west)), "opening the other half is the same chest");
    }

    @Test
    void theOrderIsTotalSoEveryAxisDecides() {
        BlockPos base = new BlockPos(5, 64, 5);
        assertEquals(base, ContainerKey.canonical(base, Optional.of(new BlockPos(5, 64, 6))), "lower z wins");
        assertEquals(base, ContainerKey.canonical(base, Optional.of(new BlockPos(5, 65, 5))), "lower y wins");
        assertEquals(base, ContainerKey.canonical(base, Optional.of(new BlockPos(6, 64, 5))), "lower x wins");
    }

    @Test
    void aSingleChestIsItsOwnPosition() {
        BlockPos pos = new BlockPos(1, 2, 3);
        assertEquals(pos, ContainerKey.canonical(pos, Optional.empty()));
    }

    @Test
    void theEnderChestSentinelCannotBeARealBlock() {
        // Below every world, so no chest anyone places can ever collide with it.
        assertTrue(ContainerKey.ENDER_CHEST.getY() < -2048);
    }

    @Test
    void theFileNameCarriesAHashNotTheAddress() {
        String hash = ContainerKey.serverHash(Optional.of("mc.example.com"));
        String name = ContainerKey.fileName(hash, "overworld");

        assertEquals("containers-" + hash + "-overworld.json", name);
        assertFalse(name.contains("example"), "the raw address never reaches the file name");
        assertEquals(8, hash.length(), "4 bytes as hex");
    }

    @Test
    void theSameAddressAlwaysGivesTheSameHash() {
        assertEquals(ContainerKey.serverHash(Optional.of("mc.example.com")),
            ContainerKey.serverHash(Optional.of("MC.Example.com")), "case does not make it another server");
        assertNotEquals(ContainerKey.serverHash(Optional.of("a.example.com")),
            ContainerKey.serverHash(Optional.of("b.example.com")));
    }

    @Test
    void withoutAServerTheFileIsLocal() {
        assertEquals(ContainerKey.LOCAL, ContainerKey.serverHash(Optional.empty()));
        assertEquals(ContainerKey.LOCAL, ContainerKey.serverHash(Optional.of("  ")));
        assertEquals("containers-local-the_nether.json", ContainerKey.fileName(ContainerKey.LOCAL, "the_nether"));
    }

    @Test
    void oddDimensionNamesCannotEscapeTheFileName() {
        assertEquals("containers-local-a_b_c.json", ContainerKey.fileName(ContainerKey.LOCAL, "a/b:c"));
        assertEquals("containers-local-unknown.json", ContainerKey.fileName(ContainerKey.LOCAL, " "));
    }
}
