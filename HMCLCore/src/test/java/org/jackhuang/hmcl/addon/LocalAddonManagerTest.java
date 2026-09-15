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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests the cached sorted view returned by [LocalAddonManager#getLocalFiles()].
///
/// The cache is the reason repeated UI reads no longer re-sort the whole file set, so the tests pin
/// both halves of the contract: an unchanged file set reuses the cached list, and every mutation
/// invalidates it.
@NotNullByDefault
public final class LocalAddonManagerTest {

    /// The sorted view is reused while the file set is unchanged and refreshed after a mutation.
    @Test
    public void testSortedViewIsCachedUntilFileSetChanges() throws IOException {
        TestManager manager = new TestManager();
        manager.insert(new TestAddonFile("b"));
        manager.insert(new TestAddonFile("a"));

        List<TestAddonFile> first = manager.getLocalFiles();
        assertEquals(List.of("a", "b"), names(first));
        assertSame(first, manager.getLocalFiles(), "an unchanged file set must reuse the cached view");

        manager.insert(new TestAddonFile("c"));
        List<TestAddonFile> second = manager.getLocalFiles();
        assertNotSame(first, second, "an inserted file must invalidate the cached view");
        assertEquals(List.of("a", "b", "c"), names(second));

        manager.remove(second.get(1));
        assertEquals(List.of("a", "c"), names(manager.getLocalFiles()));

        manager.clear();
        assertTrue(manager.getLocalFiles().isEmpty());
    }

    /// Adding a duplicate leaves the file set unchanged and keeps the cached view valid.
    @Test
    public void testDuplicateInsertKeepsCachedView() throws IOException {
        TestManager manager = new TestManager();
        TestAddonFile file = new TestAddonFile("mod");
        manager.insert(file);

        List<TestAddonFile> first = manager.getLocalFiles();
        manager.insert(file);

        assertSame(first, manager.getLocalFiles());
    }

    /// Moving a file to and from the old state invalidates the cached view both ways.
    @Test
    public void testSetOldInvalidatesSortedView(@TempDir Path tempDirectory) throws IOException {
        TestManager manager = new TestManager();
        TestAddonFile file = new TestAddonFile(tempDirectory, "mod");
        manager.insert(file);
        assertEquals(List.of("mod"), names(manager.getLocalFiles()));

        manager.setOld(file, true);
        assertTrue(manager.getLocalFiles().isEmpty(), "a backed up file must leave the sorted view");

        manager.setOld(file, false);
        assertEquals(List.of("mod"), names(manager.getLocalFiles()));
    }

    /// Returns the display names of a local addon list, in list order.
    ///
    /// @param files the files to map
    /// @return the file names in list order
    private static List<String> names(List<TestAddonFile> files) {
        return files.stream().map(TestAddonFile::getFileName).toList();
    }

    /// Minimal [LocalAddonFile] used to exercise the manager without a game instance.
    @NotNullByDefault
    private static final class TestAddonFile extends LocalAddonFile implements Comparable<TestAddonFile> {
        private final Path file;

        /// Creates a file with the given name inside the given directory.
        ///
        /// @param directory the directory the file lives in
        /// @param name      the file name without extension
        private TestAddonFile(Path directory, String name) {
            this.file = directory.resolve(name + ".jar");
        }

        /// Creates a file with the given name in the current working directory.
        ///
        /// @param name the file name without extension
        private TestAddonFile(String name) {
            this(Path.of("."), name);
        }

        @Override
        public Path getFile() {
            return file;
        }

        @Override
        public String getFileName() {
            return FileUtils.getNameWithoutExtension(file);
        }

        @Override
        public void markDisabled() {
        }

        @Override
        public void setOld(boolean old) {
        }

        @Override
        public boolean keepOldFiles() {
            return false;
        }

        @Override
        public void delete() {
        }

        @Override
        public int compareTo(TestAddonFile other) {
            return getFileName().compareTo(other.getFileName());
        }
    }

    /// Minimal [LocalAddonManager] that exposes the protected mutation helpers to the tests.
    @NotNullByDefault
    private static final class TestManager extends LocalAddonManager<TestAddonFile> {

        /// Creates a manager that never consults the game instance.
        private TestManager() {
            // The base class only stores the instance; this manager never reads it.
            super((DefaultGameInstance) null);
        }

        @Override
        public Path getDirectory() {
            return Path.of(".");
        }

        @Override
        public void refresh() {
        }

        @Override
        public Comparator<TestAddonFile> getComparator() {
            return TestAddonFile::compareTo;
        }

        /// Adds a file through the guarded mutation helper.
        ///
        /// @param file the file to add
        private void insert(TestAddonFile file) {
            addLocalFile(file);
        }

        /// Removes a file through the guarded mutation helper.
        ///
        /// @param file the file to remove
        private void remove(TestAddonFile file) {
            removeLocalFile(file);
        }

        /// Clears all files through the guarded mutation helper.
        private void clear() {
            clearLocalFiles();
        }
    }
}
