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
