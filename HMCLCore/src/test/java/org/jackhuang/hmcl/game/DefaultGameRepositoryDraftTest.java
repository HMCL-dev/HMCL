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
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests exclusive repository drafts and their filesystem visibility boundary.
@NotNullByDefault
public final class DefaultGameRepositoryDraftTest {

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
        assertTrue(update.executor().test());

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
                repository, new MojangDownloadProvider(), cache);
        GameInstanceManifest replacement = original.withDownloads(Map.of(DownloadType.CLIENT,
                new DownloadInfo("https://example.invalid/client.jar", sha1)));

        Task<?> update = manager.updateInstanceAsync(id, instance ->
                new GameDownloadTask(manager, replacement).thenApplyAsync(jar -> {
                    assertEquals("old client", Files.readString(target));
                    assertEquals(original, repository.getInstance(id).getManifest());
                    return replacement;
                }));
        assertTrue(update.executor().test());

        assertEquals(replacement, repository.getInstance(id).getManifest());
        assertTrue(replacement.getDownloadInfo().validateChecksum(target, false));
        assertEquals("new client", Files.readString(cached));
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
                instance -> Task.completed(replacement)).executor().test());

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
        manager.getCacheRepository().cacheText(new UrlResponseInfo(200, URI.create(newUrl),
                HttpHeaders.of(Map.of("etag", List.of("new-client"),
                        "cache-control", List.of("max-age=3600")), (name, value) -> true)), "new client");
        GameInstanceManifest replacement = original.withDownloads(Map.of(DownloadType.CLIENT, new DownloadInfo(newUrl)));

        assertTrue(manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).executor().test());

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

        assertTrue(manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).executor().test());

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

        assertTrue(manager.updateInstanceAsync(id, instance -> Task.completed(replacement)).executor().test());

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
        assertFalse(update.executor().test());

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
        assertFalse(update.executor().test());

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
        assertFalse(update.executor().test());

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
        assertFalse(update.executor().test());

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
        assertFalse(update.executor().test());

        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Creates a dependency manager with a cache isolated inside the test repository.
    private static DefaultDependencyManager createDependencyManager(TestRepository repository) {
        return new DefaultDependencyManager(repository, new MojangDownloadProvider(),
                new DefaultCacheRepository(repository.getBaseDirectory().resolve("cache")));
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
