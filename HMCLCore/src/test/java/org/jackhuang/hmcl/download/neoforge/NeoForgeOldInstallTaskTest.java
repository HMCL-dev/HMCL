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
import org.jackhuang.hmcl.download.forge.ProcessorTestSupport;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies how the legacy NeoForge install task uses the processor sidecar cache.
@NotNullByDefault
public class NeoForgeOldInstallTaskTest {

    /// Skips the whole processor chain when the sidecar records the current artifacts.
    @Test
    public void testSkipsWhenSidecarMatches(@TempDir Path tempDirectory) throws IOException {
        Path patched = ProcessorTestSupport.writeFile(tempDirectory.resolve("libraries/patched.jar"), "patched");
        Path installer = writeInstaller(tempDirectory.resolve("neoforge-installer.jar"), patched);
        Path clientJar = writeClientJar(tempDirectory);

        // The processor jar is absent from the repository, so only a skipped chain can succeed.
        ProcessorReuse.writeSidecar(processors(), Map.of(
                "PATCHED", patched.toString(),
                "MINECRAFT_JAR", clientJar.toString()), installer);

        var executor = createTask(tempDirectory, clientJar, installer).executor();
        assertTrue(executor.test(), () -> String.valueOf(executor.getException()));
        assertTrue(Files.isRegularFile(Path.of(patched + ".hmcl-cache")));
    }

    /// Runs the processor chain when no sidecar has been recorded yet.
    @Test
    public void testRunsWithoutSidecar(@TempDir Path tempDirectory) throws IOException {
        Path patched = ProcessorTestSupport.writeFile(tempDirectory.resolve("libraries/patched.jar"), "patched");
        Path installer = writeInstaller(tempDirectory.resolve("neoforge-installer.jar"), patched);
        Path clientJar = writeClientJar(tempDirectory);

        // The chain runs, so the missing processor jar makes the task fail.
        var executor = createTask(tempDirectory, clientJar, installer).executor();
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

    /// Writes the client jar the processor chain reads.
    ///
    /// @param tempDirectory the test directory
    /// @return the client jar path
    private static Path writeClientJar(Path tempDirectory) throws IOException {
        return ProcessorTestSupport.writeZip(
                tempDirectory.resolve("work/client.jar"), Map.of("version.json", "{}"));
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

        return ProcessorTestSupport.writeZip(installer, Map.of(
                "install_profile.json", JsonUtils.GSON.toJson(profile),
                "version.json", "{\"id\":\"neoforge-test\",\"libraries\":[]}"));
    }

    /// Creates a NeoForge install task whose progress updates do not require the JavaFX toolkit.
    ///
    /// @param tempDirectory the test directory holding the repository and download cache
    /// @param clientJar     the client JAR to install from
    /// @param installer     the NeoForge installer JAR
    /// @return the install task
    private static NeoForgeOldInstallTask createTask(Path tempDirectory, Path clientJar, Path installer)
            throws IOException {
        Path work = tempDirectory.resolve("work");
        Files.createDirectories(work);

        DefaultCacheRepository cacheRepository = new DefaultCacheRepository(work.resolve("download-cache"));
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                new ProcessorTestSupport.Repository(work.resolve("game")),
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
                clientJar,
                "neoforge-test",
                installer) {
            @Override
            protected void updateProgressImmediately(double progress) {
                // Avoid JavaFX toolkit initialization in this isolated processor test.
            }
        };
    }
}
