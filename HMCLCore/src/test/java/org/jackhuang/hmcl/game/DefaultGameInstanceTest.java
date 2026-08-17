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

import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.MojangDownloadProvider;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameVerificationFixTask;
import org.jackhuang.hmcl.modpack.curse.CurseCompletionTask;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackCompletionTask;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthCompletionTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackCompletionTask;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
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
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests snapshot-bound behavior of [DefaultGameInstance].
@NotNullByDefault
public final class DefaultGameInstanceTest {

    /// Launch repair for ModLauncher adds support metadata without materializing bundled files.
    @Test
    public void testModLauncherLaunchRepairDoesNotWriteBundledLibraries(@TempDir Path tempDirectory) {
        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        GameInstanceID instanceId = new GameInstanceID("instance");
        GameInstanceManifest manifest = new GameInstanceManifest(instanceId)
                .withMainClass(GameComponentAnalyzer.MOD_LAUNCHER_MAIN)
                .withLibraries(List.of(
                        new Library(new Artifact("net.minecraftforge", "forge", "1.0")),
                        new Library(new Artifact("optifine", "OptiFine", "1.0"))));
        TestGameInstance instance = repository.publish(instanceId, manifest);
        GameInstanceManifest launchManifest = instance.getResolvedManifest().launchManifest();
        assertTrue(launchManifest.getLibraries().stream()
                .noneMatch(library -> library.is(
                        "org.jackhuang.hmcl", "transformer-discovery-service")));

        GameInstanceManifest repaired = LaunchManifestNormalizer.repairForLaunch(launchManifest);
        Library transformerService = repaired.getLibraries().stream()
                .filter(library -> library.is(
                        "org.jackhuang.hmcl", "transformer-discovery-service"))
                .findAny()
                .orElseThrow();
        Path transformerFile = repository.getLayout().getLibraryFile(instanceId, transformerService);

        assertFalse(Files.exists(transformerFile));
        assertEquals(repaired, LaunchManifestNormalizer.repairForLaunch(repaired));
    }

    /// Saving a manifest preserves its root flag and pending patches without baking in normalization.
    @Test
    public void testSavePreservesManifestPatchStructure(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID instanceId = new GameInstanceID("instance");
        List<GameInstancePatch> patches = List.of(new GameInstancePatch(
                "loader",
                null,
                0,
                null,
                null,
                List.of(new Library(new Artifact("example", "library", "1.0")))));
        GameInstanceManifest manifest = new GameInstanceManifest(instanceId)
                .withRoot(true)
                .withPatches(patches);

        repository.saveAsync(manifest).run();

        GameInstanceManifest savedManifest = repository.getInstance(instanceId).getManifest();
        assertTrue(savedManifest.isRoot());
        assertEquals(patches, savedManifest.getPatches());
        assertTrue(savedManifest.getLibraries().isEmpty());
    }

    /// Asset and modpack paths are resolved directly from the owning instance.
    @Test
    public void testInstanceOwnsAssetAndModpackPaths(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID instanceId = new GameInstanceID("instance");
        TestGameInstance instance = repository.publish(instanceId, new GameInstanceManifest(instanceId));
        String assetId = "legacy";
        String assetName = "icons/minecraft.icns";
        String assetHash = "abcdef0123456789";
        Path indexFile = repository.getLayout().getAssetIndexFile(assetId);
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, """
                {
                  "objects": {
                    "%s": {
                      "hash": "%s",
                      "size": 1
                    }
                  }
                }
                """.formatted(assetName, assetHash));

