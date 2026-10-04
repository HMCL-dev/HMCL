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
package org.jackhuang.hmcl.util.tree;

import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveOutputStream;
import kala.compress.archivers.tar.TarArchiveReader;
import kala.compress.archivers.tar.TarConstants;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies archive path traversal and handling of skipped entries.
@NotNullByDefault
public final class ArchiveFileTreeTest {

    /// Creates an empty TAR tree whose entries can be added directly by each test.
    private static TarFileTree openTree(Path directory) throws IOException {
        Path archive = directory.resolve("test.tar");
        try (var output = new TarArchiveOutputStream(Files.newOutputStream(archive))) {
            output.finish();
        }
        return new TarFileTree(new TarArchiveReader(archive), null);
    }

    /// Completes traversal when the final directory component is a dot, with or without a slash.
    @ParameterizedTest
    @ValueSource(strings = {".", "./", "a/.", "a/./"})
    public void testDotDirectoryTerminates(String name, @TempDir Path directory) throws IOException {
        try (var tree = openTree(directory)) {
            // A TAR directory flag does not require its name to end in a slash.
            var entry = new TarArchiveEntry(name, TarConstants.LF_DIR);
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ArchiveFileTree.addEntry(tree.getRoot(), entry));
            assertTrue(tree.getRoot().getFiles().isEmpty());
            if (name.startsWith("a/")) {
                assertEquals(1, tree.getRoot().getSubDirs().size());
                assertNotNull(tree.getDirectory("a"));
            } else {
                assertTrue(tree.getRoot().getSubDirs().isEmpty());
            }
        }
    }

    /// Preserves lookup of files beneath dot and empty directory components.
    @Test
    public void testNormalizedFilePath(@TempDir Path directory) throws IOException {
        try (var tree = openTree(directory)) {
            var entry = new TarArchiveEntry("./a//./file.txt");
            ArchiveFileTree.addEntry(tree.getRoot(), entry);
            assertSame(entry, tree.getEntry("a/file.txt"));
            assertTrue(tree.getRoot().getFiles().isEmpty());
        }
    }

    /// Skips the whole entry when a parent component is a file, retaining the existing file.
    @ParameterizedTest
    @ValueSource(strings = {"a/", "a/file.txt"})
    public void testFileConflictSkipsEntry(String name, @TempDir Path directory) throws IOException {
        try (var tree = openTree(directory)) {
            var existing = new TarArchiveEntry("a");
            ArchiveFileTree.addEntry(tree.getRoot(), existing);
            ArchiveFileTree.addEntry(tree.getRoot(), new TarArchiveEntry(name));
            assertSame(existing, tree.getEntry("a"));
            assertEquals(1, tree.getRoot().getFiles().size());
            assertTrue(tree.getRoot().getSubDirs().isEmpty());
        }
    }

    /// Preserves explicit and implicit directories when a file has the same name.
    @ParameterizedTest
    @ValueSource(strings = {"a/", "a/file.txt"})
    public void testDirectoryConflictSkipsFile(String name, @TempDir Path directory) throws IOException {
        try (var tree = openTree(directory)) {
            ArchiveFileTree.addEntry(tree.getRoot(), new TarArchiveEntry(name));
            ArchiveFileTree.addEntry(tree.getRoot(), new TarArchiveEntry("a"));
            assertNotNull(tree.getDirectory("a"));
            assertTrue(tree.getRoot().getFiles().isEmpty());
            if (name.endsWith("file.txt"))
                assertNotNull(tree.getEntry("a/file.txt"));
        }
    }

    /// Keeps the first file entry when another file has the same path.
    @Test
    public void testDuplicateFileIsSkipped(@TempDir Path directory) throws IOException {
        try (var tree = openTree(directory)) {
            var existing = new TarArchiveEntry("file.txt");
            ArchiveFileTree.addEntry(tree.getRoot(), existing);
            ArchiveFileTree.addEntry(tree.getRoot(), new TarArchiveEntry("file.txt"));
            assertSame(existing, tree.getEntry("file.txt"));
            assertEquals(1, tree.getRoot().getFiles().size());
        }
    }

    /// Skips entries containing parent directory components without remapping their files.
    @ParameterizedTest
    @ValueSource(strings = {"../file.txt", "a/../file.txt"})
    public void testParentDirectorySkipsEntry(String name, @TempDir Path directory) throws IOException {
        try (var tree = openTree(directory)) {
            ArchiveFileTree.addEntry(tree.getRoot(), new TarArchiveEntry(name));
            assertNull(tree.getEntry("file.txt"));
            assertNull(tree.getEntry("a/file.txt"));
            var valid = new TarArchiveEntry("valid.txt");
            ArchiveFileTree.addEntry(tree.getRoot(), valid);
            assertSame(valid, tree.getEntry("valid.txt"));
        }
    }
}
