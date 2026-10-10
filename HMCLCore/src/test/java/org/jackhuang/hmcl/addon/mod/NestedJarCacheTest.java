/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.addon.mod;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies format-v6 JIJ cache round trips and corrupt-cache tolerance.
@NotNullByDefault
public final class NestedJarCacheTest {
    /// Persists every relation and JarJar selection field without changing the immutable tree.
    @Test
    public void testRoundTrip(@TempDir Path tempDirectory) {
        NestedJarInspector.NestedJar node = new NestedJarInspector.NestedJar(
                "META-INF/jarjar/library.jar",
                "library.jar",
                "library",
                "Library",
                "2.4.0",
                ModLoaderType.FORGE,
                "[1.20,1.21)",
                true,
                List.of(new ModDependency("api", "[2,3)", false, ModLoaderType.FORGE)),
                Map.of("library-api", "2.4.0"),
                List.of(new ModConflict("bad", "[1,2)", true, ModLoaderType.FORGE)),
                "example:library",
                "[2,3)",
                "2.4.0",
                List.of());
        Path cache = tempDirectory.resolve("jij-cache.json");
        Map<String, NestedJarCache.Entry> expected = Map.of(
                "library.jar", new NestedJarCache.Entry(123L, 456L, "FORGE", List.of(node)));

        NestedJarCache.save(cache, expected);
        assertEquals(expected, NestedJarCache.load(cache));
    }

    /// Treats malformed data as a cache miss instead of failing analysis.
    @Test
    public void testCorruptCache(@TempDir Path tempDirectory) throws Exception {
        Path cache = tempDirectory.resolve("jij-cache.json");
        Files.writeString(cache, "not-json");
        assertTrue(NestedJarCache.load(cache).isEmpty());
    }

    /// Rejects cache keys that would escape the instance mods directory.
    @Test
    public void testRejectsTraversalKey(@TempDir Path tempDirectory) throws Exception {
        Path cache = tempDirectory.resolve("jij-cache.json");
        Files.writeString(cache, """
                {"formatVersion":7,"entries":[{"path":"../outside.jar","lastModified":1,"size":1,"loaderKey":"FABRIC","tree":[]}]}
                """);
        assertTrue(NestedJarCache.load(cache).isEmpty());
    }

    /// Rejects a structurally incomplete tree instead of publishing it as a complete cache hit.
    @Test
    public void testRejectsMalformedTree(@TempDir Path tempDirectory) throws Exception {
        Path cache = tempDirectory.resolve("jij-cache.json");
        Files.writeString(cache, """
                {"formatVersion":7,"entries":[{"path":"mod.jar","lastModified":1,"size":1,"loaderKey":"FABRIC","tree":[1]}]}
                """);
        assertTrue(NestedJarCache.load(cache).isEmpty());
    }

    /// Rejects a cached tree deeper than the scanner could have produced.
    @Test
    public void testRejectsOverDepthTree(@TempDir Path tempDirectory) throws Exception {
        Path cache = tempDirectory.resolve("jij-cache.json");
        String node = "{\"path\":\"leaf.jar\",\"fileName\":\"leaf.jar\",\"loader\":\"UNKNOWN\"}";
        for (int depth = 0; depth < NestedJarInspector.MAX_DEPTH; depth++) {
            node = "{\"path\":\"child.jar\",\"fileName\":\"child.jar\",\"loader\":\"UNKNOWN\",\"children\":["
                    + node + "]}";
        }
        Files.writeString(cache, "{\"formatVersion\":7,\"entries\":[{\"path\":\"mod.jar\","
                + "\"lastModified\":1,\"size\":1,\"loaderKey\":\"FABRIC\",\"tree\":[" + node + "]}]}");

        assertTrue(NestedJarCache.load(cache).isEmpty());
    }

    /// Rejects a cached tree containing more nodes than a production scan allows.
    @Test
    public void testRejectsOverNodeLimitTree(@TempDir Path tempDirectory) throws Exception {
        Path cache = tempDirectory.resolve("jij-cache.json");
        String node = "{\"path\":\"child.jar\",\"fileName\":\"child.jar\",\"loader\":\"UNKNOWN\"}";
        String tree = String.join(",", java.util.Collections.nCopies(NestedJarInspector.MAX_NODES + 1, node));
        Files.writeString(cache, "{\"formatVersion\":7,\"entries\":[{\"path\":\"mod.jar\","
                + "\"lastModified\":1,\"size\":1,\"loaderKey\":\"FABRIC\",\"tree\":[" + tree + "]}]}");

        assertTrue(NestedJarCache.load(cache).isEmpty());
    }
}
