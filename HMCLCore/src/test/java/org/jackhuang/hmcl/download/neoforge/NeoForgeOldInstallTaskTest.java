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
package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile.Processor;
import org.jackhuang.hmcl.download.forge.ProcessorReuse;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.DefaultGameRepositoryLayout;
import org.jackhuang.hmcl.game.DefaultGameRepositorySnapshot;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies how the legacy NeoForge install task uses the processor sidecar cache.
@NotNullByDefault
public class NeoForgeOldInstallTaskTest {

    /// Skips the whole processor chain when the sidecar records the current artifacts.
    @Test
    public void testSkipsWhenSidecarMatches(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("libraries/patched.jar"), "patched");
        Path installer = writeInstaller(tempDirectory.resolve("neoforge-installer.jar"), patched);

        // The processor jar is absent from the repository, so only a skipped chain can succeed.
        Path clientJar = writeFile(tempDirectory.resolve("work/client.jar"), "client");
        ProcessorReuse.writeSidecar(processors(), Map.of(
                "PATCHED", patched.toString(),
                "MINECRAFT_JAR", clientJar.toString()), installer);

        var executor = createTask(tempDirectory, installer).executor();
        assertTrue(executor.test(), () -> String.valueOf(executor.getException()));
        assertTrue(Files.isRegularFile(Path.of(patched + ".hmcl-cache")));
    }

    /// Runs the processor chain when no sidecar has been recorded yet.
    @Test
    public void testRunsWithoutSidecar(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("libraries/patched.jar"), "patched");
        Path installer = writeInstaller(tempDirectory.resolve("neoforge-installer.jar"), patched);

        // The chain runs, so the missing processor jar makes the task fail.
        var executor = createTask(tempDirectory, installer).executor();
        assertFalse(executor.test());
    }

    /// Returns the client processor declared by the fixture installer.
    ///
    /// @return the processor list
    private static List<Processor> processors() {
        return List.of(new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.neoforged.installertools:installertools:4.0.12:fatjar"),
                List.of(),
                List.of(
                        "--task", "PROCESS_MINECRAFT_JAR",
                        "--input", "{MINECRAFT_JAR}",
                        "--output", "{PATCHED}",
                        "--extract-libraries-to", "{ROOT}/libraries/"),
                Map.of()));
    }

    /// Writes a NeoForge installer fixture whose only client processor writes the patched jar.
    ///
    /// @param installer the installer JAR path
    /// @param patched   the artifact the `PATCHED` data entry points to
    /// @return the installer JAR path
    private static Path writeInstaller(Path installer, Path patched) throws IOException {
        Map<String, Object> profile = Map.of(
                "spec", 1,
                "profile", "NeoForge",
                "minecraft", "1.21.1",
                "json", "version.json",
                "version", "neoforge-test",
                "libraries", List.of(),
                "data", Map.of(
                        "PATCHED", Map.of("client", "'" + patched.toAbsolutePath().normalize() + "'")),
                "processors", List.of(Map.of(
                        "jar", "net.neoforged.installertools:installertools:4.0.12:fatjar",
                        "args", List.of(
                                "--task", "PROCESS_MINECRAFT_JAR",
                                "--input", "{MINECRAFT_JAR}",
                                "--output", "{PATCHED}",
                                "--extract-libraries-to", "{ROOT}/libraries/"))));

        Files.createDirectories(installer.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(installer))) {
            writeZipEntry(output, "install_profile.json", JsonUtils.GSON.toJson(profile));
            writeZipEntry(output, "version.json", "{\"id\":\"neoforge-test\",\"libraries\":[]}");
        }
        return installer;
    }

    /// Writes one UTF-8 text entry to a ZIP stream.
    ///
    /// @param output  the destination ZIP stream
    /// @param name    the entry name
    /// @param content the entry content
    private static void writeZipEntry(ZipOutputStream output, String name, String content)
            throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    /// Creates a NeoForge install task whose progress updates do not require the JavaFX toolkit.
    ///
    /// @param tempDirectory the test directory holding the repository and download cache
    /// @param installer     the NeoForge installer JAR
    /// @return the install task
    private static NeoForgeOldInstallTask createTask(Path tempDirectory, Path installer)
            throws IOException {
        Path work = tempDirectory.resolve("work");
        Files.createDirectories(work);
        Path minecraftJar = writeFile(work.resolve("client.jar"), "client");

        DefaultCacheRepository cacheRepository = new DefaultCacheRepository(work.resolve("download-cache"));
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                new TestRepository(work.resolve("game")),
                new DownloadProvider() {
                    @Override
                    public DefaultCacheRepository getCacheRepository() {
                        return cacheRepository;
                    }
                },
                cacheRepository);
        return new NeoForgeOldInstallTask(
                dependencyManager,
                new GameInstanceManifest(new GameInstanceID("instance")),
                minecraftJar,
                "neoforge-test",
                installer) {
            @Override
            protected void updateProgressImmediately(double progress) {
                // Avoid JavaFX toolkit initialization in this isolated processor test.
            }
        };
    }

    /// Writes the file, creating its parent directories.
    ///
    /// @param file    the destination file
    /// @param content the file content
    /// @return the destination file
    private static Path writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    /// Minimal repository backed by the default layout.
    private static final class TestRepository extends DefaultGameRepository {
        /// Creates a repository rooted at the given directory.
        ///
        /// @param baseDirectory the repository base directory
        private TestRepository(Path baseDirectory) {
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
            throw new UnsupportedOperationException();
        }
    }
}
