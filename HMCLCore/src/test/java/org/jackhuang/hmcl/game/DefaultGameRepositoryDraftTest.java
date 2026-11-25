// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.ComponentRemoteVersionList;
import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.download.fabric.FabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltAPIRemoteVersion;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackCompletionException;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackCompletionTask;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthManifest;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackProvider;
import org.jackhuang.hmcl.modpack.server.ServerModpackCompletionTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackManifest;
import org.jackhuang.hmcl.modpack.server.ServerModpackRemoteInstallTask;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.function.ExceptionalBiConsumer;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests exclusive repository drafts and their filesystem visibility boundary.
@NotNullByDefault
public final class DefaultGameRepositoryDraftTest {

    /// A failed local server pack extraction keeps the previous override hashes for a successful retry.
    @Test
    void localServerUpdateRetriesPartiallyExtractedOverrides(@TempDir Path directory) throws Exception {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("server");
        repository.save(new GameInstanceManifest(id));
        DefaultGameInstance instance = repository.getInstance(id);
        Path root = instance.getInstanceRoot();
        Path existing = Files.writeString(root.resolve("settings.txt"), "original");
        Path blocker = Files.createFile(root.resolve("config"));
        Path configuration = repository.getLayout().getModpackConfigurationFile(id);
        var oldFile = new ModpackConfiguration.FileInformation("settings.txt", DigestUtils.digestToString("SHA-1", existing));
        JsonUtils.writeToJsonFile(configuration, new ModpackConfiguration<>(new ServerModpackManifest(), "Server", "pack", "1", List.of(oldFile)));
        String oldConfiguration = Files.readString(configuration);

        Path archive = directory.resolve("pack.zip");
        try (Zipper zip = new Zipper(archive)) {
            zip.putTextFile("first", "overrides/first.txt");
            zip.putTextFile("added", "overrides/config/new.txt");
            zip.putTextFile("updated", "overrides/settings.txt");
        }
        String gameVersion = "1.21";
        var manifest = new ServerModpackManifest("pack", "", "2", "", "", List.of(),
                List.of(new ServerModpackManifest.Addon("game", gameVersion)));
        Modpack pack = manifest.toModpack();
        DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, gameVersion);

        Task<?> update = manifest.getProvider().createUpdateTask(manager, instance, archive, pack, null);
        assertThrows(IOException.class, update::run);
        assertEquals("first", Files.readString(root.resolve("first.txt")));
        assertEquals(oldConfiguration, Files.readString(configuration));

