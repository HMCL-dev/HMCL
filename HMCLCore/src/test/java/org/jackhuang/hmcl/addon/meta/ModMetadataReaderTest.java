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
package org.jackhuang.hmcl.addon.meta;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModMetadata;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies that every mod metadata reader produces a detached [ModMetadata] value.
///
/// The readers no longer construct `LocalModFile` instances or touch a `ModManager`, so they can be
/// exercised with nothing but a file and an open archive tree. These tests pin the field mapping of
/// each supported mod format, which is what the parse cache stores and replays.
@NotNullByDefault
public final class ModMetadataReaderTest {

    /// Fabric metadata is read from `fabric.mod.json`, including joined authors and the homepage.
    @Test
    public void testFabricModMetadata(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("fabric-mod.jar");
        writeArchive(modFile, Map.of("fabric.mod.json", """
                {
                  "id": "example",
                  "name": "Example",
                  "version": "1.2.3",
                  "description": "Fabric description",
                  "authors": ["Alice", "Bob"],
                  "contact": {"homepage": "https://example.invalid/fabric"}
                }
                """));

        ModMetadata metadata = read(modFile, FabricModMetadata::fromFile);

        assertEquals("example", metadata.modId());
        assertEquals(ModLoaderType.FABRIC, metadata.loaderType());
        assertEquals("Example", metadata.name());
        assertEquals("Alice, Bob", metadata.authors());
        assertEquals("1.2.3", metadata.version());
        assertEquals("https://example.invalid/fabric", metadata.url());
        assertEquals("Fabric description", metadata.description().toString());
    }