        AssetIndex index = instance.getAssetIndex(assetId);
        assertEquals(assetHash, index.getObjects().get(assetName).hash());
        assertEquals(
                Optional.of(repository.getLayout().getAssetObject(index.getObjects().get(assetName))),
                instance.getAssetObject(assetId, assetName));
        assertEquals(Optional.empty(), instance.getAssetObject(assetId, "missing"));
        assertEquals(repository.getLayout().getAssetDirectory(), instance.getActualAssetDirectory(assetId));
        assertEquals(instance.getInstanceRoot().resolve("modpack.json"), instance.getModpackConfigurationFile());
    }

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

    /// Snapshot copies never reuse addon managers; only the version cache is shared for the same manifest.
    @Test
    public void testSnapshotCopyDoesNotShareAddonManagers(@TempDir Path tempDirectory) throws IOException {
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

        TestGameInstance sameManifestCopy = original.withNewSnapshot(repository.newSnapshot());
        assertSame(original.cachedVersion(), sameManifestCopy.cachedVersion());
        assertNotSame(originalModManager, sameManifestCopy.getModManager());
        assertNotSame(originalResourcePackManager, sameManifestCopy.getResourcePackManager());

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

    /// A cached game download can be materialized at an explicit destination.
    @Test
    public void testGameDownloadMaterializesExplicitDestination(@TempDir Path tempDirectory)
            throws Exception {
        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        DefaultCacheRepository cacheRepository =
                new DefaultCacheRepository(tempDirectory.resolve("cache"));
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                repository,
                new MojangDownloadProvider(),
                cacheRepository);
        Path source = tempDirectory.resolve("client.jar");
        Files.writeString(source, "client");
        String sha1 = DigestUtils.digestToString("SHA-1", source);
        Path cached = cacheRepository.cacheFile(source, "SHA-1", sha1);
        GameInstanceManifest manifest = new GameInstanceManifest(new GameInstanceID("instance"))
                .withDownloads(Map.of(
                        DownloadType.CLIENT,
                        new DownloadInfo("https://example.invalid/client.jar", sha1)));
        Path destination = tempDirectory.resolve("fixed.jar");

        var task = new GameDownloadTask(dependencyManager, manifest)
                .thenAcceptAsync(cachedJar -> Files.copy(cachedJar, destination));

        assertTrue(task.executor().test());
        assertEquals("client", Files.readString(destination));
        assertEquals("client", Files.readString(cached));
    }

    /// A game download returns the content-addressed cache file without a version-named copy.
    @Test
    public void testGameDownloadReturnsContentAddressedCacheFile(@TempDir Path tempDirectory)
            throws Exception {
        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        Path cacheDirectory = tempDirectory.resolve("cache");
        DefaultCacheRepository cacheRepository = new DefaultCacheRepository(cacheDirectory);
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                repository,
                new MojangDownloadProvider(),
                cacheRepository);
        Path source = tempDirectory.resolve("client.jar");
        Files.writeString(source, "client");
        String sha1 = DigestUtils.digestToString("SHA-1", source);
        Path cached = cacheRepository.cacheFile(source, "SHA-1", sha1);
        GameInstanceManifest manifest = new GameInstanceManifest(new GameInstanceID("instance"))
                .withDownloads(Map.of(
                        DownloadType.CLIENT,
                        new DownloadInfo("https://example.invalid/client.jar", sha1)));

        GameDownloadTask task = new GameDownloadTask(dependencyManager, manifest);

        assertTrue(task.executor().test());
        assertEquals(cached, task.getResult());
        assertFalse(Files.exists(cacheDirectory.resolve("jars")));
        assertFalse(cached.startsWith(repository.getLayout().getInstanceRoot(manifest.id())));
    }

    /// Keeps the detached client JAR path distinct from the Minecraft version processor variable.
    @Test
    public void testForgeProcessorSeparatesMinecraftJarAndVersion(@TempDir Path tempDirectory)
            throws IOException {
        Path versionMarker = tempDirectory.resolve("minecraft-version");
        Files.writeString(versionMarker, "version");
        String markerSha1 = DigestUtils.digestToString("SHA-1", versionMarker);

        Path minecraftJar = tempDirectory.resolve("cache/client.jar");
        Files.createDirectories(minecraftJar.getParent());
        Files.writeString(minecraftJar, "client");

        Path installer = tempDirectory.resolve("forge-installer.jar");
        writeForgeProcessorFixture(
                installer,
                versionMarker.toAbsolutePath().normalize().toString(),
                markerSha1);

        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                repository,
                new MojangDownloadProvider(),
                new DefaultCacheRepository(tempDirectory.resolve("download-cache")));
        ForgeNewInstallTask task = new ForgeNewInstallTask(
                dependencyManager,
                new GameInstanceManifest(new GameInstanceID("instance")),
                minecraftJar,
                "forge-test",
                installer) {
            @Override
            protected void updateProgressImmediately(double progress) {
                // Avoid JavaFX toolkit initialization in this isolated processor test.
            }
        };

        var executor = task.executor();
        assertTrue(executor.test(), () -> String.valueOf(executor.getException()));
        assertTrue(Files.isRegularFile(minecraftJar));
    }

    /// Legacy verification fixes the captured instance jar rather than a newer same-id snapshot.
    @Test
    public void testVerificationFixKeepsCapturedInstance(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID instanceId = new GameInstanceID("instance");
        GameInstanceManifest manifest = new GameInstanceManifest(instanceId).withLibraries(List.of(
                new Library(new Artifact("net.minecraftforge", "forge", "1.5.2-7.8.1.738"))));

        TestGameInstance captured = repository.publish(
                instanceId,
                manifest,
                tempDirectory.resolve("versions/instance/captured.json"));
        writeSignedJar(captured.getInstanceJarFile());

        TestGameInstance current = repository.publish(
                instanceId,
                manifest,
                tempDirectory.resolve("versions/instance/current.json"));
        writeSignedJar(current.getInstanceJarFile());

        new GameVerificationFixTask(captured, GameVersionNumber.asGameVersion("1.5.2"), manifest).execute();

        assertFalse(hasZipEntry(captured.getInstanceJarFile(), "META-INF/MOJANG_C.DSA"));
        assertFalse(hasZipEntry(captured.getInstanceJarFile(), "META-INF/MOJANG_C.SF"));
        assertTrue(hasZipEntry(current.getInstanceJarFile(), "META-INF/MOJANG_C.DSA"));
        assertTrue(hasZipEntry(current.getInstanceJarFile(), "META-INF/MOJANG_C.SF"));
    }

    /// Dependency managers and modpack completion tasks reject cross-repository instances.
    @Test
    public void testDependencyManagerValidatesInstanceRepository(@TempDir Path tempDirectory) {
        TestRepository instanceRepository = new TestRepository(tempDirectory.resolve("instance"));
        TestRepository managerRepository = new TestRepository(tempDirectory.resolve("manager"));
        TestGameInstance instance = instanceRepository.publish(
                new GameInstanceID("instance"),
                new GameInstanceManifest(new GameInstanceID("instance")));
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                managerRepository,
                new MojangDownloadProvider(),
                new DefaultCacheRepository(tempDirectory.resolve("cache")));

        assertThrows(IllegalArgumentException.class, () -> dependencyManager.validateGameInstance(instance));
        assertThrows(IllegalArgumentException.class, () -> new CurseCompletionTask(dependencyManager, instance));
        assertThrows(IllegalArgumentException.class, () -> new McbbsModpackCompletionTask(dependencyManager, instance));
        assertThrows(IllegalArgumentException.class, () -> new ModrinthCompletionTask(dependencyManager, instance));
        assertThrows(IllegalArgumentException.class, () -> new ServerModpackCompletionTask(dependencyManager, instance));
    }

    /// Non-conventional JSON/jar basenames are kept on disk and recorded on the instance.
    @Test
    public void testRefreshRecordsNonConventionalStoragePaths(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID folderId = new GameInstanceID("MyInstance");
        Path instanceDir = repository.getLayout().getInstanceRoot(folderId);
        Files.createDirectories(instanceDir);

        Path json = instanceDir.resolve("1.20.1.json");
        Path jar = instanceDir.resolve("1.20.1.jar");
        Files.writeString(json, "{\"id\":\"1.20.1\",\"mainClass\":\"net.minecraft.client.main.Main\",\"libraries\":[]}");
        writeVersionJar(jar, "1.20.1");

        repository.refresh();

        DefaultGameInstance instance = repository.getInstance(folderId);
        assertEquals(json, instance.getManifestFile());
        assertEquals(jar, instance.getInstanceJarFile());
        assertEquals(GameVersionNumber.asGameVersion("1.20.1"), instance.getVersion());
        assertEquals(folderId, instance.getId());
        assertEquals(folderId, instance.getManifest().id());
        assertEquals(json, repository.getInstanceJson(folderId));
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

    /// Writes a jar containing the legacy signature entries removed before launching Forge.
    ///
    /// @param jar the jar path
    private static void writeSignedJar(Path jar) throws IOException {
        Files.createDirectories(jar.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry("META-INF/MOJANG_C.DSA"));
            output.write(1);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("META-INF/MOJANG_C.SF"));
            output.write(1);
            output.closeEntry();
        }
    }

    /// Writes a Forge installer fixture whose processor output is keyed by
    /// `{MINECRAFT_VERSION}`.
    ///
    /// @param installer       the installer JAR path
    /// @param minecraftVersion the value stored in the install profile's `minecraft` field
    /// @param outputSha1      expected checksum for the processor output
    private static void writeForgeProcessorFixture(
            Path installer,
            String minecraftVersion,
            String outputSha1) throws IOException {
        Map<String, Object> profile = Map.of(
                "spec", 1,
                "minecraft", minecraftVersion,
                "json", "version.json",
                "version", "forge-test",
                "libraries", List.of(),
                "processors", List.of(Map.of(
                        "jar", "example:processor:1.0",
                        "outputs", Map.of("{MINECRAFT_VERSION}", outputSha1))));

        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(installer))) {
            writeZipEntry(output, "install_profile.json", JsonUtils.GSON.toJson(profile));
            writeZipEntry(output, "version.json", "{\"id\":\"forge-test\",\"libraries\":[]}");
        }
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

    /// Returns whether a zip contains an entry with the given name.
    ///
    /// @param zipFile   the zip path
    /// @param entryName the entry name
    /// @return `true` when the entry exists
    private static boolean hasZipEntry(Path zipFile, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            return zip.getEntry(entryName) != null;
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
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            return new TestGameInstance(snapshot, id, manifest, manifestFile);
        }

        /// Publishes a snapshot containing one test instance.
        ///
        /// @param id       the instance id
        /// @param manifest the stored manifest
        /// @return the published instance
        private TestGameInstance publish(GameInstanceID id, GameInstanceManifest manifest) {
            return publish(id, manifest, null);
        }

        /// Publishes a snapshot containing one test instance with an optional manifest path.
        ///
        /// @param id           the instance id
        /// @param manifest     the stored manifest
        /// @param manifestFile the non-conventional manifest path, or `null`
        /// @return the published instance
        private TestGameInstance publish(
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            DefaultGameRepositorySnapshot snapshot = newSnapshot();
            TestGameInstance instance = createInstance(snapshot, id, manifest, manifestFile);
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
        /// @param snapshot     the owning snapshot
        /// @param id           the instance id
        /// @param manifest     the stored manifest
        /// @param manifestFile non-conventional manifest path, or `null`
        private TestGameInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            super(snapshot, id, manifest, manifestFile);
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
