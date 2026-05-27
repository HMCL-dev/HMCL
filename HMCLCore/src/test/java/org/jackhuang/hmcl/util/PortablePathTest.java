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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for [PortablePath].
@NotNullByDefault
public final class PortablePathTest {
    /// Tests separator canonicalization and absolute-path detection.
    @Test
    public void canonicalizesSeparators() {
        assertSame(PortablePath.EMPTY, PortablePath.of(""));
        assertEquals("a/b/c", PortablePath.of("a\\b//c/").toString());
        assertEquals("/a/b", PortablePath.of("///a//b/").toString());
        assertEquals("C:\\Users\\Name", PortablePath.of("C:\\Users\\Name\\").toString());
        assertEquals("C:\\Users\\Name", PortablePath.of("C:/Users/Name/").toString());
        assertEquals("\\Users\\Name", PortablePath.of("\\Users\\Name\\").toString());
        assertEquals("\\\\server\\share\\file", PortablePath.of("\\\\server\\share\\file").toString());
        assertEquals("\\\\server\\share\\file", PortablePath.of("//server/share/file").toString());

        assertFalse(PortablePath.of("a/b").isAbsolute());
        assertTrue(PortablePath.of("/a/b").isAbsolute());
        assertTrue(PortablePath.of("C:\\Users\\Name").isAbsolute());
        assertTrue(PortablePath.of("\\Users\\Name").isAbsolute());
        assertTrue(PortablePath.of("\\\\server\\share\\file").isAbsolute());
    }

    /// Tests root, parent, file name, and name element accessors.
    @Test
    public void readsPathElements() {
        PortablePath path = PortablePath.of("/a/b/c");

        assertEquals(PortablePath.of("/"), path.getRoot());
        assertEquals(PortablePath.of("/a/b"), path.getParent());
        assertEquals(PortablePath.of("c"), path.getFileName());
        assertEquals(3, path.getNameCount());
        assertEquals(PortablePath.of("b"), path.getName(1));
        assertEquals(List.of("a", "b", "c"), path.getNames());

        assertNull(PortablePath.of("/").getParent());
        assertNull(PortablePath.of("/").getFileName());
        assertNull(PortablePath.of("a").getParent());
    }

    /// Tests lexical resolution operations.
    @Test
    public void resolvesPaths() {
        assertEquals(PortablePath.of("a/b/c"), PortablePath.of("a/b").resolve("c"));
        assertEquals(PortablePath.of("/c"), PortablePath.of("a/b").resolve("/c"));
        assertEquals(PortablePath.of("/a"), PortablePath.of("/").resolve("a"));
        assertEquals(PortablePath.of("C:\\a\\b\\c\\d"), PortablePath.of("C:\\a\\b").resolve("c/d"));
        assertEquals(PortablePath.of("a/c"), PortablePath.of("a/b").resolveSibling("c"));
        assertEquals(PortablePath.of("c"), PortablePath.of("a").resolveSibling("c"));
    }

    /// Tests lexical `.` and `..` normalization.
    @Test
    public void normalizesDotSegments() {
        assertEquals(PortablePath.of("a/c"), PortablePath.of("a/./b/../c").normalize());
        assertEquals(PortablePath.of(".."), PortablePath.of("../a/..").normalize());
        assertEquals(PortablePath.of("/a"), PortablePath.of("/../a").normalize());
        assertEquals(PortablePath.of("C:\\"), PortablePath.of("C:\\a\\..").normalize());
    }

    /// Tests prefix, suffix, and relativization operations.
    @Test
    public void comparesAndRelativizesPaths() {
        PortablePath path = PortablePath.of("/a/b/c");

        assertTrue(path.startsWith("/a"));
        assertFalse(path.startsWith("/a/bc"));
        assertTrue(path.endsWith("b/c"));
        assertFalse(path.endsWith("/b/c"));
        assertEquals(PortablePath.of("../d/e"), PortablePath.of("/a/b/c").relativize(PortablePath.of("/a/b/d/e")));
        assertTrue(PortablePath.of("C:\\a\\b\\c").endsWith("b/c"));
        assertThrows(IllegalArgumentException.class,
                () -> PortablePath.of("/a").relativize(PortablePath.of("a")));
    }

    /// Tests JSON serialization as a canonical string.
    @Test
    public void serializesAsJsonString() {
        PortablePath path = PortablePath.of("a\\b");

        assertEquals("\"a/b\"", JsonUtils.GSON.toJson(path, PortablePath.class));
        assertEquals(path, JsonUtils.GSON.fromJson("\"a/b\"", PortablePath.class));
    }
}
