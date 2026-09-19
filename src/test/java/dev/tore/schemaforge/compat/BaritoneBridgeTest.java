package dev.tore.schemaforge.compat;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Baritone is compileOnly, so the test runtime is exactly the "Baritone missing" case (P3-01 AK2). */
class BaritoneBridgeTest {
    @Test
    void baritoneIsNotOnTestClasspath() {
        assertTrue(SignatureCheck.load(getClass().getClassLoader(), "baritone.api.BaritoneAPI").isEmpty());
    }

    @Test
    void withoutBaritoneNothingIsPresent() {
        assertFalse(BaritoneBridge.isPresent());
    }

    @Test
    void withoutBaritoneNavigationDoesNothing() {
        BlockPos target = new BlockPos(1, 2, 3);
        assertDoesNotThrow(() -> BaritoneBridge.gotoNear(target, 3));
        assertDoesNotThrow(() -> BaritoneBridge.gotoBlock(target));
        assertDoesNotThrow(BaritoneBridge::stop);
        assertFalse(assertDoesNotThrow(BaritoneBridge::isPathing));
    }
}
