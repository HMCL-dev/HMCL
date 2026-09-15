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

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.TestGameInstanceFixture;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests the mod scan pipeline: directory collection, metadata parsing, caching, and merging.
///
/// The parse cache is the reason refreshing an unchanged mods directory no longer re-opens every
/// archive, so the tests pin both halves of its contract: an unchanged file state replays the
/// cached metadata, and a changed state re-parses.
@NotNullByDefault
public final class ModManagerTest {

    /// Recognized mod files are parsed, and unrelated files are ignored.
    @Test
    public void testRefreshParsesRecognizedMods(@TempDir Path tempDirectory) throws IOException {
        ModManager manager = newManager(tempDirectory);
        Path modsDirectory = manager.getDirectory();
        Files.createDirectories(modsDirectory);

        writeFabricJar(modsDirectory.resolve("fabric-mod.jar"), "Fabric Mod", "fabric");
        writeForgeJar(modsDirectory.resolve("forge-mod.jar"), "Forge Mod", "forgemod");
        Files.writeString(modsDirectory.resolve("readme.txt"), "not a mod");
        Files.writeString(modsDirectory.resolve("broken.jar"), "not a zip archive");

        manager.refresh();

        List<LocalModFile> mods = manager.getLocalFiles();
        assertEquals(3, mods.size(), () -> "unexpected mods: " + names(mods));
        assertEquals(
                List.of("broken", "fabric-mod", "forge-mod"),
                names(mods),
                "the list is sorted by file name");

        LocalModFile fabric = mods.get(1);
        assertEquals("Fabric Mod", fabric.getName());
        assertEquals("fabric", fabric.getId());
        assertEquals(ModLoaderType.FABRIC, fabric.getModLoaderType());
        assertTrue(fabric.isActive());

        // A file that cannot be parsed still shows up, under its file name and without a loader.
        LocalModFile broken = mods.get(0);
        assertEquals("broken", broken.getName());
        assertEquals(ModLoaderType.UNKNOWN, broken.getModLoaderType());
    }

    /// A disabled mod file is listed under its display name and reported as inactive.
    @Test
    public void testRefreshHandlesDisabledMods(@TempDir Path tempDirectory) throws IOException {
        ModManager manager = newManager(tempDirectory);
        Path modsDirectory = manager.getDirectory();
        Files.createDirectories(modsDirectory);

        writeFabricJar(modsDirectory.resolve("disabled-mod.jar.disabled"), "Disabled Mod", "disabled");

        manager.refresh();

        List<LocalModFile> mods = manager.getLocalFiles();
        assertEquals(1, mods.size());
        assertEquals("disabled-mod", mods.get(0).getFileName());
        assertEquals("Disabled Mod", mods.get(0).getName());
        assertFalse(mods.get(0).isActive());
    }

    /// An unchanged file state replays the cached metadata instead of re-opening the archive.
    ///
    /// The cache is keyed on size and last-modified time, so rewriting the archive with content of
    /// the same length and restoring its timestamp must yield the previously parsed metadata.
    @Test
    public void testRefreshReusesCachedMetadataWhileFileStateIsUnchanged(@TempDir Path tempDirectory)
            throws IOException {
        ModManager manager = newManager(tempDirectory);
        Path modsDirectory = manager.getDirectory();
        Files.createDirectories(modsDirectory);

        Path modFile = modsDirectory.resolve("example.jar");
        writeFabricJar(modFile, "Alpha", "example");
        long originalSize = Files.size(modFile);
        FileTime originalTimestamp = Files.getLastModifiedTime(modFile);

        manager.refresh();
        assertEquals("Alpha", single(manager).getName());

        // Same entry names and same content length, so the archive size stays identical.
        writeFabricJar(modFile, "Bravo", "example");
        assertEquals(originalSize, Files.size(modFile), "the fixture must keep the archive size stable");
        Files.setLastModifiedTime(modFile, originalTimestamp);

        manager.refresh();
        assertEquals(
                "Alpha",
                single(manager).getName(),
                "an unchanged file state must replay the cached metadata");

        // A changed timestamp invalidates the cached entry.
        Files.setLastModifiedTime(modFile, FileTime.fromMillis(originalTimestamp.toMillis() + 2000L));

        manager.refresh();
        assertEquals("Bravo", single(manager).getName(), "a changed timestamp must force a re-parse");
    }

    /// Added files appear and removed files disappear after a refresh.
    @Test
    public void testRefreshPicksUpAddedAndRemovedFiles(@TempDir Path tempDirectory) throws IOException {
        ModManager manager = newManager(tempDirectory);
        Path modsDirectory = manager.getDirectory();
        Files.createDirectories(modsDirectory);

        Path first = modsDirectory.resolve("first.jar");
        writeFabricJar(first, "First", "first");
        manager.refresh();
        assertEquals(List.of("first"), names(manager.getLocalFiles()));

        writeFabricJar(modsDirectory.resolve("second.jar"), "Second", "second");
        manager.refresh();
        assertEquals(List.of("first", "second"), names(manager.getLocalFiles()));

        Files.delete(first);
        manager.refresh();
        assertEquals(List.of("second"), names(manager.getLocalFiles()));
    }

