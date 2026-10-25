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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.DefaultGameRepositoryLayout;
import org.jackhuang.hmcl.game.DefaultGameRepositorySnapshot;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// Fixtures shared by the processor-chain install task tests.
@NotNullByDefault
public final class ProcessorTestSupport {

    /// Utility class: not instantiable.
    private ProcessorTestSupport() {
    }

    /// Writes the file, creating its parent directories.
    ///
    /// @param file    the destination file
    /// @param content the file content
    /// @return the destination file
    public static Path writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    /// Writes a ZIP file with the given entries.
    ///
    /// Entry timestamps are fixed so that two calls with the same entries produce identical bytes.
    ///
    /// @param file    the destination file
    /// @param entries entry name to UTF-8 content
    /// @return the destination file
    public static Path writeZip(Path file, Map<String, String> entries) throws IOException {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0);
                output.putNextEntry(zipEntry);
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return file;
    }

    /// Repository writing every instance into the given game directory.
    public static final class Repository extends DefaultGameRepository {
        /// Creates a repository rooted at the given directory.
        ///
        /// @param baseDirectory the repository base directory
        public Repository(Path baseDirectory) {
            super(baseDirectory);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameRepositoryLayout createLayout(Path baseDirectory) {
            return new DefaultGameRepositoryLayout(baseDirectory);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameInstance createInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            return new Instance(snapshot, id, manifest, manifestFile);
        }
    }

    /// Instance used by the install task tests.
    private static final class Instance extends DefaultGameInstance {
        /// Creates an instance from repository snapshot data.
        ///
        /// @param snapshot     the repository snapshot
        /// @param id           the instance id
        /// @param manifest     the instance manifest
        /// @param manifestFile the manifest file, or `null` before the instance is stored
        private Instance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            super(snapshot, id, manifest, manifestFile);
        }

        /// Creates an instance that may reuse storage paths from `shareSession`.
        ///
        /// @param snapshot     the repository snapshot
        /// @param id           the instance id
        /// @param manifest     the instance manifest
        /// @param shareSession the instance whose storage paths are reused
        private Instance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                DefaultGameInstance shareSession) {
            super(snapshot, id, manifest, shareSession);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameInstance withNewSnapshot(DefaultGameRepositorySnapshot newSnapshot) {
            return new Instance(newSnapshot, id, manifest, this);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameInstance withManifest(
                DefaultGameRepositorySnapshot newSnapshot,
                GameInstanceManifest manifest) {
            return new Instance(newSnapshot, id, manifest, this);
        }
    }
}
