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
package dev.tore.schemaforge.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-01 AK2: the budget reset is an Orbit handler on Meteor's TickEvent.Pre. Checked without creating the module (needs a client). */
class SchemaPrinterTest {
    @Test
    void tickHookListensToTickEventPre() throws ReflectiveOperationException {
        Class<?> module = Class.forName("dev.tore.schemaforge.modules.SchemaPrinter", false, getClass().getClassLoader());
        Method hook = module.getDeclaredMethod("onTickPre", TickEvent.Pre.class);
        assertTrue(hook.isAnnotationPresent(EventHandler.class));
    }

    /** P2-06: the AdditiveOnlyGuard is engaged on activation and released on deactivation. */
    @Test
    void activationHooksAreOverridden() throws ReflectiveOperationException {
        Class<?> module = Class.forName("dev.tore.schemaforge.modules.SchemaPrinter", false, getClass().getClassLoader());
        assertEquals(module, module.getMethod("onActivate").getDeclaringClass());
        assertEquals(module, module.getMethod("onDeactivate").getDeclaringClass());
    }

    /** P2-07: toggle() is overridden so a run only starts from a real toggle, not from Meteor's re-activation on world join. */
    @Test
    void toggleIsOverriddenToTellRealTogglesApart() throws ReflectiveOperationException {
        Class<?> module = Class.forName("dev.tore.schemaforge.modules.SchemaPrinter", false, getClass().getClassLoader());
        assertEquals(module, module.getMethod("toggle").getDeclaringClass());
    }
}