    /// Forge 1.13+ metadata is read from `META-INF/mods.toml`.
    @Test
    public void testForgeNewModMetadata(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("forge-mod.jar");
        writeArchive(modFile, Map.of("META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"

                [[mods]]
                modId="forgemod"
                version="4.0"
                displayName="Forge Mod"
                description="Forge description"
                authors="Frank"
                displayURL="https://example.invalid/forge"
                """));

        ModMetadata metadata = read(modFile, ForgeNewModMetadata::fromForgeFile);

        assertEquals("forgemod", metadata.modId());
        assertEquals(ModLoaderType.FORGE, metadata.loaderType());
        assertEquals("Forge Mod", metadata.name());
        assertEquals("Frank", metadata.authors());
        assertEquals("4.0", metadata.version());
        assertEquals("https://example.invalid/forge", metadata.url());
        assertEquals("Forge description", metadata.description().toString());
    }

    /// Legacy Forge metadata is read from `mcmod.info`.
    @Test
    public void testForgeOldModMetadata(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("legacy-forge-mod.jar");
        writeArchive(modFile, Map.of("mcmod.info", """
                [
                  {
                    "modid": "oldmod",
                    "name": "Old Mod",
                    "description": "Legacy description",
                    "author": "Carol",
                    "version": "2.0",
                    "mcversion": "1.7.10",
                    "url": "https://example.invalid/old"
                  }
                ]
                """));

        ModMetadata metadata = read(modFile, ForgeOldModMetadata::fromFile);

        assertEquals("oldmod", metadata.modId());
        assertEquals(ModLoaderType.FORGE, metadata.loaderType());
        assertEquals("Old Mod", metadata.name());
        assertEquals("Carol", metadata.authors());
        assertEquals("2.0", metadata.version());
        assertEquals("1.7.10", metadata.gameVersion());
        assertEquals("https://example.invalid/old", metadata.url());
    }

    /// Quilt metadata is read from `quilt.mod.json` and flattens its contributors.
    @Test
    public void testQuiltModMetadata(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("quilt-mod.jar");
        writeArchive(modFile, Map.of("quilt.mod.json", """
                {
                  "schema_version": 1,
                  "quilt_loader": {
                    "id": "qmod",
                    "version": "3.0",
                    "metadata": {
                      "name": "Quilt Mod",
                      "description": "Quilt description",
                      "contributors": {"Dave": "author"},
                      "contact": {"homepage": "https://example.invalid/quilt"}
                    }
                  }
                }
                """));

        ModMetadata metadata = read(modFile, QuiltModMetadata::fromFile);

        assertEquals("qmod", metadata.modId());
        assertEquals(ModLoaderType.QUILT, metadata.loaderType());
        assertEquals("Quilt Mod", metadata.name());
        assertEquals("Dave (author)", metadata.authors());
        assertEquals("3.0", metadata.version());
        assertEquals("https://example.invalid/quilt", metadata.url());
    }

    /// LiteLoader metadata is read from `litemod.json`.
    @Test
    public void testLiteModMetadata(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("lite-mod.litemod");
        writeArchive(modFile, Map.of("litemod.json", """
                {
                  "name": "Lite Mod",
                  "version": "1.0",
                  "mcversion": "1.12.2",
                  "author": "Eve",
                  "description": "LiteLoader description"
                }
                """));

        ModMetadata metadata = read(modFile, LiteModMetadata::fromFile);

        assertEquals("Lite Mod", metadata.modId());
        assertEquals(ModLoaderType.LITE_LOADER, metadata.loaderType());
        assertEquals("Lite Mod", metadata.name());
        assertEquals("Eve", metadata.authors());
        assertEquals("1.0", metadata.version());
        assertEquals("1.12.2", metadata.gameVersion());
    }

    /// A reader rejects an archive that does not carry its metadata file.
    @Test
    public void testUnrelatedArchiveIsRejected(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("unrelated.jar");
        writeArchive(modFile, Map.of("README.txt", "not a mod"));

        try (ZipFileTree tree = CompressingUtils.openZipTree(modFile)) {
            assertThrows(IOException.class, () -> FabricModMetadata.fromFile(modFile, tree));
            assertThrows(IOException.class, () -> ForgeOldModMetadata.fromFile(modFile, tree));
            assertThrows(IOException.class, () -> QuiltModMetadata.fromFile(modFile, tree));
            assertThrows(IOException.class, () -> LiteModMetadata.fromFile(modFile, tree));
        }
    }

    /// Optional fields absent from the metadata do not fail the parse.
    @Test
    public void testMissingOptionalFieldsAreTolerated(@TempDir Path tempDirectory) throws IOException {
        Path modFile = tempDirectory.resolve("minimal-fabric-mod.jar");
        writeArchive(modFile, Map.of("fabric.mod.json", """
                {"id": "minimal", "name": "Minimal", "version": "1.0", "description": ""}
                """));

        ModMetadata metadata = read(modFile, FabricModMetadata::fromFile);

        assertEquals("minimal", metadata.modId());
        assertEquals("Minimal", metadata.name());
        assertEquals("", metadata.authors());
        assertEquals("", metadata.url());
        assertEquals("", metadata.logoPath());
    }

    /// Parses one archive with the given reader.
    ///
    /// @param modFile the archive to read
    /// @param reader  the reader under test
    /// @return the parsed metadata
    private static ModMetadata read(Path modFile, Reader reader) throws IOException {
        try (ZipFileTree tree = CompressingUtils.openZipTree(modFile)) {
            ModMetadata metadata = reader.read(modFile, tree);
            assertNotNull(metadata);
            return metadata;
        }
    }

    /// Adapter that lets the tests pass a reader method reference.
    @FunctionalInterface
    private interface Reader {
        /// Reads metadata from the given archive.
        ///
        /// @param modFile the archive path, used in error messages
        /// @param tree    the open archive tree
        /// @return the parsed metadata
        ModMetadata read(Path modFile, ZipFileTree tree) throws IOException;
    }

    /// Writes a zip archive holding the given UTF-8 text entries.
    ///
    /// @param archive the archive path
    /// @param entries the entry name to content mapping
    private static void writeArchive(Path archive, Map<String, String> entries) throws IOException {
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(entries).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