    /// Parsing a mods directory larger than the parallel threshold yields every mod exactly once.
    @Test
    public void testRefreshParsesLargeDirectoryInParallel(@TempDir Path tempDirectory) throws IOException {
        ModManager manager = newManager(tempDirectory);
        Path modsDirectory = manager.getDirectory();
        Files.createDirectories(modsDirectory);

        int modCount = 32;
        List<String> expected = new ArrayList<>(modCount);
        for (int i = 0; i < modCount; i++) {
            String fileName = "mod-%02d".formatted(i);
            writeFabricJar(modsDirectory.resolve(fileName + ".jar"), "Mod " + i, fileName);
            expected.add(fileName);
        }

        manager.refresh();

        List<LocalModFile> mods = manager.getLocalFiles();
        assertEquals(modCount, mods.size());
        assertEquals(expected, names(mods));
        for (int i = 0; i < modCount; i++) {
            assertEquals("mod-%02d".formatted(i), mods.get(i).getId());
            assertEquals("Mod " + i, mods.get(i).getName());
        }
    }

    /// Repeated reads reuse the sorted view until the mod set changes.
    @Test
    public void testGetLocalFilesReusesSortedView(@TempDir Path tempDirectory) throws IOException {
        ModManager manager = newManager(tempDirectory);
        Path modsDirectory = manager.getDirectory();
        Files.createDirectories(modsDirectory);

        writeFabricJar(modsDirectory.resolve("first.jar"), "First", "first");
        writeFabricJar(modsDirectory.resolve("second.jar"), "Second", "second");

        List<LocalModFile> first = manager.getLocalFiles();
        assertSame(first, manager.getLocalFiles());

        writeFabricJar(modsDirectory.resolve("third.jar"), "Third", "third");
        manager.refresh();

        List<LocalModFile> second = manager.getLocalFiles();
        assertEquals(List.of("first", "second", "third"), names(second));
    }

    /// Creates a mod manager bound to a fresh instance rooted at the given directory.
    ///
    /// @param tempDirectory the repository base directory, which is also the instance run directory
    /// @return the mod manager under test
    private static ModManager newManager(Path tempDirectory) {
        TestGameInstanceFixture repository = new TestGameInstanceFixture(tempDirectory);
        DefaultGameInstance instance = repository.publishInstance("instance");
        return instance.getModManager();
    }

    /// Returns the only mod in the manager, failing when the count is not one.
    ///
    /// @param manager the manager to read
    /// @return the single mod
    private static LocalModFile single(ModManager manager) throws IOException {
        List<LocalModFile> mods = manager.getLocalFiles();
        assertEquals(1, mods.size(), () -> "unexpected mods: " + names(mods));
        return mods.get(0);
    }

    /// Returns the display file names of a mod list, in list order.
    ///
    /// @param mods the mods to map
    /// @return the file names in list order
    private static List<String> names(List<LocalModFile> mods) {
        return mods.stream().map(LocalModFile::getFileName).toList();
    }

    /// Writes a Fabric mod archive with a stable size for a given name length.
    ///
    /// Entries are stored without compression so that the archive size depends only on the entry
    /// name and the content length, which lets the cache tests swap the display name while keeping
    /// the file size identical.
    ///
    /// @param modFile the archive path
    /// @param name    the mod display name
    /// @param modId   the mod id
    private static void writeFabricJar(Path modFile, String name, String modId) throws IOException {
        String json = "{\"id\":\"%s\",\"name\":\"%s\",\"version\":\"1.0\",\"description\":\"\"}"
                .formatted(modId, name);
        writeArchive(modFile, "fabric.mod.json", json);
    }

    /// Writes a Forge 1.13+ mod archive.
    ///
    /// @param modFile the archive path
    /// @param name    the mod display name
    /// @param modId   the mod id
    private static void writeForgeJar(Path modFile, String name, String modId) throws IOException {
        String toml = """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"

                [[mods]]
                modId="%s"
                version="1.0"
                displayName="%s"
                description=""
                """.formatted(modId, name);
        writeArchive(modFile, "META-INF/mods.toml", toml);
    }

    /// Writes a zip archive holding one UTF-8 text entry, stored without compression.
    ///
    /// @param archive the archive path
    /// @param name    the entry name
    /// @param content the entry content
    private static void writeArchive(Path archive, String name, String content) throws IOException {
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.setLevel(Deflater.NO_COMPRESSION);
            output.putNextEntry(new ZipEntry(name));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