        Files.delete(blocker);
        manifest.getProvider().createUpdateTask(manager, instance, archive, pack, null).run();
        assertEquals("added", Files.readString(root.resolve("config/new.txt")));
        assertEquals("updated", Files.readString(existing));
        assertEquals("2", Objects.requireNonNull(JsonUtils.fromJsonFile(configuration, ModpackConfiguration.class)).getVersion());
    }

    /// Failed server file downloads leave component updates available for a later attempt.
    @Test
    void serverModpackFileFailurePrecedesComponentUpdate(@TempDir Path directory) throws Exception {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("server");
        repository.save(new GameInstanceManifest(id));
        DefaultGameInstance instance = repository.getInstance(id);
        Path existing = Files.writeString(instance.getInstanceRoot().resolve("settings.txt"), "old");
        String oldHash = DigestUtils.digestToString("SHA-1", existing);
        String newHash = DigestUtils.digestToString("SHA-1", "updated".getBytes(StandardCharsets.UTF_8));
        var installed = publishServerUpdate(directory, List.of(new ModpackConfiguration.FileInformation("settings.txt", oldHash)),
                List.of(new ModpackConfiguration.FileInformation("added.txt", newHash),
                        new ModpackConfiguration.FileInformation("settings.txt", newHash)));
        Path overrides = Files.createDirectories(directory.resolve("published/overrides"));
        Files.writeString(overrides.resolve("added.txt"), "updated");
        var manager = createOfflineGameInstaller(repository, id, "1.21");
        var task = new ServerModpackCompletionTask(manager, instance);
        JsonUtils.writeToJsonFile(instance.getModpackConfigurationFile(), installed);
        assertThrows(DownloadException.class, task::run);
        var saved = readServerManifest(instance);
        assertEquals("1.20", saved.getAddons().get(0).getVersion());
        assertEquals(newHash, saved.getFiles().get(0).getHash());
        assertEquals(oldHash, saved.getFiles().get(1).getHash());
        assertEquals("old", Files.readString(existing));

        Files.writeString(overrides.resolve("settings.txt"), "updated");
        new ServerModpackCompletionTask(manager, instance).run();
        assertEquals("updated", Files.readString(existing));
        assertEquals(newHash, readServerManifest(repository.getInstance(id)).getFiles().get(1).getHash());
    }

    /// A queued update cannot delete files or rewrite the configuration of a replacement instance.
    @Test
    void staleServerUpdateLeavesReplacementUntouched(@TempDir Path directory) throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("server");
        repository.save(new GameInstanceManifest(id));
        var installed = publishServerUpdate(directory,
                List.of(new ModpackConfiguration.FileInformation("settings.txt", "0".repeat(40))), List.of());
        var update = new ServerModpackCompletionTask(createDependencyManager(repository), repository.getInstance(id), installed);

        assertTrue(repository.removeInstanceFromDisk(id));
        repository.save(new GameInstanceManifest(id).withMainClass("replacement.Main"));
        DefaultGameInstance replacement = repository.getInstance(id);
        Path settings = Files.writeString(replacement.getInstanceRoot().resolve("settings.txt"), "replacement settings");
        Path config = Files.writeString(replacement.getModpackConfigurationFile(), "replacement config");
        assertThrows(IllegalStateException.class, update::run);
        assertEquals("replacement settings", Files.readString(settings));
        assertEquals("replacement config", Files.readString(config));
    }

    /// A restart during component installation must still see the previously installed version.
    @Test
    void serverUpdatePublishesComponentsAfterBuild(@TempDir Path directory) throws Exception {
        GameInstanceID id = new GameInstanceID("server");
        var repository = new TestRepository(directory) {
            /// Component version visible on disk just before the updated instance is published.
            private @Nullable String versionBeforeCommit;

            /// Reads the installed configuration inside the build, before publishing its result.
            @Override
            void publishDraftSnapshot(DefaultGameRepositoryDraft draft, DefaultGameRepositorySnapshot newSnapshot) {
                @Nullable DefaultGameInstance previous = draft.getBaseSnapshot().findInstance(id);
                if (previous != null)
                    versionBeforeCommit = assertDoesNotThrow(() -> readServerManifest(previous)).getAddons().get(0).getVersion();
                super.publishDraftSnapshot(draft, newSnapshot);
            }
        };
        repository.save(new GameInstanceManifest(id));
        DefaultGameInstance instance = repository.getInstance(id);
        var installed = publishServerUpdate(directory, List.of(), List.of());
        JsonUtils.writeToJsonFile(instance.getModpackConfigurationFile(), installed);

        var update = new ServerModpackCompletionTask(createOfflineGameInstaller(repository, id, "1.21"), instance);
        update.run();
        assertEquals("1.20", repository.versionBeforeCommit);
        assertEquals("1.21", readServerManifest(repository.getInstance(id)).getAddons().get(0).getVersion());
    }

    /// A replacement made as the build releases its draft keeps its own configuration.
    @Test
    void serverUpdateCannotPublishToReplacementAfterBuild(@TempDir Path directory) throws IOException {
        GameInstanceID id = new GameInstanceID("server");
        var repository = new TestRepository(directory) {
            /// Enables replacement for the build's commit after the initial installation is ready.
            private boolean replaceOnCommit;

            /// Installs a replacement as soon as the writer can acquire a new draft.
            @Override
            void releaseDraft(DefaultGameRepositoryDraft draft) {
                super.releaseDraft(draft);
                if (replaceOnCommit && draft.getState() == GameRepositoryDraft.State.COMMITTED) {
                    replaceOnCommit = false;
                    assertTrue(removeInstanceFromDisk(id));
                    assertDoesNotThrow(() -> {
                        save(new GameInstanceManifest(id).withMainClass("replacement.Main"));
                        Files.writeString(getInstance(id).getModpackConfigurationFile(), "replacement config");
                    });
                }
            }
        };
        repository.save(new GameInstanceManifest(id));
        DefaultGameInstance instance = repository.getInstance(id);
        JsonUtils.writeToJsonFile(instance.getModpackConfigurationFile(), publishServerUpdate(directory, List.of(), List.of()));

        var update = new ServerModpackCompletionTask(createOfflineGameInstaller(repository, id, "1.21"), instance);
        repository.replaceOnCommit = true;
        assertThrows(IllegalStateException.class, update::run);
        assertEquals("replacement.Main", repository.getInstanceManifest(id).mainClass());
        assertEquals("replacement config", Files.readString(repository.getInstance(id).getModpackConfigurationFile()));
    }

    /// Publishes a local 1.20 to 1.21 server update and returns its previously installed configuration.
    private static ModpackConfiguration<ServerModpackManifest> publishServerUpdate(
            Path directory, List<ModpackConfiguration.FileInformation> oldFiles,
            List<ModpackConfiguration.FileInformation> newFiles) throws IOException {
        Path published = directory.resolve("published");
        String fileApi = published.toUri().toASCIIString();
        ServerModpackManifest old = new ServerModpackManifest("pack", "", "1", "", fileApi, oldFiles,
                List.of(new ServerModpackManifest.Addon("game", "1.20")));
        ServerModpackManifest remote = new ServerModpackManifest("pack", "", "2", "", fileApi, newFiles,
                List.of(new ServerModpackManifest.Addon("game", "1.21")));
        Files.createDirectory(published);
        JsonUtils.writeToJsonFile(published.resolve("server-manifest.json"), remote);
        return new ModpackConfiguration<>(old, "Server", "pack", "1", oldFiles);
    }

    /// Reads the manifest a later launcher process would use for synchronization.
    private static ServerModpackManifest readServerManifest(DefaultGameInstance instance) throws IOException {
        return Objects.requireNonNull(JsonUtils.fromJsonFile(instance.getModpackConfigurationFile(),
                ModpackConfiguration.typeOf(ServerModpackManifest.class))).getManifest();
    }

    /// The displayed game stage carries the selected version for a build or a component install.
    @Test
    void componentVersionStageMatchesTheBuildPlan(@TempDir Path directory) throws Exception {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        String version = "1.21.2:local";
        DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, version);
        TaskStage expected = new TaskStage("hmcl.install.game", version);

        Set<TaskStage.Hint> declared = ConcurrentHashMap.newKeySet();
        Set<TaskStage> gameStages = ConcurrentHashMap.newKeySet();
        TaskListener listener = new TaskListener() {
            /// Records the plan's stage hints as the build enters the task tree.
            @Override
            public void onStagesDeclared(@Unmodifiable List<TaskStage.Hint> hints) {
                declared.addAll(hints);
            }

            /// Collects the version labels shown for game installation.
            @Override
            public void onStageStarted(TaskStage.Scope scope) {
                if (scope.stage().key().equals(expected.key()))
                    gameStages.add(scope.stage());
            }
        };
        try (var builder = manager.newGameBuilder(id)) {
            Task<GameInstance> build = buildTask(builder.component(GameComponentType.GAME, version));
            build.addTaskListener(listener);
            GameInstance installed = build.run();
            assertSame(repository.getInstance(id), installed);
        }
        assertTrue(declared.containsAll(List.of(new TaskStage.Hint(expected), new TaskStage.Hint(InstallStages.LIBRARIES),
                new TaskStage.Hint(InstallStages.ASSETS))), declared::toString);
        assertEquals(Set.of(expected), gameStages);

        DefaultGameInstance instance = repository.getInstance(id);
        Task<?> install = manager.installComponentRemoteAsync(instance, instance.getManifest(),
                version, GameComponentType.GAME, version);
        gameStages.clear();
        install.addTaskListener(listener).run();
        assertEquals(Set.of(expected), gameStages);
    }

    /// A missing game version releases the target for a subsequent installation.
    @Test
    void invalidBuildReleasesTarget(@TempDir Path directory) {
        DefaultDependencyManager manager = createDependencyManager(new TestRepository(directory));
        GameInstanceID id = new GameInstanceID("instance");
        Task<GameInstance> build = buildTask(manager.newGameBuilder(id));
        assertThrows(IllegalStateException.class, build::run);
        manager.newGameBuilder(id).close();
    }

    /// Cancellation before installation removes prepared files and releases the target.
    @Test
    void cancelledBuildReleasesPreparedInstance(@TempDir Path directory) throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        DefaultDependencyManager manager = createDependencyManager(repository);
        Path instanceRoot = repository.getLayout().getInstanceRoot(id);
        Task<GameInstance> build = new Task<>() {
            /// Cancels after reserving and preparing the target, just before the build starts.
            @Override
            public GameInstance execute(TaskExecutionContext context) throws Exception {
                GameBuilder builder = manager.newGameBuilder(id).component(GameComponentType.GAME, "1.21.2");
                Files.createDirectories(instanceRoot);
                Files.writeString(instanceRoot.resolve("override.txt"), "prepared");
                cancel();
                return builder.build(context);
            }
        };
        assertThrows(CancellationException.class, build::run);
        assertFalse(repository.hasInstance(id));
        assertFalse(Files.exists(instanceRoot));
        manager.newGameBuilder(id).close();
    }

    /// API mods cannot complete an instance update when their sole JAR is unavailable.
    @Test
    void apiModUpdateFailsWhenJarIsMissing(@TempDir Path directory) throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id).withMainClass(GameComponentAnalyzer.VANILLA_MAIN));
        DefaultDependencyManager manager = createDependencyManager(repository);
        GameVersionNumber game = GameVersionNumber.asGameVersion("1.20.1");
        String url = directory.resolve("missing.jar").toUri().toString();
        RemoteAddon.File file = new RemoteAddon.File(Map.of(), url, "mod.jar");
        RemoteAddon.Version version = new RemoteAddon.Version(RemoteAddon.Source.MODRINTH, "v1", "mod", "Mod", "1",
                Instant.EPOCH, RemoteAddon.VersionType.Release, file, List.of(), List.of("1.20.1"), List.of());

        for (ComponentRemoteVersion remote : List.of(
                new FabricAPIRemoteVersion(game, "1", "1", Instant.EPOCH, version, List.of(url)),
                new LegacyFabricAPIRemoteVersion(game, "1", "1", Instant.EPOCH, version, List.of(url)),
                new QuiltAPIRemoteVersion(game, "1", "1", Instant.EPOCH, version, List.of(url)))) {
            Task<?> update = manager.updateInstanceAsync(id, instance ->
                    manager.installComponentRemoteAsync(instance, instance.getManifest(), remote));
            assertThrows(DownloadException.class, update::run, remote.getComponentType().getPatchId());
        }
    }

    /// Only formats allowing manual completion retain a committed instance after a completion failure.
    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "true,true"})
    void modpackFailureAfterGameCommit(boolean completionFailure, boolean keepIncomplete, @TempDir Path directory)
            throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, "1.21.2");
        Exception failure = completionFailure ? new ModpackCompletionException() : new IOException();
        Task<?> install = installTask(manager, id, null, keepIncomplete, (builder, context) -> {
            builder.component(GameComponentType.GAME, "1.21.2").build(context);
            assertTrue(repository.hasInstance(id));
            throw failure;
        });
        assertSame(failure, assertThrows(Exception.class, install::run));
        assertEquals(completionFailure && keepIncomplete, repository.hasInstance(id));
        assertEquals(completionFailure && keepIncomplete, Files.exists(repository.getLayout().getInstanceRoot(id)));
    }

    /// Failed updates release the instance for retry and retain all files already written.
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    void failedModpackUpdateKeepsProgressForRetry(boolean writeOverrides, boolean publishBuild,
                                                  @TempDir Path directory) throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        String version = "1.21.2";
        repository.save(new GameInstanceManifest(id).withMainClass("original.Main"));
        DefaultGameInstance original = repository.getInstance(id);
        Path marker = Files.writeString(original.getInstanceRoot().resolve("settings.txt"), "original");
        Path added = original.getInstanceRoot().resolve("added.txt");
        DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, version);
        IOException failure = new IOException("Update failed");
        Task<?> update = installTask(manager, id, original, false, (builder, context) -> {
            assertTrue(Files.notExists(directory.resolve("backup")));
            if (writeOverrides) {
                Files.writeString(marker, "updated");
                Files.writeString(added, "new");
            }
            if (publishBuild)
                builder.component(GameComponentType.GAME, version).build(context);
            throw failure;
        });
        assertSame(failure, assertThrows(IOException.class, update::run));
        assertEquals(writeOverrides ? "updated" : "original", Files.readString(marker));
        if (writeOverrides)
            assertEquals("new", Files.readString(added));
        else
            assertTrue(Files.notExists(added));
        assertTrue(Files.notExists(directory.resolve("backup")));
        DefaultGameInstance current = repository.getInstance(id);
        if (publishBuild)
            assertEquals(version, current.getComponentVersion(GameComponentType.GAME));
        else
            assertSame(original, current);
        manager.newGameBuilder(current).close();
    }

    /// Failure after another installation reuses the same id preserves the replacement and its files.
    @Test
    void modpackRollbackPreservesReplacement(@TempDir Path directory) throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, "1.21.2");
        IOException failure = new IOException();
        Path marker = repository.getLayout().getInstanceRoot(id).resolve("replacement.txt");
        Task<?> install = installTask(manager, id, null, false, (builder, context) -> {
            builder.component(GameComponentType.GAME, "1.21.2").build(context);
            assertTrue(repository.removeInstanceFromDisk(id));
            repository.save(new GameInstanceManifest(id).withMainClass("replacement.Main"));
            Files.writeString(marker, "replacement");
            throw failure;
        });
        assertSame(failure, assertThrows(IOException.class, install::run));
        assertEquals("replacement.Main", repository.getInstanceManifest(id).mainClass());
        assertEquals("replacement", Files.readString(marker));
    }

    /// A Modrinth retry replaces changed files and drops obsolete ones after build or download failures.
    @Test
    void modrinthUpdateRetainsBaselineUntilFilesAreInstalled(@TempDir Path directory) throws Exception {
        FailingRepository repository = new FailingRepository(directory);
        GameInstanceID id = new GameInstanceID("modrinth");
        repository.save(new GameInstanceManifest(id));
        DefaultGameInstance original = repository.getInstance(id);
        Path mods = Files.createDirectories(original.getRunDirectory().resolve("mods"));
        Path retained = Files.writeString(mods.resolve("changed.jar"), "old");
        Path removed = Files.writeString(mods.resolve("removed.jar"), "old");
        Path serverOnly = Files.writeString(mods.resolve("server.jar"), "old");
        Path payload = Files.writeString(directory.resolve("published.jar"), "new");
        Path addedSource = Files.writeString(directory.resolve("added.jar"), "wrong");
        List<String> urls = List.of(payload.toUri().toString());
        Map<String, String> oldHashes = Map.of("sha512", DigestUtils.digestToString("SHA-512", retained));
        Map<String, String> newHashes = Map.of("sha512", DigestUtils.digestToString("SHA-512", "new".getBytes(StandardCharsets.UTF_8)));
        Map<String, String> addedHashes = Map.of("sha512", DigestUtils.digestToString("SHA-512", "added".getBytes(StandardCharsets.UTF_8)));
        Map<String, String> dependencies = Map.of("minecraft", "1.21");
        var previous = new ModrinthManifest("minecraft", 1, "1", "pack", null, List.of(
                new ModrinthManifest.File("mods/changed.jar", oldHashes, null, urls, 3),
                new ModrinthManifest.File("mods/removed.jar", oldHashes, null, urls, 3),
                new ModrinthManifest.File("mods/server.jar", oldHashes, null, urls, 3)), dependencies);
        var next = new ModrinthManifest("minecraft", 1, "2", "pack", null, List.of(
                new ModrinthManifest.File("mods/changed.jar", newHashes, null, urls, 3),
                new ModrinthManifest.File("mods/added.jar", addedHashes, null, List.of(addedSource.toUri().toString()), 5),
                new ModrinthManifest.File("mods/server.jar", oldHashes, Map.of("client", "unsupported"), urls, 3)), dependencies);
        Path configuration = repository.getLayout().getModpackConfigurationFile(id);
        Path index = original.getInstanceRoot().resolve("modrinth.index.json");
        JsonUtils.writeToJsonFile(configuration, new ModpackConfiguration<>(previous, "Modrinth", "pack", "1", List.of()));
        JsonUtils.writeToJsonFile(index, previous);
        String oldConfig = Files.readString(configuration);
        String oldIndex = Files.readString(index);
        Path archive = directory.resolve("update.mrpack");
        try (Zipper zip = new Zipper(archive)) {
            zip.putTextFile(JsonUtils.GSON.toJson(next), "modrinth.index.json");
        }
        Modpack pack;
        try (var zip = CompressingUtils.openZipFile(archive)) {
            pack = ModrinthModpackProvider.INSTANCE.readManifest(zip, archive);
        }
        DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, "1.21");

        repository.failDraftPublish = true;
        assertThrows(IllegalStateException.class, () -> ModrinthModpackProvider.INSTANCE
                .createUpdateTask(manager, original, archive, pack, null).run());
        assertEquals(oldConfig, Files.readString(configuration));
        assertEquals("old", Files.readString(retained));
        assertTrue(Files.exists(removed));

        assertThrows(ModpackCompletionException.class, () -> ModrinthModpackProvider.INSTANCE
                .createUpdateTask(manager, repository.getInstance(id), archive, pack, null).run());
        assertEquals(oldConfig, Files.readString(configuration));
        assertEquals(oldIndex, Files.readString(index));
        assertEquals("new", Files.readString(retained));
        Files.delete(payload);
        Files.writeString(addedSource, "added");
        ModrinthModpackProvider.INSTANCE.createUpdateTask(manager, repository.getInstance(id), archive, pack, null).run();
        assertEquals("new", Files.readString(retained));
        assertEquals("added", Files.readString(mods.resolve("added.jar")));
        assertTrue(Files.notExists(removed));
        assertTrue(Files.notExists(serverOnly));
        assertEquals("2", Objects.requireNonNull(JsonUtils.fromJsonFile(index, ModrinthManifest.class)).getVersionId());
        assertEquals("2", Objects.requireNonNull(JsonUtils.fromJsonFile(configuration, ModpackConfiguration.class)).getVersion());
    }

    /// An incomplete MCBBS install keeps its patch and remote metadata unless cancellation wins.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incompleteMcbbsInstallKeepsPublishedPatch(boolean cancel, @TempDir Path directory) throws Exception {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("mcbbs");
        String fileApi = directory.toUri().toASCIIString();
        String metadata = """
                {"name":"local","version":"1","addons":[{"id":"game","version":"1.21"}],"fileApi":"%s"}
                """.formatted(fileApi);
        var manifest = JsonUtils.fromNonNullJson(metadata, McbbsModpackManifest.class);
        Files.writeString(directory.resolve("manifest.json"), """
                {"name":"remote","version":"2","files":[
                  {"type":"curse","projectID":1,"fileID":2,"fileName":"missing.jar","url":"%s"}
                ]}
                """.formatted(directory.resolve("missing.jar").toUri().toASCIIString()));
        Path archive = directory.resolve("pack.zip");
        try (Zipper zip = new Zipper(archive)) {
            zip.putTextFile("local", "overrides/local.txt");
        }
        Task<?> install = manifest.toModpack().getInstallTask(createOfflineGameInstaller(repository, id, "1.21"),
                archive, id, "", null);
        if (cancel) {
            install.addTaskListener(new TaskListener() {
                /// Cancels after the download failure has been published to the installer.
                @Override
                public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                    if (task instanceof McbbsModpackCompletionTask && failure instanceof ModpackCompletionException)
                        install.cancel();
                }
            });
            assertThrows(CancellationException.class, install::run);
            assertFalse(repository.hasInstance(id));
        } else {
            assertThrows(ModpackCompletionException.class, install::run);
            DefaultGameInstance instance = repository.getInstance(id);
            assertTrue(instance.getManifest().getPatches().stream().anyMatch(patch -> "mcbbs".equals(patch.id())));
            var config = Objects.requireNonNull(JsonUtils.fromJsonFile(instance.getModpackConfigurationFile(),
                    ModpackConfiguration.typeOf(McbbsModpackManifest.class)));
            assertEquals("remote", config.getName());
            assertEquals("2", config.getVersion());
            assertTrue(config.getOverrides().isEmpty());
        }
    }

    /// Cancellation from the final child's completion rolls back the newly built instance.
    @Test
    void cancellationDuringFinalInstallNotificationRollsBack(@TempDir Path directory) throws IOException {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        String version = "1.21.2";
        ServerModpackManifest manifest = new ServerModpackManifest("pack", "", "1", "", "", List.of(),
                List.of(new ServerModpackManifest.Addon("game", version)));
        Task<?> install = new ServerModpackRemoteInstallTask(createOfflineGameInstaller(repository, id, version), manifest, id);
        install.addTaskListener(new TaskListener() {
            /// Requests cancellation after the final child has published success.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                if (task instanceof ServerModpackCompletionTask && failure == null)
                    install.cancel();
            }
        });
        assertThrows(CancellationException.class, install::run);
        assertFalse(repository.hasInstance(id));
        assertFalse(Files.exists(repository.getLayout().getInstanceRoot(id)));
    }

    /// Runs an installation or update with its task's cancellation boundary.
    private static Task<@Nullable Void> installTask(
            DefaultDependencyManager manager, GameInstanceID id, @Nullable DefaultGameInstance updateTarget,
            boolean keepIncomplete, ExceptionalBiConsumer<DefaultGameBuilder, TaskExecutionContext, Exception> install) {
        return new Task<>() {
            /// Runs the installation in its owning task tree.
            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                Modpack.installWithGameBuilder(context, manager, id, updateTarget, keepIncomplete, builder -> install.accept(builder, context));
                return null;
            }
        };
    }

    /// Creating and cancelling a queued installation leaves the repository available to other writers.
    @Test
    void modpackCancelledBeforeExecutionDoesNotReserveInstance(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("cancelled");
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            Task<?> task = new ServerModpackRemoteInstallTask(createDependencyManager(repository), new ServerModpackManifest(), id);
            task.cancel();
            assertFalse(task.test());
            assertInstanceOf(CancellationException.class, task.exceptionNow());
            assertTrue(draft.isOpen());
        }
        assertFalse(Files.exists(repository.getLayout().getInstanceRoot(id)));
    }

    /// An invalid pack releases its reservation, and a later install cannot remove an existing instance.
    @Test
    void modpackFailureRollsBackOnlyItsOwnReservation(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        DefaultDependencyManager manager = createDependencyManager(repository);
        GameInstanceID id = new GameInstanceID("instance");
        ServerModpackManifest missingGame = new ServerModpackManifest();
        assertThrows(IllegalStateException.class, new ServerModpackRemoteInstallTask(manager, missingGame, id)::run);
        assertFalse(Files.exists(repository.getLayout().getInstanceRoot(id)));

        Task<?> competingInstall = new ServerModpackRemoteInstallTask(manager, missingGame, id);
        GameInstanceManifest existing = new GameInstanceManifest(id).withMainClass("existing.Main");
        repository.save(existing);
        assertThrows(IllegalStateException.class, competingInstall::run);
        assertEquals(existing, repository.getInstance(id).getManifest());
        assertTrue(Files.isRegularFile(repository.getLayout().getInstanceJson(id)));
    }

    /// Finds a manifest among unrelated and malformed JSON files and preserves its storage path.
    @Test
    public void testRefreshSelectsManifestAmongMultipleJsonFiles(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        Path root = Files.createDirectories(repository.getLayout().getInstanceRoot(id));
        Files.writeString(root.resolve("a-broken.json"), "{");
        Files.writeString(root.resolve("b-settings.json"), "{\"id\":\"settings\",\"mainClass\":42}");
        Path selected = root.resolve("c-game.json");
        Files.writeString(selected, "{\"id\":\"original\",\"mainClass\":\"example.Main\"}");
        Files.writeString(root.resolve("d-game.json"), "{\"id\":\"other\",\"mainClass\":\"other.Main\"}");

        repository.refresh();

        assertEquals(selected, repository.getInstance(id).getManifestFile());
        assertEquals(id, repository.getInstance(id).getManifest().id());
        assertEquals("example.Main", repository.getInstance(id).getManifest().mainClass());
        assertFalse(Files.exists(repository.getLayout().getInstanceJson(id)));
    }

    /// Supplies missing IDs for conventional, single-candidate, and multiple-candidate manifests without rewriting files.
    @ParameterizedTest
    @ValueSource(strings = {"conventional", "single", "multiple"})
    public void testRefreshSuppliesMissingManifestId(String layout, @TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        Path root = Files.createDirectories(repository.getLayout().getInstanceRoot(id));
        Path manifestFile = root.resolve(layout.equals("conventional") ? "instance.json" : "game.json");
        String originalJson = "{\"mainClass\":\"example.Main\"}";
        Files.writeString(manifestFile, originalJson);
        if (layout.equals("multiple")) {
            Files.writeString(root.resolve("a-settings.json"), "{}");
            Files.writeString(root.resolve("b-broken.json"), "{");
        }

        repository.refresh();

        DefaultGameInstance instance = repository.getInstance(id);
        assertEquals(id, instance.getManifest().id());
        assertEquals("example.Main", instance.getManifest().mainClass());
        assertEquals(manifestFile, instance.getManifestFile());
        assertEquals(originalJson, Files.readString(manifestFile));
    }

    /// Uses the directory identity even when the JSON ID is null, invalid, or different.
    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "{}", "\"..\"", "\"original\""})
    public void testRefreshOverridesManifestId(String jsonId, @TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        Files.createDirectories(repository.getLayout().getInstanceRoot(id));
        Path manifestFile = repository.getLayout().getInstanceJson(id);
        String originalJson = "{\"id\":" + jsonId + ",\"mainClass\":\"example.Main\"}";
        Files.writeString(manifestFile, originalJson);

        repository.refresh();

        assertEquals(id, repository.getInstance(id).getManifest().id());
        assertEquals(originalJson, Files.readString(manifestFile));
    }

    /// Ignores directories whose multiple JSON files do not contain an instance manifest.
    @Test
    public void testRefreshIgnoresUnrelatedJsonFiles(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        Path root = Files.createDirectories(repository.getLayout().getInstanceRoot(id));
        Files.writeString(root.resolve("settings.json"), "{\"id\":\"settings\"}");
        Files.writeString(root.resolve("empty.json"), "null");

        repository.refresh();

        assertFalse(repository.hasInstance(id));
    }

    /// Rejects path-segment instance ids before they can reach repository deletion code.
    @Test
    public void testRejectsSpecialPathSegmentIds() {
        assertThrows(IllegalArgumentException.class, () -> new GameInstanceID("."));
        assertThrows(IllegalArgumentException.class, () -> new GameInstanceID(".."));
    }

    /// Keeps a new manifest in memory until commit and publishes the resulting instance once committed.
    @Test
    public void testCommitPublishesModifiedManifest(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest manifest = new GameInstanceManifest(id).withMainClass("example.Main");
        Path manifestFile = repository.getLayout().getInstanceJson(id);
        Path instanceRoot = repository.getLayout().getInstanceRoot(id);
        Path draftStorage = tempDirectory.resolve(".hmcl").resolve("repository-drafts");

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            draft.put(manifest);

            assertFalse(repository.hasInstance(id));
            assertFalse(Files.exists(instanceRoot));
            assertFalse(Files.exists(manifestFile));
            assertFalse(Files.exists(draftStorage));

            GameRepositorySnapshot committed = draft.commit();
            assertEquals(GameRepositoryDraft.State.COMMITTED, draft.getState());
            assertEquals(manifest, committed.getInstance(id).getManifest());
        }

        assertTrue(repository.hasInstance(id));
        GameInstanceManifest stored = JsonUtils.fromNonNullJson(
                Files.readString(manifestFile),
                GameInstanceManifest.class);
        assertEquals(id, stored.id());
        assertEquals("example.Main", stored.mainClass());
    }

    /// Materializes a completed client JAR only while committing the new instance.
    @Test
    public void testCommitMaterializesPrimaryJar(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest manifest = new GameInstanceManifest(id);
        Path source = tempDirectory.resolve("cache/client.jar");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "client");
        Path target = repository.getLayout().getInstanceJarFile(id);

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            draft.put(manifest);
            draft.putPrimaryJar(id, source);

            assertFalse(Files.exists(repository.getLayout().getInstanceRoot(id)));
            assertFalse(Files.exists(target));

            draft.commit();
        }

        assertEquals("client", Files.readString(target));
        assertEquals("client", Files.readString(source));
    }

    /// Aborting removes files below a root that was first created by the draft.
    @Test
    public void testAbortRemovesDraftCreatedInstanceRoot(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        Path root = repository.getLayout().getInstanceRoot(id);

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            draft.put(new GameInstanceManifest(id));
            Files.createDirectories(root);
            Files.writeString(root.resolve("downloaded.jar"), "content");
        }

        assertFalse(Files.exists(root));
        assertFalse(repository.hasInstance(id));

        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Leaves the published manifest and its JSON unchanged when an update is aborted.
    @Test
    public void testAbortDoesNotOverwriteExistingManifest(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        GameInstanceManifest updated = original.withMainClass("updated.Main");
        repository.save(original);

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            draft.put(updated);
            assertEquals(original, repository.getInstance(id).getManifest());
        }

        Path manifestFile = repository.getLayout().getInstanceJson(id);
        assertEquals(original, repository.getInstance(id).getManifest());
        GameInstanceManifest stored = JsonUtils.fromNonNullJson(
                Files.readString(manifestFile),
                GameInstanceManifest.class);
        assertEquals(id, stored.id());
        assertEquals("original.Main", stored.mainClass());
    }

    /// Rejects overlapping drafts and direct snapshot writes while a draft owns the repository.
    @Test
    public void testDraftExcludesOtherRepositoryWrites(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            assertThrows(IllegalStateException.class, repository::openDraft);
            assertThrows(IllegalStateException.class, repository::refresh);
            assertThrows(
                    IllegalStateException.class,
                    () -> repository.setBaseDirectory(tempDirectory.resolve("other")));
            assertTrue(draft.isOpen());
        }

        repository.refresh();
    }

    /// Refuses to claim and later delete an unregistered directory that predates the draft.
    @Test
    public void testPutRejectsPreexistingUnregisteredDirectory(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        Path root = repository.getLayout().getInstanceRoot(id);
        Files.createDirectories(root);
        Path retained = root.resolve("retained.txt");
        Files.writeString(retained, "content");

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            assertThrows(FileAlreadyExistsException.class, () -> draft.put(new GameInstanceManifest(id)));
        }

        assertTrue(Files.exists(retained));
    }

    /// Keeps a pending removal private and preserves the published files when the draft aborts.
    @Test
    public void testAbortPreservesRemovedInstance(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id));
        Path root = repository.getLayout().getInstanceRoot(id);

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            draft.remove(id);
            assertTrue(repository.hasInstance(id));
            assertTrue(Files.isDirectory(root));
        }

        assertTrue(repository.hasInstance(id));
        assertTrue(Files.isDirectory(root));
    }

    /// Renames an instance and its direct inheritance references in one draft commit.
    @Test
    public void testRenameCommitsFilesAndReferences(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID parentId = new GameInstanceID("parent");
        GameInstanceID renamedId = new GameInstanceID("renamed");
        GameInstanceID childId = new GameInstanceID("child");
        repository.save(new GameInstanceManifest(parentId));
        repository.save(new GameInstanceManifest(childId).withInheritsFrom(parentId));

        assertTrue(repository.renameInstance(parentId, renamedId));

        assertFalse(repository.hasInstance(parentId));
        assertTrue(repository.hasInstance(renamedId));
        assertEquals(renamedId, repository.getInstance(childId).getManifest().inheritsFrom());
        assertFalse(Files.exists(repository.getLayout().getInstanceRoot(parentId)));
        assertTrue(Files.isRegularFile(repository.getLayout().getInstanceJson(renamedId)));
    }

    /// Removes a registered instance through a one-shot draft.
    @Test
    public void testRemoveInstanceUsesDraftCommit(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id));
        Path root = repository.getLayout().getInstanceRoot(id);

        assertTrue(repository.removeInstanceFromDisk(id));

        assertFalse(repository.hasInstance(id));
        assertFalse(Files.exists(root));
    }

    /// Restores modified manifests and releases exclusivity when publication fails after replacement.
    @Test
    public void testCommitFailureRollsBackAndReleasesDraft(@TempDir Path tempDirectory) throws IOException {
        FailingRepository repository = new FailingRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        repository.save(original);
        repository.failDraftPublish = true;

        DefaultGameRepositoryDraft draft = repository.openDraft();
        draft.put(original.withMainClass("updated.Main"));
        assertThrows(IllegalStateException.class, draft::commit);

        assertEquals(GameRepositoryDraft.State.FAILED, draft.getState());
        assertEquals(original, repository.getInstance(id).getManifest());
        GameInstanceManifest stored = JsonUtils.fromNonNullJson(
                Files.readString(repository.getLayout().getInstanceJson(id)),
                GameInstanceManifest.class);
        assertEquals("original.Main", stored.mainClass());
        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Restores an existing primary JAR when snapshot publication fails after replacement.
    @Test
    public void testCommitFailureRollsBackPrimaryJar(@TempDir Path tempDirectory) throws IOException {
        FailingRepository repository = new FailingRepository(tempDirectory.resolve("game"));
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        repository.save(original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "original");
        Path source = tempDirectory.resolve("cache/replacement.jar");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "replacement");
        repository.failDraftPublish = true;

        DefaultGameRepositoryDraft draft = repository.openDraft();
        draft.put(original.withMainClass("updated.Main"));
        draft.putPrimaryJar(id, source);
        assertThrows(IllegalStateException.class, draft::commit);

        assertEquals("original", Files.readString(target));
        assertEquals("replacement", Files.readString(source));
        assertEquals(GameRepositoryDraft.State.FAILED, draft.getState());
    }

    /// A manifest-only update preserves an existing client JAR.
    @Test
    public void testUpdateInstanceAsyncCommitsWorkingManifest(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id).withMainClass("original.Main"));
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "custom client");

        Task<?> update = createDependencyManager(repository).updateInstanceAsync(id, workingInstance -> Task.supplyAsync(() ->
                workingInstance.getManifest().withMainClass("updated.Main")));
        assertTrue(update.test());

        assertEquals("updated.Main", repository.getInstance(id).getManifest().mainClass());
        assertEquals("custom client", Files.readString(target));
    }

    /// A version change replaces an existing client JAR with the file selected by the new manifest.
    @Test
    public void testUpdateInstanceAsyncReplacesClientJar(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        repository.save(original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "old client");
        Path source = tempDirectory.resolve("new-client.jar");
        Files.writeString(source, "new client");
        String sha1 = DigestUtils.digestToString("SHA-1", source);
        DefaultCacheRepository cache = new DefaultCacheRepository(tempDirectory.resolve("cache"));
        Path cached = cache.cacheFile(source, "SHA-1", sha1);
        DefaultDependencyManager manager = new DefaultDependencyManager(
                repository, new TestDownloadProvider(cache), cache);
        GameInstanceManifest replacement = original.withDownloads(Map.of(DownloadType.CLIENT,
                new DownloadInfo("https://example.invalid/client.jar", sha1)));

        Task<?> update = manager.updateInstanceAsync(id, instance ->
                new GameDownloadTask(manager, replacement).thenApplyAsync(jar -> {
                    assertEquals("old client", Files.readString(target));
                    assertEquals(original, repository.getInstance(id).getManifest());
                    return replacement;
                }));
        assertTrue(update.test());

        assertEquals(replacement, repository.getInstance(id).getManifest());
        assertTrue(replacement.getDownloadInfo().validateChecksum(target, false));
        assertEquals("new client", Files.readString(cached));
    }

    /// Game installation takes ownership of the new client; loader installation retains the shared client.
    @ParameterizedTest
    @CsvSource({"GAME,false", "GAME,true", "FABRIC,false", "FABRIC,true"})
    public void testComponentInstallationUpdatesJarOwnership(
            GameComponentType componentType, boolean hasGamePatch, @TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory.resolve("game"));
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceID sharedId = new GameInstanceID("1.21.1");
        repository.save(new GameInstanceManifest(sharedId));
        Path sharedJar = repository.getLayout().getInstanceJarFile(sharedId);
        writeVersionJar(sharedJar, "1.21.1");
        String oldSha1 = DigestUtils.digestToString("SHA-1", sharedJar);
        GameInstanceManifest original = new GameInstanceManifest(id)
                .withRoot(null)
                .withMainClass(GameComponentAnalyzer.VANILLA_MAIN)
                .withJar(sharedId)
                .withDownloads(Map.of(DownloadType.CLIENT,
                        new DownloadInfo("https://example.invalid/old.jar", oldSha1)));
        repository.save(hasGamePatch ? original.withBaseGamePatch("1.21.1") : original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        DefaultDependencyManager manager = createDependencyManager(repository);
        Path source = tempDirectory.resolve("new-client.jar");
        writeVersionJar(source, "1.21.2");
        String newSha1 = DigestUtils.digestToString("SHA-1", source);
        manager.getCacheRepository().cacheFile(source, "SHA-1", newSha1);

        boolean installGame = componentType == GameComponentType.GAME;
        GameInstancePatch patch = installGame
                ? GameInstancePatch.fromManifest(new GameInstanceManifest(id)
                        .withMainClass(GameComponentAnalyzer.VANILLA_MAIN)
                        .withDownloads(Map.of(DownloadType.CLIENT,
                                new DownloadInfo("https://example.invalid/new.jar", newSha1))),
                        componentType.getPatchId(), "1.21.2", GameInstancePatch.PRIORITY_MC)
                : new GameInstancePatch(componentType.getPatchId(), "0.16.0", GameInstancePatch.PRIORITY_LOADER,
                        null, "net.fabricmc.loader.impl.launch.knot.KnotClient", List.of());
        ComponentRemoteVersion remote = fixedComponent(componentType, installGame ? "1.21.2" : "1.21.1", patch);

        assertTrue(manager.updateInstanceAsync(id, instance -> manager
                .installComponentRemoteAsync(instance, instance.getManifest(), remote)
                .thenApplyAsync(manifest -> {
                    assertEquals(sharedJar, repository.getInstance(id).getInstanceJarFile());
                    assertFalse(Files.exists(target));
                    return manifest;
                })).test());

        repository.refresh();
        DefaultGameInstance updated = repository.getInstance(id);
        assertEquals(installGame ? id : sharedId, updated.getManifest().jar());
        assertEquals(installGame ? target : sharedJar, updated.getInstanceJarFile());
        assertEquals(GameVersionNumber.asGameVersion(installGame ? "1.21.2" : "1.21.1"), updated.getVersion());
        assertEquals(installGame ? newSha1 : oldSha1,
                DigestUtils.digestToString("SHA-1", updated.getInstanceJarFile()));
        assertEquals(installGame ? "1.21.2" : "1.21.1", updated.getComponentVersion(GameComponentType.GAME));
        assertEquals(oldSha1, DigestUtils.digestToString("SHA-1", sharedJar));
        assertEquals(installGame, Files.exists(target));
    }

    /// Equivalent checksums preserve a customized client even when the download URL changes.
    @Test
    public void testUpdateInstanceAsyncPreservesClientWithSameChecksum(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withDownloads(Map.of(DownloadType.CLIENT,
                new DownloadInfo("https://example.invalid/old.jar", "a".repeat(40))));
        repository.save(original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "custom client");
        GameInstanceManifest replacement = original.withDownloads(Map.of(DownloadType.CLIENT,
                new DownloadInfo("https://example.invalid/new.jar", "A".repeat(40))));

        assertTrue(createDependencyManager(repository).updateInstanceAsync(id,
                instance -> Task.completed(replacement)).test());

        assertEquals(replacement, repository.getInstance(id).getManifest());
        assertEquals("custom client", Files.readString(target));
    }

    /// A changed URL selects a replacement when neither client download declares a checksum.
    @Test
    public void testUpdateInstanceAsyncReplacesClientWithoutChecksum(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withDownloads(Map.of(DownloadType.CLIENT,
                new DownloadInfo("https://example.invalid/old.jar")));
        repository.save(original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "old client");
        String newUrl = "https://example.invalid/new.jar";
        DefaultDependencyManager manager = createDependencyManager(repository);
        manager.getCacheRepository().cacheText(new UrlResponseInfo(200, WebURL.parse(newUrl),
                HttpHeaders.of(Map.of("etag", List.of("new-client"),
                        "cache-control", List.of("max-age=3600")), (name, value) -> true)), "new client");
        GameInstanceManifest replacement = original.withDownloads(Map.of(DownloadType.CLIENT, new DownloadInfo(newUrl)));

        assertTrue(manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).test());

        assertEquals(replacement, repository.getInstance(id).getManifest());
        assertEquals("new client", Files.readString(target));
    }

    /// Client changes supplied only by patches are resolved before choosing the replacement JAR.
    @Test
    public void testUpdateInstanceAsyncResolvesClientPatch(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id);
        repository.save(original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "old client");
        Path source = tempDirectory.resolve("new-client.jar");
        Files.writeString(source, "new client");
        String sha1 = DigestUtils.digestToString("SHA-1", source);
        DefaultDependencyManager manager = createDependencyManager(repository);
        manager.getCacheRepository().cacheFile(source, "SHA-1", sha1);
        GameInstanceManifest replacement = original.withPatches(List.of(GameInstancePatch.fromManifest(
                original.withDownloads(Map.of(DownloadType.CLIENT,
                        new DownloadInfo("https://example.invalid/client.jar", sha1))),
                GameComponentType.GAME.getPatchId(), "new-version", GameInstancePatch.PRIORITY_MC)));

        assertTrue(manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).test());

        assertEquals(replacement, repository.getInstance(id).getManifest());
        assertEquals("new client", Files.readString(target));
    }

    /// Switching JAR ownership materializes an owned client but never replaces a referenced client.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testUpdateInstanceAsyncSwitchesJarOwnership(boolean useOwnJar, @TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceID sharedId = new GameInstanceID("shared");
        repository.save(new GameInstanceManifest(sharedId));
        Path sharedJar = repository.getLayout().getInstanceJarFile(sharedId);
        Files.writeString(sharedJar, "shared client");
        String sha1 = DigestUtils.digestToString("SHA-1", sharedJar);
        DownloadInfo download = new DownloadInfo("https://example.invalid/client.jar", sha1);
        GameInstanceManifest original = new GameInstanceManifest(id)
                .withJar(useOwnJar ? sharedId : id)
                .withDownloads(useOwnJar ? Map.of(DownloadType.CLIENT, download) : null);
        repository.save(original);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "custom client");
        DefaultDependencyManager manager = createDependencyManager(repository);
        manager.getCacheRepository().cacheFile(sharedJar, "SHA-1", sha1);
        GameInstanceManifest replacement = original.withJar(useOwnJar ? id : sharedId)
                .withDownloads(Map.of(DownloadType.CLIENT, download));

        assertTrue(manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).test());

        assertEquals(useOwnJar ? "shared client" : "custom client", Files.readString(target));
        assertEquals("shared client", Files.readString(sharedJar));
        assertEquals(useOwnJar ? target : sharedJar, repository.getInstance(id).getInstanceJarFile());
    }

    /// A failed JAR lookup leaves both the old manifest and its client JAR intact.
    @Test
    public void testUpdateInstanceAsyncPreservesFilesWhenJarTaskFails(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        repository.save(original);
        Path manifestFile = repository.getLayout().getInstanceJson(id);
        String originalJson = Files.readString(manifestFile);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "original");

        GameInstanceManifest replacement = original.withDownloads(Map.of(DownloadType.CLIENT,
                new DownloadInfo("file:///missing-client.jar", "0".repeat(40))));
        Task<?> update = createDependencyManager(repository).updateInstanceAsync(id,
                instance -> Task.completed(replacement));
        assertFalse(update.test());

        assertEquals(original, repository.getInstance(id).getManifest());
        assertEquals(originalJson, Files.readString(manifestFile));
        assertEquals("original", Files.readString(target));
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            assertTrue(draft.isOpen());
        }
    }

    /// Publication failure rolls back both files after an asynchronous update stages a new JAR.
    @Test
    public void testUpdateInstanceAsyncRollsBackManifestAndJar(@TempDir Path tempDirectory) throws Exception {
        FailingRepository repository = new FailingRepository(tempDirectory.resolve("game"));
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        repository.save(original);
        Path manifestFile = repository.getLayout().getInstanceJson(id);
        String originalJson = Files.readString(manifestFile);
        Path target = repository.getLayout().getInstanceJarFile(id);
        Files.writeString(target, "original");
        Path source = tempDirectory.resolve("replacement.jar");
        Files.writeString(source, "replacement");
        repository.failDraftPublish = true;

        DefaultDependencyManager manager = createDependencyManager(repository);
        String sha1 = DigestUtils.digestToString("SHA-1", source);
        Path cached = manager.getCacheRepository().cacheFile(source, "SHA-1", sha1);
        GameInstanceManifest replacement = original.withMainClass("updated.Main")
                .withDownloads(Map.of(DownloadType.CLIENT,
                        new DownloadInfo("https://example.invalid/client.jar", sha1)));
        Task<?> update = manager.updateInstanceAsync(id, instance -> Task.completed(replacement));
        assertFalse(update.test());

        assertEquals(original, repository.getInstance(id).getManifest());
        assertEquals(originalJson, Files.readString(manifestFile));
        assertEquals("original", Files.readString(target));
        assertEquals("replacement", Files.readString(source));
        assertEquals("replacement", Files.readString(cached));
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            assertTrue(draft.isOpen());
        }
    }

    /// Rejects an asynchronous update that attempts to create a different instance id.
    @Test
    public void testUpdateInstanceAsyncRejectsChangedId(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceID otherId = new GameInstanceID("other");
        repository.save(new GameInstanceManifest(id).withMainClass("original.Main"));

        Task<?> update = createDependencyManager(repository).updateInstanceAsync(id, workingInstance ->
                Task.supplyAsync(() -> workingInstance.getManifest().withId(otherId)));
        assertFalse(update.test());

        assertEquals("original.Main", repository.getInstance(id).getManifest().mainClass());
        assertFalse(repository.hasInstance(otherId));
        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Releases the draft when the target disappears before the updater is invoked.
    @Test
    public void testUpdateInstanceAsyncAbortsDraftWhenTargetIsMissing(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("missing");

        Task<?> update = createDependencyManager(repository).updateInstanceAsync(id, workingInstance ->
                Task.completed(workingInstance.getManifest()));
        assertFalse(update.test());

        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Releases the draft when the updater throws before returning its task.
    @Test
    public void testUpdateInstanceAsyncAbortsDraftWhenUpdaterThrows(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id));

        Task<?> update = createDependencyManager(repository).updateInstanceAsync(id, workingInstance -> {
            throw new IOException("Simulated updater failure");
        });
        assertFalse(update.test());

        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Cancellation in the updater releases the draft even when its returned task never starts.
    @Test
    public void testUpdateInstanceAsyncAbortsDraftBeforeChildStarts(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest original = new GameInstanceManifest(id).withMainClass("original.Main");
        repository.save(original);
        DefaultDependencyManager manager = createDependencyManager(repository);
        GameInstanceManifest replacement = original.withMainClass("updated.Main");
        Task<@Nullable Void> root = new Task<>() {
            /// Cancels the tree while the updater owns the draft.
            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                return context.run(manager.updateInstanceAsync(id, instance -> {
                    cancel();
                    return Task.completed(replacement);
                }));
            }
        };

        assertThrows(CancellationException.class, root::run);
        assertEquals(original, repository.getInstance(id).getManifest());
        manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).run();
        assertEquals(replacement, repository.getInstanceManifest(id));
    }

    /// Cancellation observed by a snapshot listener preserves a published installation or update.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationDuringPublicationPreservesTaskResult(boolean update, @TempDir Path directory) throws Exception {
        TestRepository repository = new TestRepository(directory);
        GameInstanceID id = new GameInstanceID("instance");
        Task<?> operation;
        if (update) {
            repository.save(new GameInstanceManifest(id));
            operation = createDependencyManager(repository).updateInstanceAsync(id, instance ->
                    Task.completed(instance.getManifest().withMainClass(GameComponentAnalyzer.VANILLA_MAIN)));
        } else {
            DefaultDependencyManager manager = createOfflineGameInstaller(repository, id, "1.21.2");
            operation = new Task<DefaultGameInstance>() {
                /// Builds the instance as the final result of this installation.
                @Override
                public DefaultGameInstance execute(TaskExecutionContext context) throws Exception {
                    try (DefaultGameBuilder builder = manager.newGameBuilder(id)) {
                        builder.component(GameComponentType.GAME, "1.21.2");
                        return builder.buildAsTaskResult(context);
                    }
                }
            };
        }
        repository.snapshotProperty().addListener((observable, previous, current) -> operation.cancel());

        operation.run();
        assertTrue(operation.isCancellationRequested());
        assertEquals(GameComponentAnalyzer.VANILLA_MAIN, repository.getInstance(id).getResolvedManifest().mainClass());
    }

    /// Releases the draft when the updater's task raises an error during execution.
    @Test
    public void testUpdateInstanceAsyncAbortsDraftOnTaskError(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id));

        AssertionError error = new AssertionError("updater task");
        Task<?> update = createDependencyManager(repository).updateInstanceAsync(id, instance -> Task.failed(error));
        assertSame(error, assertThrows(AssertionError.class, update::run));
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            assertTrue(draft.isOpen());
        }
    }

    /// Runs a configured game build as a root task.
    private static Task<GameInstance> buildTask(GameBuilder builder) {
        return new Task<>() {
            /// {@inheritDoc}
            @Override
            public GameInstance execute(TaskExecutionContext context) throws Exception {
                return builder.build(context);
            }
        };
    }

    /// Writes a minimal client JAR whose version can be detected after committing and refreshing.
    private static void writeVersionJar(Path jar, String version) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry("version.json"));
            output.write(("{\"id\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    /// Creates a dependency manager with a cache isolated inside the test repository.
    private static DefaultDependencyManager createDependencyManager(TestRepository repository) {
        DefaultCacheRepository cacheRepository =
                new DefaultCacheRepository(repository.getBaseDirectory().resolve("cache"));
        return new DefaultDependencyManager(repository, new TestDownloadProvider(cacheRepository), cacheRepository);
    }

    /// Provides a fixed game patch and cached JAR for offline game builder installations.
    private static DefaultDependencyManager createOfflineGameInstaller(
            TestRepository repository, GameInstanceID id, String version) throws IOException {
        DefaultCacheRepository cache = new DefaultCacheRepository(repository.getBaseDirectory().resolve("cache"));
        Path jar = repository.getBaseDirectory().resolve("client.jar");
        writeVersionJar(jar, version);
        String sha1 = DigestUtils.digestToString("SHA-1", jar);
        cache.cacheFile(jar, "SHA-1", sha1);
        GameInstanceManifest manifest = new GameInstanceManifest(id)
                .withMainClass(GameComponentAnalyzer.VANILLA_MAIN)
                .withDownloads(Map.of(DownloadType.CLIENT, new DownloadInfo("https://example.invalid/client.jar", sha1)));
        GameInstancePatch patch = GameInstancePatch.fromManifest(manifest, GameComponentType.GAME.getPatchId(),
                version, GameInstancePatch.PRIORITY_MC);
        ComponentRemoteVersion remote = fixedComponent(GameComponentType.GAME, version, patch);
        DownloadProvider provider = new DownloadProvider() {
            /// Supplies the local game version for the build task.
            @Override
            protected Task<? extends ComponentRemoteVersionList<?>> fetchVersionsAsync(
                    GameComponentType type, @Nullable GameVersionNumber gameVersion) {
                return Task.completed(ComponentRemoteVersionList.of(type, new TreeSet<>(List.of(remote))));
            }
        };
        return new DefaultDependencyManager(repository, provider, cache);
    }

    /// Creates a component version whose install task returns the supplied patch.
    private static ComponentRemoteVersion fixedComponent(GameComponentType componentType, String gameVersion,
                                                         GameInstancePatch patch) {
        return new ComponentRemoteVersion(componentType, GameVersionNumber.asGameVersion(gameVersion),
                Objects.requireNonNull(patch.version()), null, ComponentRemoteVersion.Type.RELEASE, List.of()) {
            /// Supplies a fixed installer result while exercising the actual installation and commit pipeline.
            @Override
            public Task<GameInstancePatch> getInstallTask(
                    DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
                return Task.completed(patch);
            }
        };
    }

    /// Minimal repository implementation for draft tests.
    @NotNullByDefault
    private static class TestRepository extends DefaultGameRepository {

        /// Creates a test repository rooted at `baseDirectory`.
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
    }

    /// Repository that can fail draft publication before changing the published snapshot.
    @NotNullByDefault
    private static final class FailingRepository extends TestRepository {

        /// Whether the next draft publication should fail.
        private boolean failDraftPublish;

        /// Creates a failing test repository rooted at `baseDirectory`.
        ///
        /// @param baseDirectory the repository base directory
        private FailingRepository(Path baseDirectory) {
            super(baseDirectory);
        }

        /// {@inheritDoc}
        @Override
        void publishDraftSnapshot(
                DefaultGameRepositoryDraft draft,
                DefaultGameRepositorySnapshot newSnapshot) {
            if (failDraftPublish) {
                failDraftPublish = false;
                throw new IllegalStateException("Simulated publication failure");
            }
            super.publishDraftSnapshot(draft, newSnapshot);
        }
    }

    /// Minimal snapshot-bound instance implementation for draft tests.
    @NotNullByDefault
    private static final class TestGameInstance extends DefaultGameInstance {

        /// Creates a test instance.
        ///
        /// @param snapshot     the owning snapshot
        /// @param id           the instance id
        /// @param manifest     the stored manifest
        /// @param manifestFile the non-conventional manifest path, or `null`
        private TestGameInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            super(snapshot, id, manifest, manifestFile);
        }

        /// Creates a test instance that may reuse compatible state.
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
    }
}
