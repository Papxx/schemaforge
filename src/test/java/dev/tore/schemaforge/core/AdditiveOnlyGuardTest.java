package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-06: Baritone break settings are forbidden on start and restored as the same objects on stop (AK2 without a client). */
class AdditiveOnlyGuardTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void engageForbidsBreakingAndReleaseRestoresSameObjects() {
        List<Block> anyway = new ArrayList<>(List.of(Blocks.SHORT_GRASS));
        Boolean allow = Boolean.TRUE;
        FakeSettings baritone = new FakeSettings(allow, anyway);
        AdditiveOnlyGuard guard = new AdditiveOnlyGuard(baritone);

        guard.engage(true);
        assertTrue(guard.engaged());
        assertFalse(baritone.allowBreak);
        assertTrue(baritone.allowBreakAnyway.isEmpty());

        guard.release();
        assertFalse(guard.engaged());
        // Baritone's #modified compares by reference: restoring copies would show up as SchemaForge changes.
        assertSame(allow, baritone.allowBreak);
        assertSame(anyway, baritone.allowBreakAnyway);
    }

    @Test
    void secondEngageKeepsTheOriginalSettings() {
        List<Block> anyway = new ArrayList<>();
        FakeSettings baritone = new FakeSettings(true, anyway);
        AdditiveOnlyGuard guard = new AdditiveOnlyGuard(baritone);

        guard.engage(true);
        guard.engage(true);
        guard.release();
        assertTrue(baritone.allowBreak);
        assertSame(anyway, baritone.allowBreakAnyway);
        assertEquals(2, baritone.writes);
    }

    @Test
    void userChoicesBeforeStartAreRestored() {
        FakeSettings baritone = new FakeSettings(false, new ArrayList<>(List.of(Blocks.DIRT)));
        List<Block> anyway = baritone.allowBreakAnyway;
        AdditiveOnlyGuard guard = new AdditiveOnlyGuard(baritone);

        guard.engage(true);
        guard.release();
        assertFalse(baritone.allowBreak);
        assertSame(anyway, baritone.allowBreakAnyway);
        assertEquals(List.of(Blocks.DIRT), anyway);
    }

    @Test
    void doesNothingWithoutAdditiveOnlyOrWithoutBaritone() {
        FakeSettings baritone = new FakeSettings(true, new ArrayList<>());
        AdditiveOnlyGuard off = new AdditiveOnlyGuard(baritone);
        off.engage(false);
        off.release();
        assertFalse(off.engaged());
        assertEquals(0, baritone.writes);

        FakeSettings missing = new FakeSettings(true, new ArrayList<>());
        missing.present = false;
        AdditiveOnlyGuard absent = new AdditiveOnlyGuard(missing);
        absent.engage(true);
        absent.release();
        assertFalse(absent.engaged());
        assertEquals(0, missing.reads + missing.writes);
    }

    @Test
    void releaseWithoutEngageWritesNothing() {
        FakeSettings baritone = new FakeSettings(true, new ArrayList<>());
        new AdditiveOnlyGuard(baritone).release();
        assertEquals(0, baritone.writes);
    }

    /** Stands in for Baritone's settings object; fields are replaced on write like {@code Setting.value}. */
    private static final class FakeSettings implements AdditiveOnlyGuard.PathfinderSettings {
        Boolean allowBreak;
        List<Block> allowBreakAnyway;
        boolean present = true;
        int reads;
        int writes;

        FakeSettings(Boolean allowBreak, List<Block> allowBreakAnyway) {
            this.allowBreak = allowBreak;
            this.allowBreakAnyway = allowBreakAnyway;
        }

        @Override
        public boolean isPresent() {
            return present;
        }

        @Override
        public AdditiveOnlyGuard.BreakSettings read() {
            reads++;
            return new AdditiveOnlyGuard.BreakSettings(allowBreak, allowBreakAnyway);
        }

        @Override
        public void write(AdditiveOnlyGuard.BreakSettings settings) {
            writes++;
            allowBreak = settings.allowBreak();
            allowBreakAnyway = settings.allowBreakAnyway();
        }
    }
}
