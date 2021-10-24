/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.io;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ZipPathTest {
    ZipFileSystemProvider provider = new ZipFileSystemProvider();
    ZipFileSystem zfs = new ZipFileSystem(provider, new ZipFile(new SeekableInMemoryByteChannel(IOUtils.readFullyAsByteArray(ZipPathTest.class.getResourceAsStream("/test.zip")))), true);

    public ZipPathTest() throws IOException {

    }

    private Path p(String path) {
        return zfs.getPath(path);
    }

    @Test
    public void testNormalizePath() throws IOException {
        BiConsumer<String, String> equals = (expected, actual) -> {
            assertEquals(zfs.getPath(expected), zfs.getPath(actual).toAbsolutePath().normalize());
        };

        BiConsumer<String, String> notEquals = (expected, actual) -> {
            assertNotEquals(zfs.getPath(expected), zfs.getPath(actual).toAbsolutePath().normalize());
        };

        equals.accept("/a/b/c/d", "/a\\b/c/d");
        equals.accept("/a/b/c/d", "/a\\b/c/d/");
        equals.accept("/a/b/c/d", "/a\\b/c//d");
        equals.accept("/a/b/c/d", "/a\\\\b/c/d");
        equals.accept("/a/b/c/d", "a/b/c/d");
        equals.accept("/a/b/c/d", "a/b/.c/../c/d");

        notEquals.accept("/a/b/c/d", "/a\\b/c");
    }

    @Test
    public void testRelativizePath() throws IOException {
        assertEquals(p("../../a/b/c"), p("/a/b/c/a/b/c").relativize(p("/a/b/c/d/e")));

        assertEquals(p("../.."), p("/a/b/c").relativize(p("/a/b/c/d/e")));
        assertEquals(p("../../"), p("/a/b/c").relativize(p("/a/b/c/d/e")));
        assertEquals(p("../../"), p("/a/b/c/").relativize(p("/a/b/c/d/e")));
        assertEquals(p("../.."), p("/a/b/c/").relativize(p("/a/b/c/d/e")));

        assertEquals(p(""), p("/a/b/c/").relativize(p("/a/b/c")));
        assertEquals(p(""), p("/a/b/c").relativize(p("/a/b/c")));
        assertEquals(p(""), p("/a/b/c").relativize(p("/a/b/c/")));
        assertEquals(p(""), p("/a/b/c/").relativize(p("/a/b/c/")));
    }
}
