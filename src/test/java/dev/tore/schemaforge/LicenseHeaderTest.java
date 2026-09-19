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
package dev.tore.schemaforge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P5-07: every Java source carries the GPL-3.0 header before its package line. */
class LicenseHeaderTest {
    private static final String SPDX = "SPDX-License-Identifier: GPL-3.0-only";

    @Test
    void everySourceFileStartsWithTheGplHeader() throws IOException {
        List<Path> sources;
        try (Stream<Path> main = Files.walk(Path.of("src/main/java")); Stream<Path> test = Files.walk(Path.of("src/test/java"))) {
            sources = Stream.concat(main, test).filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(sources.isEmpty(), "tests run from the project directory");
        List<Path> missing = sources.stream().filter(p -> !hasHeader(p)).toList();
        assertTrue(missing.isEmpty(), "missing GPL header: " + missing);
    }

    private static boolean hasHeader(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            int pkg = text.indexOf("package ");
            return text.startsWith("/*") && pkg > 0 && text.substring(0, pkg).contains(SPDX)
                && text.substring(0, pkg).contains("GNU General Public License");
        } catch (IOException e) {
            return false;
        }
    }
}
