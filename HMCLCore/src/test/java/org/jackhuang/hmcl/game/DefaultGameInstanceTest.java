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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Tests snapshot-bound behavior of [DefaultGameInstance].
@NotNullByDefault
public final class DefaultGameInstanceTest {

    /// The selected primary jar follows the resolved manifest's `jar` field.
    @Test
    public void testPrimaryJarUsesResolvedJarField(@TempDir Path tempDirectory) {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID instanceId = new GameInstanceID("instance");
        GameInstanceID jarId = new GameInstanceID("shared-jar");
        TestGameInstance instance = repository.publish(instanceId,
                new GameInstanceManifest(instanceId).withJar(jarId));

        assertEquals(repository.getLayout().getInstanceJarFile(jarId), instance.getInstanceJarFile());
    }

    /// Manifest changes do not reuse version or manager caches from the previous snapshot member.
    @Test
    public void testManifestChangeInvalidatesDerivedState(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID instanceId = new GameInstanceID("instance");
        GameInstanceID oldJarId = new GameInstanceID("old-jar");
        GameInstanceID newJarId = new GameInstanceID("new-jar");
        writeVersionJar(repository.getLayout().getInstanceJarFile(oldJarId), "1.20.1");
        writeVersionJar(repository.getLayout().getInstanceJarFile(newJarId), "1.21.1");

        GameInstanceManifest oldManifest = new GameInstanceManifest(instanceId).withJar(oldJarId);
        TestGameInstance original = repository.publish(instanceId, oldManifest);
        assertEquals(GameVersionNumber.asGameVersion("1.20.1"), original.getVersion());
        var originalModManager = original.getModManager();
        var originalResourcePackManager = original.getResourcePackManager();

        TestGameInstance unchanged = original.withNewSnapshot(repository.newSnapshot());
        assertSame(original.cachedVersion(), unchanged.cachedVersion());
        assertSame(originalModManager, unchanged.getModManager());
        assertSame(originalResourcePackManager, unchanged.getResourcePackManager());

        GameInstanceManifest newManifest = oldManifest.withJar(newJarId);
        TestGameInstance updated = original.withManifest(repository.newSnapshot(), newManifest);
        assertNull(updated.cachedVersion());
        assertNotSame(originalModManager, updated.getModManager());
        assertNotSame(originalResourcePackManager, updated.getResourcePackManager());
        assertEquals(GameVersionNumber.asGameVersion("1.21.1"), updated.getVersion());
    }

    /// Version lookup for an explicit manifest does not reuse a same-id instance with different content.
    @Test
    public void testExplicitManifestDoesNotReuseDifferentCachedManifest(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID instanceId = new GameInstanceID("instance");
        GameInstanceID cachedJarId = new GameInstanceID("cached-jar");
        GameInstanceID requestedJarId = new GameInstanceID("requested-jar");
        writeVersionJar(repository.getLayout().getInstanceJarFile(cachedJarId), "1.20.1");
        writeVersionJar(repository.getLayout().getInstanceJarFile(requestedJarId), "1.21.1");

        GameInstanceManifest cachedManifest = new GameInstanceManifest(instanceId).withJar(cachedJarId);
        TestGameInstance cachedInstance = repository.publish(instanceId, cachedManifest);
        assertEquals(GameVersionNumber.asGameVersion("1.20.1"), cachedInstance.getVersion());

        GameInstanceManifest requestedManifest = cachedManifest.withJar(requestedJarId);
        assertEquals(Optional.of("1.21.1"), repository.getGameVersion(requestedManifest));
    }

    /// Writes a minimal jar containing the version metadata consumed by [GameVersion].
    ///
    /// @param jar     the jar path
    /// @param version the Minecraft version stored in `version.json`
    private static void writeVersionJar(Path jar, String version) throws IOException {
        Files.createDirectories(jar.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry("version.json"));
            output.write(("{\"id\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    /// Minimal repository implementation for snapshot-bound instance tests.
    @NotNullByDefault
    private static final class TestRepository extends DefaultGameRepository {

        /// Creates a test repository rooted at the given directory.
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
        protected TestGameInstance createInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest) {
            return new TestGameInstance(snapshot, id, manifest);
        }

        /// Publishes a snapshot containing one test instance.
        ///
        /// @param id       the instance id
        /// @param manifest the stored manifest
        /// @return the published instance
        private TestGameInstance publish(GameInstanceID id, GameInstanceManifest manifest) {
            DefaultGameRepositorySnapshot snapshot = newSnapshot();
            TestGameInstance instance = createInstance(snapshot, id, manifest);
            snapshot.put(instance);
            publishSnapshot(snapshot);
            return instance;
        }

        /// Creates an empty mutable snapshot using the current layout.
        ///
        /// @return the new snapshot
        private DefaultGameRepositorySnapshot newSnapshot() {
            return createSnapshot(getLayout());
        }
    }

    /// Minimal concrete game instance that exposes cache state to tests.
    @NotNullByDefault
    private static final class TestGameInstance extends DefaultGameInstance {

        /// Creates a test instance without shared session state.
        ///
        /// @param snapshot the owning snapshot
        /// @param id       the instance id
        /// @param manifest the stored manifest
        private TestGameInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest) {
            super(snapshot, id, manifest);
        }

        /// Creates a test instance that may reuse compatible session state.
        ///
        /// @param snapshot     the owning snapshot
        /// @param id           the instance id
        /// @param manifest     the stored manifest
        /// @param shareSession the prior snapshot member
        private TestGameInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                TestGameInstance shareSession) {
            super(snapshot, id, manifest, shareSession);
        }

        /// {@inheritDoc}
        @Override
        protected TestGameInstance withNewSnapshot(DefaultGameRepositorySnapshot newSnapshot) {
            return new TestGameInstance(newSnapshot, id, manifest, this);
        }

        /// {@inheritDoc}
        @Override
        protected TestGameInstance withManifest(
                DefaultGameRepositorySnapshot newSnapshot,
                GameInstanceManifest manifest) {
            return new TestGameInstance(newSnapshot, id, manifest, this);
        }

        /// Returns the cache without triggering version detection.
        ///
        /// @return the cached version, or `null` when detection has not run
        private @Nullable GameVersionNumber cachedVersion() {
            return version;
        }
    }
}
