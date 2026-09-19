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

import dev.tore.schemaforge.core.view.PlayerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3-02: travel to one target – arrival, timeout, blacklist after three tries, behaviour without a pathfinder. */
class NavigatorTest {
    private static final BlockPos TARGET = new BlockPos(100, 64, 100);
    private static final Vec3 FAR = new Vec3(0, 65, 0);
    private static final Vec3 CLOSE = new Vec3(100.5, 65.6, 97.5);

    @Test
    void arrivingCancelsThePath() {
        Fixture f = new Fixture();
        assertTrue(f.navigator.start(TARGET));
        assertEquals(List.of(TARGET), f.pathing.goals);
        assertEquals(Navigator.GOAL_RADIUS, f.pathing.lastRadius);

        f.player.eyePos = FAR;
        assertEquals(Navigator.Progress.TRAVELING, f.navigator.tick(f.player));
        f.player.eyePos = CLOSE;
        assertEquals(Navigator.Progress.ARRIVED, f.navigator.tick(f.player));
        assertEquals(1, f.pathing.stops, "the pathfinder is handed back on arrival");
        assertEquals(0, f.navigator.attempts(TARGET), "arriving is not a failed attempt");
    }

    @Test
    void notPathingAfterTheGracePeriodFails() {
        Fixture f = new Fixture();
        f.player.eyePos = FAR;
        f.pathing.pathFound = false;
        f.navigator.start(TARGET);

        assertEquals(Navigator.Progress.TRAVELING, f.navigator.tick(f.player), "the pathfinder may need a few ticks");
        f.clock.addAndGet(2_000);
        assertEquals(Navigator.Progress.FAILED, f.navigator.tick(f.player));
        assertEquals(1, f.navigator.attempts(TARGET));
        assertFalse(f.navigator.blacklisted(TARGET));
    }

    @Test
    void stillPathingAfterTheTimeoutFails() {
        Fixture f = new Fixture();
        f.player.eyePos = FAR;
        f.navigator.start(TARGET);

        f.clock.addAndGet(Navigator.TIMEOUT_MS - 1);
        assertEquals(Navigator.Progress.TRAVELING, f.navigator.tick(f.player));
        f.clock.addAndGet(1);
        assertEquals(Navigator.Progress.FAILED, f.navigator.tick(f.player));
    }

    @Test
    void thirdFailureBlacklistsTheTarget() {
        Fixture f = new Fixture();
        f.player.eyePos = FAR;
        f.pathing.pathFound = false;

        for (int attempt = 1; attempt <= Navigator.MAX_ATTEMPTS; attempt++) {
            assertTrue(f.navigator.start(TARGET), "attempt " + attempt + " still allowed");
            f.clock.addAndGet(2_000);
            assertEquals(Navigator.Progress.FAILED, f.navigator.tick(f.player));
        }

        assertEquals(Navigator.MAX_ATTEMPTS, f.navigator.attempts(TARGET));
        assertTrue(f.navigator.blacklisted(TARGET));
        assertFalse(f.navigator.start(TARGET), "a blacklisted target is never approached again");
        assertEquals(Navigator.MAX_ATTEMPTS, f.pathing.goals.size(), "no cluster is walked to more than three times");
    }

    @Test
    void withoutAPathfinderNothingIsStarted() {
        Fixture f = new Fixture();
        f.pathing.present = false;

        assertFalse(f.navigator.available());
        assertFalse(f.navigator.start(TARGET));
        assertEquals(List.of(), f.pathing.goals);
        assertEquals(Navigator.Progress.FAILED, f.navigator.tick(f.player), "no target, nothing to walk to");
        f.navigator.cancel();
        assertEquals(0, f.pathing.stops);
    }

    private static final class Fixture {
        final AtomicLong clock = new AtomicLong(1_000_000);
        final FakePathing pathing = new FakePathing();
        final MutablePlayer player = new MutablePlayer(FAR);
        final Navigator navigator = new Navigator(pathing, clock::get);
    }

    /** Records goals and stops; {@code pathFound} false means the pathfinder never starts walking. */
    static final class FakePathing implements Navigator.Pathing {
        final List<BlockPos> goals = new ArrayList<>();
        boolean present = true;
        boolean pathFound = true;
        int lastRadius;
        int stops;
        private boolean pathing;

        @Override
        public boolean isPresent() {
            return present;
        }

        @Override
        public void gotoNear(BlockPos pos, int radius) {
            goals.add(pos);
            lastRadius = radius;
            pathing = pathFound;
        }

        @Override
        public boolean isPathing() {
            return pathing;
        }

        @Override
        public void stop() {
            stops++;
            pathing = false;
        }
    }

    /** A player that can be moved between ticks. */
    static final class MutablePlayer implements PlayerView {
        Vec3 eyePos;

        MutablePlayer(Vec3 eyePos) {
            this.eyePos = eyePos;
        }

        @Override
        public Vec3 eyePos() {
            return eyePos;
        }

        @Override
        public float yaw() {
            return 0;
        }

        @Override
        public float pitch() {
            return 0;
        }

        @Override
        public double reach() {
            return 4.5;
        }

        @Override
        public boolean hasLineOfSight(Vec3 target) {
            return true;
        }
    }
}
