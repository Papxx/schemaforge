package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3-05: the HUD lines - percent, blocks per minute, ETA, state and the top three shortages. */
class ProgressReportTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void withoutARunItSaysIdle() {
        assertEquals(List.of("SchemaForge: idle"), ProgressReport.lines(Optional.empty(), List.of(), false));
    }

    @Test
    void aRunningBuildShowsStatePercentRateAndEta() {
        List<String> lines = ProgressReport.lines(Optional.of(status(BuildSession.State.BUILDING, 210, 129, 0, 48)),
            List.of(), true);

        assertEquals(2, lines.size());
        assertEquals("BUILDING house 61%", lines.getFirst());
        assertEquals("210 placed, 129 left - 48/min - ETA 3m", lines.get(1));
    }

    @Test
    void percentCountsPlacedAgainstEverythingStillKnown() {
        assertEquals(0, ProgressReport.percent(status(BuildSession.State.BUILDING, 0, 100, 0, 0)));
        assertEquals(50, ProgressReport.percent(status(BuildSession.State.BUILDING, 50, 50, 0, 0)));
        // Floored, so 99% never shows up as a finished build.
        assertEquals(99, ProgressReport.percent(status(BuildSession.State.BUILDING, 999, 1, 0, 0)));
        assertEquals(100, ProgressReport.percent(status(BuildSession.State.DONE, 100, 0, 0, 0)));
        assertEquals(100, ProgressReport.percent(status(BuildSession.State.DONE, 0, 0, 0, 0)), "nothing to do is done");
    }

    @Test
    void etaNeedsBothRemainingBlocksAndARate() {
        assertEquals("-", ProgressReport.eta(status(BuildSession.State.DONE, 10, 0, 0, 30)));
        assertEquals("?", ProgressReport.eta(status(BuildSession.State.BUILDING, 10, 50, 0, 0)));
        assertEquals("5m", ProgressReport.eta(status(BuildSession.State.BUILDING, 10, 150, 0, 30)));
        assertEquals("1h00m", ProgressReport.eta(status(BuildSession.State.BUILDING, 10, 1800, 0, 30)));
        assertEquals("2h30m", ProgressReport.eta(status(BuildSession.State.BUILDING, 10, 4500, 0, 30)));
    }

    @Test
    void mismatchedBlocksGetTheirOwnLineOnlyWhenThereAreSome() {
        assertEquals(3, ProgressReport.lines(Optional.of(status(BuildSession.State.BUILDING, 1, 1, 4, 10)), List.of(), true).size());
        List<String> lines = ProgressReport.lines(Optional.of(status(BuildSession.State.BUILDING, 1, 1, 4, 10)), List.of(), true);
        assertEquals("4 mismatched", lines.get(2));
    }

    @Test
    void onlyTheThreeBiggestShortagesAreListed() {
        List<MaterialManager.Shortage> shortages = List.of(
            new MaterialManager.Shortage(Items.TORCH, 4),
            new MaterialManager.Shortage(Items.STONE, 64),
            new MaterialManager.Shortage(Items.OAK_PLANKS, 12),
            new MaterialManager.Shortage(Items.RAIL, 6));

        String line = ProgressReport.lines(Optional.of(status(BuildSession.State.BUILDING, 1, 1, 0, 10)), shortages, true).getLast();

        assertEquals("missing: 64x stone, 12x oak_planks, 6x rail +1 more", line, "biggest first, rest summed up");
    }

    @Test
    void aSingleShortageHasNoMoreSuffix() {
        String line = ProgressReport.lines(Optional.of(status(BuildSession.State.BUILDING, 1, 1, 0, 10)),
            List.of(new MaterialManager.Shortage(Items.STONE, 3)), true).getLast();
        assertEquals("missing: 3x stone", line);
        assertFalse(line.contains("more"));
    }

    @Test
    void aStoppedRunIsNotShownAsBuilding() {
        String line = ProgressReport.lines(Optional.of(status(BuildSession.State.BUILDING, 5, 5, 0, 10)), List.of(), false).getFirst();
        assertTrue(line.startsWith("STOPPED"), line);

        String done = ProgressReport.lines(Optional.of(status(BuildSession.State.DONE, 5, 0, 0, 10)), List.of(), false).getFirst();
        assertTrue(done.startsWith("DONE"), done);
    }

    private static BuildSession.Status status(BuildSession.State state, int placed, int remaining, int mismatched, double perMinute) {
        return new BuildSession.Status(state, "house", 1, 2, 9, placed, perMinute, mismatched, remaining);
    }
}
