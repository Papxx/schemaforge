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
package dev.tore.schemaforge.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves methods of optional mods as {@link MethodHandle}s from class and type names only,
 * so nothing here links against the mod's classes (hard rules 2 and 3). Added in P0-05.
 */
final class SignatureCheck {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
        "void", void.class,
        "boolean", boolean.class,
        "int", int.class,
        "long", long.class,
        "double", double.class,
        "float", float.class
    );

    private SignatureCheck() {
    }

    /**
     * A method signature. {@code names} holds alternative method names tried in order,
     * for methods Litematica has renamed between versions.
     */
    record Spec(String owner, boolean isStatic, String returnType, List<String> names, List<String> params) {
        Spec {
            if (names.isEmpty()) throw new IllegalArgumentException("Spec needs at least one method name");
            names = List.copyOf(names);
            params = List.copyOf(params);
        }

        static Spec virtual(String owner, String returnType, String name, String... params) {
            return new Spec(owner, false, returnType, List.of(name), List.of(params));
        }

        static Spec staticMethod(String owner, String returnType, String name, String... params) {
            return new Spec(owner, true, returnType, List.of(name), List.of(params));
        }

        /** {@code Owner.name(Param)} with simple class names, e.g. {@code LitematicaSchematic.getAreaSize(String)}. */
        String describe() {
            List<String> simpleParams = params.stream().map(SignatureCheck::simpleName).toList();
            return simpleName(owner) + "." + names.getFirst() + "(" + String.join(", ", simpleParams) + ")";
        }
    }

    /** Outcome of {@link #resolve}: either a handle plus the name that matched, or a problem description. */
    record Result(Optional<MethodHandle> handle, String resolvedName, String problem) {
        static Result found(MethodHandle handle, String name) {
            return new Result(Optional.of(handle), name, "");
        }

        static Result failed(String problem) {
            return new Result(Optional.empty(), "", problem);
        }

        boolean isFound() {
            return handle.isPresent();
        }
    }

    static Result resolve(ClassLoader loader, Spec spec) {
        Optional<Class<?>> owner = load(loader, spec.owner());
        if (owner.isEmpty()) return Result.failed("class " + spec.owner() + " not found");

        List<String> typeNames = new ArrayList<>(spec.params().size() + 1);
        typeNames.add(spec.returnType());
        typeNames.addAll(spec.params());
        List<Class<?>> types = new ArrayList<>(typeNames.size());
        for (String typeName : typeNames) {
            Optional<Class<?>> type = load(loader, typeName);
            if (type.isEmpty()) return Result.failed("type " + typeName + " not found");
            types.add(type.get());
        }
        MethodType methodType = MethodType.methodType(types.getFirst(), types.subList(1, types.size()));

        for (String name : spec.names()) {
            try {
                MethodHandle handle = spec.isStatic()
                    ? LOOKUP.findStatic(owner.get(), name, methodType)
                    : LOOKUP.findVirtual(owner.get(), name, methodType);
                return Result.found(handle, name);
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                // try the next alternative name
            }
        }
        String kind = spec.isStatic() ? "static " : "";
        return Result.failed("no " + kind + "method " + String.join(" | ", spec.names()) + methodType);
    }

    /** Getter handle for a public static field. */
    static Result resolveStaticField(ClassLoader loader, String owner, String name, String type) {
        Optional<Class<?>> ownerClass = load(loader, owner);
        if (ownerClass.isEmpty()) return Result.failed("class " + owner + " not found");
        Optional<Class<?>> fieldType = load(loader, type);
        if (fieldType.isEmpty()) return Result.failed("type " + type + " not found");
        try {
            return Result.found(LOOKUP.findStaticGetter(ownerClass.get(), name, fieldType.get()), name);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return Result.failed("no static field " + name + " of type " + simpleName(type));
        }
    }

    /** Loads a class without initializing it; primitives are accepted by name. */
    static Optional<Class<?>> load(ClassLoader loader, String name) {
        Class<?> primitive = PRIMITIVES.get(name);
        if (primitive != null) return Optional.of(primitive);
        try {
            return Optional.of(Class.forName(name, false, loader));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    static String simpleName(String className) {
        String withoutPackage = className.substring(className.lastIndexOf('.') + 1);
        return withoutPackage.substring(withoutPackage.lastIndexOf('$') + 1);
    }
}
