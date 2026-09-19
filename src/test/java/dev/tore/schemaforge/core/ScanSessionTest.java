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

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-03: the scan walks every container, opens it paced, and gives up on one that will not open. */
class ScanSessionTest {
    private static final BlockPos A = new BlockPos(2, 64, 0);
    private static final BlockPos B = new BlockPos(9, 64, 0);
    private static final BlockPos C = new BlockPos(30, 64, 0);

    @Test
    void theRouteAlwaysTakesTheNearestContainerLeft() {
        List<BlockPos> route = ScanSession.route(List.of(C, B, A), new Vec3(0, 64, 0));
        assertEquals(List.of(A, B, C), route);

        // From the far end the order turns around.
        assertEquals(List.of(C, B, A), ScanSession.route(List.of(A, B, C), new Vec3(40, 64, 0)));
    }

    @Test
    void theRouteDropsDuplicates() {
        assertEquals(List.of(A, B), ScanSession.route(List.of(A, B, A), new Vec3(0, 64, 0)));
    }

    @Test
    void everyContainerIsOpenedOnceAndLearned() {
        Fixture f = new Fixture(List.of(A, B));
        f.player.eyePos = new Vec3(2, 64.5, 0);
        f.session.start();

        f.runUntilDone(400);

        assertEquals(List.of(A, B), f.actions.opened, "each container opened exactly once");
        assertEquals(2, f.session.learnedCount());
        assertEquals(List.of(), f.session.failedContainers());
        assertEquals(2, f.actions.closed, "the screen is closed again each time");
    }

    @Test
    void openingIsPacedByTheInterval() {
        Fixture f = new Fixture(List.of(A));
        f.player.eyePos = new Vec3(2, 64.5, 0);
        // Never learns, so the session keeps waiting and we can watch the open attempts.
        f.actions.learnOnOpen = false;
        f.session.start();

        for (int i = 0; i < 9; i++) f.session.tick(f.player);
        assertEquals(1, f.actions.opened.size(), "one attempt per interval, not one per tick");
    }

    @Test
    void aContainerThatNeverOpensIsSkippedAfterTheTimeout() {
        Fixture f = new Fixture(List.of(A, B));
        f.player.eyePos = new Vec3(2, 64.5, 0);
        f.actions.learnOnOpen = false;
        f.session.start();

        // The first target times out, then the second one is tried and also times out.
        f.runUntilDone(1_000);

        assertEquals(List.of(A, B), f.session.failedContainers());
        assertEquals(0, f.session.learnedCount());
        assertTrue(f.notes.stream().anyMatch(note -> note.contains("did not open")), f.notes.toString());
    }

    @Test
    void anUnreachableContainerIsSkippedAndTheScanGoesOn() {
        Fixture f = new Fixture(List.of(C, A));
        // Standing next to A but far from C; the pathfinder finds no way to C.
        f.player.eyePos = new Vec3(2, 64.5, 0);
        f.pathing.pathFound = false;
        f.session.start();

        f.runUntilDone(1_000);

        assertEquals(List.of(C), f.session.failedContainers(), "only the one out of reach");
        assertEquals(List.of(A), f.actions.opened);
        assertTrue(f.notes.stream().anyMatch(note -> note.contains("cannot be reached")), f.notes.toString());
    }

    @Test
    void cancellingHandsBackThePathAndTheScreen() {
        Fixture f = new Fixture(List.of(C));
        f.session.start();
        f.session.tick(f.player);
        f.actions.screenOpen = true;

        f.session.cancel();

        assertEquals(ScanSession.State.DONE, f.session.state());
        assertEquals(1, f.actions.closed);
        assertTrue(f.pathing.stops > 0);
    }

    @Test
    void anEmptyListIsDoneAtOnce() {
        Fixture f = new Fixture(List.of());
        f.session.start();
        assertEquals(ScanSession.State.DONE, f.session.state());
        assertEquals(0, f.session.total());
    }

    private static final class Fixture {
        final AtomicLong clock = new AtomicLong(1_000_000);
        final NavigatorTest.FakePathing pathing = new NavigatorTest.FakePathing();
        final NavigatorTest.MutablePlayer player = new NavigatorTest.MutablePlayer(new Vec3(0, 64.5, 0));
        final FakeActions actions = new FakeActions();
        final List<String> notes = new ArrayList<>();
        final ScanSession session;

        Fixture(List<BlockPos> targets) {
            Navigator navigator = new Navigator(pathing, clock::get);
            session = new ScanSession(targets, navigator, actions, ScanSession.Config.defaults(), notes::add);
        }

        /** Ticks until the scan is over; the clock moves so travel timeouts can fire. */
        void runUntilDone(int maxTicks) {
            for (int i = 0; i < maxTicks && session.state() != ScanSession.State.DONE; i++) {
                clock.addAndGet(100);
                // Walk to whatever the session is heading for, so travel can succeed.
                session.target().ifPresent(pos -> {
                    if (pathing.pathFound) player.eyePos = Vec3.atCenterOf(pos).add(0, 0.5, 0);
                });
                session.tick(player);
            }
            assertEquals(ScanSession.State.DONE, session.state(), "scan finished within " + maxTicks + " ticks");
        }
    }

    /** Learns the moment a container is opened, unless told otherwise. */
    private static final class FakeActions implements ScanSession.Actions {
        final List<BlockPos> opened = new ArrayList<>();
        final Set<BlockPos> learned = new LinkedHashSet<>();
        boolean learnOnOpen = true;
        boolean screenOpen;
        int closed;

        @Override
        public void open(BlockPos pos) {
            opened.add(pos);
            screenOpen = true;
            if (learnOnOpen) learned.add(pos);
        }

        @Override
        public void close() {
            closed++;
            screenOpen = false;
        }

        @Override
        public boolean screenOpen() {
            return screenOpen;
        }

        @Override
        public boolean learned(BlockPos pos) {
            return learned.contains(pos);
        }
    }
}
