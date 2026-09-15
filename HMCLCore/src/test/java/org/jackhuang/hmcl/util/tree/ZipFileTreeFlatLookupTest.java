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

import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Tests the flat entry lookup added for callers that only probe for known entry names.
///
/// [ZipFileTree#getEntryFlat(String)] must resolve exactly what [#ZipFileTree#getEntry(String)]
/// resolves for exact entry names, because the mod metadata readers rely on it to avoid
/// materializing the full entry tree of every archive they inspect.
@NotNullByDefault
public final class ZipFileTreeFlatLookupTest {

    /// A flat lookup resolves every name the tree-based lookup resolves, and misses the same names.
    @Test
    public void testFlatLookupAgreesWithTreeLookup(@TempDir Path tempDirectory) throws IOException {
        Path archive = tempDirectory.resolve("archive.jar");
        List<String> names = List.of("META-INF/mods.toml", "fabric.mod.json", "nested/deep/entry.txt");
        writeArchive(archive, names);

        try (ZipFileTree tree = CompressingUtils.openZipTree(archive)) {
            for (String name : names) {
                ZipArchiveEntry flat = tree.getEntryFlat(name);
                assertNotNull(flat, name);
                assertSame(tree.getEntry(name), flat, name);
            }

            for (String missing : List.of("quilt.mod.json", "litemod.json", "META-INF/neoforge.mods.toml")) {
                assertNull(tree.getEntryFlat(missing), missing);
                assertNull(tree.getEntry(missing), missing);
            }
        }
    }

    /// A leading slash is normalized before the flat lookup, matching the tree-based lookup.
    @Test
    public void testFlatLookupNormalizesLeadingSlash(@TempDir Path tempDirectory) throws IOException {
        Path archive = tempDirectory.resolve("archive.zip");
        writeArchive(archive, List.of("pack.mcmeta"));

        try (ZipFileTree tree = CompressingUtils.openZipTree(archive)) {
            ZipArchiveEntry entry = tree.getEntryFlat("/pack.mcmeta");
            assertNotNull(entry);
            assertEquals("pack.mcmeta", entry.getName());
            assertSame(tree.getEntry("/pack.mcmeta"), entry);
        }
    }

    /// A flat lookup stays correct on archives holding many entries.
    @Test
    public void testFlatLookupOnWideArchive(@TempDir Path tempDirectory) throws IOException {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            names.add("package/entry-" + i + ".class");
        }
        names.add("fabric.mod.json");

        Path archive = tempDirectory.resolve("wide.jar");
        writeArchive(archive, names);

        try (ZipFileTree tree = CompressingUtils.openZipTree(archive)) {
            assertNull(tree.getEntryFlat("META-INF/mods.toml"));

            ZipArchiveEntry entry = tree.getEntryFlat("package/entry-511.class");
            assertNotNull(entry);
            assertEquals("package/entry-511.class", entry.getName());

            assertNotNull(tree.getEntryFlat("fabric.mod.json"));
        }
    }

    /// Writes a zip archive containing the given entry names as UTF-8 text entries.
    ///
    /// @param archive the archive path
    /// @param names   the entry names to create
    private static void writeArchive(Path archive, List<String> names) throws IOException {
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : names) {
                output.putNextEntry(new ZipEntry(name));
                output.write(name.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
