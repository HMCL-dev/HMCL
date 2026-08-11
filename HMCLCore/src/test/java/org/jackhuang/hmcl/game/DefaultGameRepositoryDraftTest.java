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

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /// Keeps a new manifest private until commit and publishes the resulting instance once committed.
    @Test
    public void testCommitPublishesStagedManifest(@TempDir Path tempDirectory) throws IOException {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceManifest manifest = new GameInstanceManifest(id).withMainClass("example.Main");
        Path manifestFile = repository.getLayout().getInstanceJson(id);

        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            draft.put(manifest);

            assertFalse(repository.hasInstance(id));
            assertFalse(Files.exists(manifestFile));

            GameRepositorySnapshot committed = draft.commit();
            assertEquals(GameRepositoryDraftState.COMMITTED, draft.getState());
            assertEquals(manifest, committed.getInstance(id).getManifest());
        }

        assertTrue(repository.hasInstance(id));
        GameInstanceManifest stored = JsonUtils.fromNonNullJson(
                Files.readString(manifestFile),
                GameInstanceManifest.class);
        assertEquals(id, stored.id());
        assertEquals("example.Main", stored.mainClass());
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

    /// Keeps a staged removal private and preserves the published files when the draft aborts.
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

    /// Restores staged manifests and releases exclusivity when publication fails before replacement.
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

        assertEquals(GameRepositoryDraftState.FAILED, draft.getState());
        assertEquals(original, repository.getInstance(id).getManifest());
        GameInstanceManifest stored = JsonUtils.fromNonNullJson(
                Files.readString(repository.getLayout().getInstanceJson(id)),
                GameInstanceManifest.class);
        assertEquals("original.Main", stored.mainClass());
        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
    }

    /// Runs an asynchronous update using the published instance as immutable context.
    @Test
    public void testUpdateInstanceAsyncCommitsWorkingManifest(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        repository.save(new GameInstanceManifest(id).withMainClass("original.Main"));

        Task<?> update = repository.updateInstanceAsync(id, workingInstance -> Task.supplyAsync(() ->
                workingInstance.getManifest().withMainClass("updated.Main")));
        assertTrue(update.executor().test());

        assertEquals("updated.Main", repository.getInstance(id).getManifest().mainClass());
    }

    /// Rejects an asynchronous update that attempts to create a different instance id.
    @Test
    public void testUpdateInstanceAsyncRejectsChangedId(@TempDir Path tempDirectory) throws Exception {
        TestRepository repository = new TestRepository(tempDirectory);
        GameInstanceID id = new GameInstanceID("instance");
        GameInstanceID otherId = new GameInstanceID("other");
        repository.save(new GameInstanceManifest(id).withMainClass("original.Main"));

        Task<?> update = repository.updateInstanceAsync(id, workingInstance ->
                Task.supplyAsync(() -> workingInstance.getManifest().withId(otherId)));
        assertFalse(update.executor().test());

        assertEquals("original.Main", repository.getInstance(id).getManifest().mainClass());
        assertFalse(repository.hasInstance(otherId));
        try (DefaultGameRepositoryDraft ignored = repository.openDraft()) {
            assertTrue(ignored.isOpen());
        }
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
