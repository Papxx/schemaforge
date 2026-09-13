package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.compat.SignatureCheck.Result;
import dev.tore.schemaforge.compat.SignatureCheck.Spec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P0-05: signature resolution without linking, checked against JDK classes. */
class SignatureCheckTest {
    private static final ClassLoader LOADER = SignatureCheckTest.class.getClassLoader();

    @Test
    void resolvesVirtualMethodWithParameter() throws Throwable {
        Result result = SignatureCheck.resolve(LOADER, Spec.virtual("java.lang.String", "boolean", "startsWith", "java.lang.String"));
        assertTrue(result.isFound());
        assertEquals(true, (boolean) result.handle().orElseThrow().invoke("schemaforge", "schema"));
    }

    @Test
    void resolvesStaticMethod() {
        assertTrue(SignatureCheck.resolve(LOADER, Spec.staticMethod("java.lang.String", "java.lang.String", "valueOf", "java.lang.Object")).isFound());
    }

    @Test
    void fallsBackToAlternativeName() {
        Spec spec = new Spec("java.util.List", false, "int", List.of("getAllSchematicsPlacements", "size"), List.of());
        Result result = SignatureCheck.resolve(LOADER, spec);
        assertTrue(result.isFound());
        assertEquals("size", result.resolvedName());
    }

    @Test
    void wrongReturnTypeFails() {
        Result result = SignatureCheck.resolve(LOADER, Spec.virtual("java.util.List", "long", "size"));
        assertFalse(result.isFound());
        assertTrue(result.problem().contains("size"), result.problem());
    }

    @Test
    void missingClassFailsWithoutThrowing() {
        Result result = SignatureCheck.resolve(LOADER, Spec.staticMethod("fi.dy.masa.litematica.data.DoesNotExist", "void", "x"));
        assertFalse(result.isFound());
        assertTrue(result.problem().contains("not found"), result.problem());
    }

    @Test
    void missingParameterTypeFails() {
        Result result = SignatureCheck.resolve(LOADER, Spec.virtual("java.lang.String", "int", "indexOf", "does.not.Exist"));
        assertFalse(result.isFound());
    }

    @Test
    void resolvesStaticField() throws Throwable {
        Result result = SignatureCheck.resolveStaticField(LOADER, "java.lang.Integer", "MAX_VALUE", "int");
        assertTrue(result.isFound());
        assertEquals(Integer.MAX_VALUE, (int) result.handle().orElseThrow().invoke());
        assertFalse(SignatureCheck.resolveStaticField(LOADER, "java.lang.Integer", "MAX_VALUE", "long").isFound());
    }

    @Test
    void describeUsesSimpleNames() {
        Spec spec = Spec.virtual("fi.dy.masa.litematica.schematic.LitematicaSchematic", "net.minecraft.core.BlockPos", "getAreaSize", "java.lang.String");
        assertEquals("LitematicaSchematic.getAreaSize(String)", spec.describe());
        assertEquals("Generic", SignatureCheck.simpleName("fi.dy.masa.litematica.config.Configs$Generic"));
    }
}
