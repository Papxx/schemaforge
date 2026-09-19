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

import dev.tore.schemaforge.core.view.SafetyView;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3-03: one reason per safety stop, and what it takes to continue. */
class SafetyMonitorTest {
    @Test
    void losingHealthPausesUntilResumedByHand() {
        Fixture f = new Fixture();
        assertEquals(Optional.empty(), f.check(), "the first check only remembers the health");
        assertEquals(Optional.empty(), f.check());

        f.view.health = 14;
        SafetyMonitor.Trigger trigger = f.check().orElseThrow();
        assertEquals(SafetyMonitor.Reason.DAMAGE, trigger.reason());
        assertFalse(trigger.autoResume(), "damage waits for .sf resume");
        assertTrue(trigger.message().contains("damage"), trigger.message());
        assertFalse(f.monitor.cleared(trigger, f.view, true, false));
    }

    @Test
    void healthThatStaysDownDoesNotPauseAgain() {
        Fixture f = new Fixture();
        f.check();
        f.view.health = 14;
        assertTrue(f.check().isPresent());
        // Resuming after damage compares against the health of the pausing tick, not the full bar.
        assertEquals(Optional.empty(), f.check());
        f.view.health = 20;
        assertEquals(Optional.empty(), f.check(), "healing is not damage");
    }

    @Test
    void damageIsIgnoredWhenTheSettingIsOff() {
        Fixture f = new Fixture();
        f.config = new SafetyMonitor.Config(false, 6, 16);
        f.check();
        f.view.health = 1;
        assertEquals(Optional.empty(), f.check());
    }

    @Test
    void hungerPausesAndContinuesAfterEating() {
        Fixture f = new Fixture();
        f.view.food = 5;
        SafetyMonitor.Trigger trigger = f.check().orElseThrow();
        assertEquals(SafetyMonitor.Reason.FOOD, trigger.reason());
        assertTrue(trigger.autoResume());

        assertFalse(f.monitor.cleared(trigger, f.view, true, false));
        f.view.food = 6;
        assertTrue(f.monitor.cleared(trigger, f.view, true, false), "min-food itself is enough");
    }

    @Test
    void aPlayerInsideTheRadiusPausesUntilTheyLeave() {
        Fixture f = new Fixture();
        f.view.nearest = OptionalDouble.of(16);
        SafetyMonitor.Trigger trigger = f.check().orElseThrow();
        assertEquals(SafetyMonitor.Reason.PLAYER_NEARBY, trigger.reason());
        assertTrue(trigger.autoResume());

        f.view.nearest = OptionalDouble.of(16.5);
        assertTrue(f.monitor.cleared(trigger, f.view, true, false));
        f.view.nearest = OptionalDouble.empty();
        assertTrue(f.monitor.cleared(trigger, f.view, true, false), "nobody around at all");
    }

    @Test
    void unloadedChunkAndEmptyInventoryPauseToo() {
        Fixture f = new Fixture();
        SafetyMonitor.Trigger chunk = f.monitor.check(f.view, false, false).orElseThrow();
        assertEquals(SafetyMonitor.Reason.CHUNK_UNLOADED, chunk.reason());
        assertTrue(f.monitor.cleared(chunk, f.view, true, false));

        SafetyMonitor.Trigger materials = f.monitor.check(f.view, true, true).orElseThrow();
        assertEquals(SafetyMonitor.Reason.NO_MATERIALS, materials.reason());
        assertTrue(f.monitor.cleared(materials, f.view, true, false));
    }

    @Test
    void damageWinsOverTheOtherReasons() {
        Fixture f = new Fixture();
        f.check();
        f.view.health = 10;
        f.view.food = 0;
        f.view.nearest = OptionalDouble.of(1);
        assertEquals(SafetyMonitor.Reason.DAMAGE, f.monitor.check(f.view, false, true).orElseThrow().reason());
    }

    private static final class Fixture {
        final FakeSafety view = new FakeSafety();
        SafetyMonitor.Config config = SafetyMonitor.Config.defaults();
        final SafetyMonitor monitor = new SafetyMonitor(() -> config);

        Optional<SafetyMonitor.Trigger> check() {
            return monitor.check(view, true, false);
        }
    }

    /** A healthy, well fed player standing alone. */
    static final class FakeSafety implements SafetyView {
        float health = 20;
        int food = 20;
        OptionalDouble nearest = OptionalDouble.empty();

        @Override
        public float health() {
            return health;
        }

        @Override
        public int food() {
            return food;
        }

        @Override
        public OptionalDouble nearestOtherPlayer() {
            return nearest;
        }
    }
}
