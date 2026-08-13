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

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default exclusive [GameRepositoryDraft] implementation.
///
/// Manifest changes are retained in memory until commit. A successful commit writes the final
/// manifests, applies removals and renames, and publishes one new immutable snapshot. Shared library
/// and asset cache writes are outside the rollback boundary. Instances of this class are not
/// thread-safe.
@NotNullByDefault
public final class DefaultGameRepositoryDraft implements GameRepositoryDraft {

    /// Repository whose published snapshot will be replaced on commit.
    private final DefaultGameRepository repository;

    /// Immutable published snapshot captured when this draft was opened.
    private final DefaultGameRepositorySnapshot baseSnapshot;

    /// Current unpublished manifests keyed by instance id.
    private final Map<GameInstanceID, GameInstanceManifest> manifests = new TreeMap<>();

    /// Instance ids whose final manifests differ from the published snapshot.
    private final Set<GameInstanceID> modifiedIds = new TreeSet<>();

    /// Instance ids whose root directories were absent before this draft first added them.
    private final Set<GameInstanceID> createdIds = new TreeSet<>();

    /// Instance ids absent from the final manifest set.
    private final Set<GameInstanceID> removedIds = new TreeSet<>();

    /// Ordered instance renames applied to the filesystem during commit.
    private final List<RenameOperation> renames = new ArrayList<>();

    /// Current lifecycle state.
    private GameRepositoryDraftState state = GameRepositoryDraftState.OPEN;

    /// Creates an open draft over the repository's current published snapshot.
    ///
    /// @param repository the repository that owns this draft
    DefaultGameRepositoryDraft(DefaultGameRepository repository) {
        this.repository = repository;
        this.baseSnapshot = repository.getSnapshot();
        for (GameInstanceManifest manifest : baseSnapshot.getInstanceManifests()) {
            manifests.put(manifest.id(), manifest);
        }
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    /// {@inheritDoc}
    @Override
    public GameRepositoryDraftState getState() {
        return state;
    }

    /// {@inheritDoc}
    @Override
    public boolean isOpen() {
        return state == GameRepositoryDraftState.OPEN;
    }

    /// {@inheritDoc}
    @Override
    public boolean isCommitted() {
        return state == GameRepositoryDraftState.COMMITTED;
    }

    /// {@inheritDoc}
    @Override
    public void put(GameInstanceManifest manifest) throws IOException {
        checkOpen();
        putManifest(manifest, true);
    }

    /// {@inheritDoc}
    @Override
    public void remove(GameInstanceID instanceId) {
        checkOpen();
        if (manifests.remove(instanceId) == null) {
            throw new NoSuchGameInstanceException(instanceId);
        }

        modifiedIds.remove(instanceId);
        removedIds.add(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public void rename(GameInstanceID from, GameInstanceID to) throws IOException {
        checkOpen();
        @Nullable GameInstanceManifest source = manifests.get(from);
        if (source == null) {
            throw new NoSuchGameInstanceException(from);
        }
        if (createdIds.contains(from)) {
            throw new IllegalStateException("Cannot rename an instance created by the same draft");
        }
        if (manifests.containsKey(to)) {
            throw new IllegalArgumentException("Target instance already exists: " + to);
        }

        Path targetRoot = getValidatedInstanceRoot(to);
        if (Files.exists(targetRoot)) {
            throw new FileAlreadyExistsException(targetRoot.toString());
        }

        GameInstanceManifest renamedManifest = source;
        if (from.equals(renamedManifest.jar())) {
            renamedManifest = renamedManifest.withJar(null);
        }
        renamedManifest = renamedManifest.withId(to);

        manifests.remove(from);
        modifiedIds.remove(from);
        removedIds.remove(from);
        putManifest(renamedManifest, false);

        manifests.replaceAll((id, manifest) -> {
            if (!from.equals(manifest.inheritsFrom())) {
                return manifest;
            }
            modifiedIds.add(id);
            return manifest.withInheritsFrom(to);
        });
        renames.add(new RenameOperation(from, to));
    }

    /// Updates one manifest in the in-memory write set.
    ///
    /// @param manifest     the manifest to retain
    /// @param claimNewRoot whether a previously absent instance root should become draft-owned
    /// @throws IOException if a new instance root cannot be claimed or initialized
    private void putManifest(
            GameInstanceManifest manifest,
            boolean claimNewRoot) throws IOException {

        GameInstanceID id = manifest.id();
        if (claimNewRoot && !manifests.containsKey(id)) {
            Path root = getValidatedInstanceRoot(id);
            if (!repository.mayClaimDraftInstanceRoot(id, root)) {
                throw new FileAlreadyExistsException(root.toString(), null,
                        "An unregistered instance directory already exists");
            }
            createdIds.add(id);
            repository.initializeDraftInstanceRoot(id, root);
        }

        manifests.put(id, manifest);
        modifiedIds.add(id);
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepositorySnapshot commit() throws IOException {
        checkOpen();
        repository.checkActiveDraft(this);
        state = GameRepositoryDraftState.COMMITTING;

        List<RenameOperation> appliedRenames = new ArrayList<>();
        List<RemovedRoot> removedRoots = new ArrayList<>();
        List<AppliedManifest> applied = new ArrayList<>();
        @Nullable Path rollbackDirectory = null;
        try {
            DefaultGameRepositorySnapshot committedSnapshot = buildCommittedSnapshot();
            for (RenameOperation rename : renames) {
                applyRename(rename, appliedRenames);
            }

            if (!removedIds.isEmpty() || !modifiedIds.isEmpty()) {
                Path currentRollbackDirectory = createRollbackDirectory();
                rollbackDirectory = currentRollbackDirectory;
                for (GameInstanceID id : removedIds) {
                    removeInstanceRoot(id, currentRollbackDirectory, removedRoots);
                }
                for (GameInstanceID id : modifiedIds) {
                    @Nullable GameInstanceManifest manifest = manifests.get(id);
                    if (manifest == null) {
                        throw new IllegalStateException("Modified manifest is missing: " + id);
                    }
                    applyManifest(id, manifest, currentRollbackDirectory, applied);
                }
            }

            repository.publishDraftSnapshot(this, committedSnapshot);
            state = GameRepositoryDraftState.COMMITTED;
            repository.releaseDraft(this);
            cleanupRollbackDirectoryAfterCommit(rollbackDirectory);
            return committedSnapshot;
        } catch (IOException | RuntimeException e) {
            IOException rollbackFailure = rollbackAppliedManifests(applied);
            rollbackFailure = accumulateNullable(rollbackFailure, rollbackRemovedRoots(removedRoots));
            rollbackFailure = accumulateNullable(rollbackFailure, rollbackRenames(appliedRenames));
            state = GameRepositoryDraftState.FAILED;
            repository.releaseDraft(this);
            IOException cleanupFailure = cleanupCreatedInstanceRoots();
            cleanupFailure = accumulateNullable(cleanupFailure, cleanupRollbackDirectory(rollbackDirectory));
            if (rollbackFailure != null) {
                e.addSuppressed(rollbackFailure);
            }
            if (cleanupFailure != null) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    /// Builds the immutable successor snapshot represented by the final manifest write set.
    ///
    /// @return the sealed snapshot to publish after filesystem changes succeed
    private DefaultGameRepositorySnapshot buildCommittedSnapshot() {
        DefaultGameRepositorySnapshot committedSnapshot = baseSnapshot.mutableCopy();
        for (RenameOperation rename : renames) {
            committedSnapshot.remove(rename.from());
        }
        for (GameInstanceID id : removedIds) {
            committedSnapshot.remove(id);
        }
        for (GameInstanceID id : modifiedIds) {
            @Nullable GameInstanceManifest manifest = manifests.get(id);
            if (manifest == null) {
                throw new IllegalStateException("Modified manifest is missing: " + id);
            }
            @Nullable DefaultGameInstance existing = committedSnapshot.get(manifest.id());
            DefaultGameInstance updated = existing != null
                    ? existing.withManifest(committedSnapshot, manifest)
                    : repository.createInstance(committedSnapshot, manifest.id(), manifest);
            committedSnapshot.put(updated);
        }
        committedSnapshot.seal();
        return committedSnapshot;
    }

    /// {@inheritDoc}
    @Override
    public void abort() throws IOException {
        if (state == GameRepositoryDraftState.ABORTED) {
            return;
        }
        if (state == GameRepositoryDraftState.COMMITTED) {
            throw new IllegalStateException("Draft is already committed");
        }
        if (state == GameRepositoryDraftState.COMMITTING) {
            throw new IllegalStateException("Draft is committing");
        }
        if (state == GameRepositoryDraftState.FAILED) {
            return;
        }

        IOException failure = cleanupCreatedInstanceRoots();
        state = failure == null ? GameRepositoryDraftState.ABORTED : GameRepositoryDraftState.FAILED;
        repository.releaseDraft(this);
        if (failure != null) {
            throw failure;
        }
    }

    /// {@inheritDoc}
    @Override
    public void close() throws IOException {
        if (state == GameRepositoryDraftState.OPEN) {
            abort();
        }
    }

    /// Returns the permanent manifest target for an instance.
    ///
    /// Existing instances retain a non-conventional manifest path discovered by refresh. New
    /// instances use the conventional path from the base layout.
    ///
    /// @param id the instance id
    /// @return the permanent manifest path
    private Path getManifestTarget(GameInstanceID id) {
        @Nullable DefaultGameInstance existing = baseSnapshot.get(id);
        return (existing != null ? existing.getManifestFile() : baseSnapshot.getLayout().getInstanceJson(id))
                .toAbsolutePath()
                .normalize();
    }

    /// Creates a directory for rollback data produced by the current commit attempt.
    ///
    /// @return the new rollback directory
    /// @throws IOException if the directory cannot be created
    private Path createRollbackDirectory() throws IOException {
        Path parent = baseSnapshot.getLayout().getBaseDirectory()
                .toAbsolutePath()
                .normalize()
                .resolve(".hmcl")
                .resolve("repository-drafts");
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, "commit-");
    }

    /// Applies one instance directory rename.
    ///
    /// @param rename  the requested rename
    /// @param applied rollback records for completed renames
    /// @throws IOException if the source files cannot be renamed
    private void applyRename(RenameOperation rename, List<RenameOperation> applied) throws IOException {
        Path sourceRoot = getValidatedInstanceRoot(rename.from());
        Path targetRoot = getValidatedInstanceRoot(rename.to());
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Instance directory does not exist: " + sourceRoot);
        }
        if (Files.exists(targetRoot)) {
            throw new FileAlreadyExistsException(targetRoot.toString());
        }

        DefaultGameRepository.moveInstanceFiles(
                baseSnapshot.getLayout().getBaseDirectory(),
                rename.from(),
                rename.to());
        applied.add(rename);
    }

    /// Moves one removed instance root into the commit rollback directory.
    ///
    /// @param id                the removed instance id
    /// @param rollbackDirectory the directory owned by the current commit attempt
    /// @param removed           rollback records for roots moved out of the repository
    /// @throws IOException if the root cannot be moved into the rollback directory
    private void removeInstanceRoot(
            GameInstanceID id,
            Path rollbackDirectory,
            List<RemovedRoot> removed) throws IOException {
        Path root = getValidatedInstanceRoot(id);
        if (Files.notExists(root)) {
            return;
        }

        Path removals = rollbackDirectory.resolve("removed");
        Files.createDirectories(removals);
        Path rollbackRoot = Files.createTempDirectory(removals, "instance-");
        Files.delete(rollbackRoot);
        moveReplacing(root, rollbackRoot);
        removed.add(new RemovedRoot(root, rollbackRoot));
    }

    /// Writes one manifest while retaining a rollback copy.
    ///
    /// @param id                the instance whose manifest will be replaced
    /// @param manifest          the final manifest
    /// @param rollbackDirectory the directory owned by the current commit attempt
    /// @param applied           rollback records for changes already started
    /// @throws IOException if the target cannot be backed up or replaced
    private void applyManifest(
            GameInstanceID id,
            GameInstanceManifest manifest,
            Path rollbackDirectory,
            List<AppliedManifest> applied) throws IOException {
        String json = JsonUtils.GSON.toJson(manifest);
        Path target = getManifestTarget(id);
        Path expectedRoot = getValidatedInstanceRoot(id);
        if (target.equals(expectedRoot) || !target.startsWith(expectedRoot)) {
            throw new IOException("Manifest path escapes instance root: " + target);
        }

        Files.createDirectories(target.getParent());
        @Nullable Path backup = null;
        if (Files.exists(target)) {
            Path backups = rollbackDirectory.resolve("backups");
            Files.createDirectories(backups);
            backup = Files.createTempFile(backups, "manifest-", ".json");
            Files.delete(backup);
            moveReplacing(target, backup);
        }

        applied.add(new AppliedManifest(target, backup));
        Files.writeString(target, json);
    }

    /// Restores manifests changed by an unsuccessful commit in reverse application order.
    ///
    /// @param applied applied manifest records
    /// @return the aggregated rollback failure, or `null` when rollback succeeded
    private static @Nullable IOException rollbackAppliedManifests(List<AppliedManifest> applied) {
        @Nullable IOException failure = null;
        List<AppliedManifest> reversed = new ArrayList<>(applied);
        Collections.reverse(reversed);
        for (AppliedManifest manifest : reversed) {
            try {
                Files.deleteIfExists(manifest.targetFile());
                if (manifest.backupFile() != null) {
                    moveReplacing(manifest.backupFile(), manifest.targetFile());
                }
            } catch (IOException e) {
                failure = accumulate(failure, e);
            }
        }
        return failure;
    }

    /// Restores roots moved out of the repository by an unsuccessful commit.
    ///
    /// @param removed removed-root rollback records
    /// @return the aggregated rollback failure, or `null` when rollback succeeded
    private static @Nullable IOException rollbackRemovedRoots(List<RemovedRoot> removed) {
        @Nullable IOException failure = null;
        List<RemovedRoot> reversed = new ArrayList<>(removed);
        Collections.reverse(reversed);
        for (RemovedRoot root : reversed) {
            try {
                moveReplacing(root.rollbackRoot(), root.originalRoot());
            } catch (IOException e) {
                failure = accumulate(failure, e);
            }
        }
        return failure;
    }

    /// Reverses instance renames completed by an unsuccessful commit.
    ///
    /// @param applied completed rename records
    /// @return the aggregated rollback failure, or `null` when rollback succeeded
    private @Nullable IOException rollbackRenames(List<RenameOperation> applied) {
        @Nullable IOException failure = null;
        List<RenameOperation> reversed = new ArrayList<>(applied);
        Collections.reverse(reversed);
        for (RenameOperation rename : reversed) {
            try {
                DefaultGameRepository.moveInstanceFiles(
                        baseSnapshot.getLayout().getBaseDirectory(),
                        rename.to(),
                        rename.from());
            } catch (IOException e) {
                failure = accumulate(failure, e);
            }
        }
        return failure;
    }

    /// Removes instance roots first created by this draft.
    ///
    /// @return the aggregated cleanup failure, or `null` when cleanup succeeded
    private @Nullable IOException cleanupCreatedInstanceRoots() {
        @Nullable IOException failure = null;
        for (GameInstanceID id : createdIds) {
            try {
                Path root = getValidatedInstanceRoot(id);
                if (Files.exists(root)) {
                    FileUtils.deleteDirectory(root);
                }
            } catch (IOException | RuntimeException e) {
                IOException cleanupException = e instanceof IOException ioException
                        ? ioException
                        : new IOException("Failed to remove draft-created instance " + id, e);
                failure = accumulate(failure, cleanupException);
            }
        }
        return failure;
    }

    /// Removes a commit rollback directory.
    ///
    /// @param rollbackDirectory the directory to remove, or `null` if none was created
    /// @return the cleanup failure, or `null` when cleanup succeeded
    private static @Nullable IOException cleanupRollbackDirectory(@Nullable Path rollbackDirectory) {
        if (rollbackDirectory == null) {
            return null;
        }
        try {
            FileUtils.deleteDirectory(rollbackDirectory);
            return null;
        } catch (IOException e) {
            return e;
        }
    }

    /// Removes rollback data after a successful commit without changing its outcome.
    ///
    /// @param rollbackDirectory the directory to remove, or `null` if none was created
    private static void cleanupRollbackDirectoryAfterCommit(@Nullable Path rollbackDirectory) {
        if (rollbackDirectory == null) {
            return;
        }
        try {
            FileUtils.deleteDirectory(rollbackDirectory);
        } catch (IOException e) {
            LOG.warning("Failed to remove commit rollback directory " + rollbackDirectory, e);
        }
    }

    /// Returns a normalized instance root after verifying that it is a strict descendant of the
    /// repository's versions directory.
    ///
    /// @param id the instance id
    /// @return the validated instance root
    /// @throws IOException if the resolved root escapes the versions directory
    private Path getValidatedInstanceRoot(GameInstanceID id) throws IOException {
        Path versions = baseSnapshot.getLayout().getBaseDirectory()
                .toAbsolutePath()
                .normalize()
                .resolve("versions");
        Path root = baseSnapshot.getLayout().getInstanceRoot(id).toAbsolutePath().normalize();
        if (root.equals(versions) || !root.startsWith(versions)) {
            throw new IOException("Instance root escapes versions directory: " + root);
        }
        return root;
    }

    /// Moves a file to `target`, using an atomic move when supported by the file system.
    ///
    /// @param source the source file
    /// @param target the target file
    /// @throws IOException if both atomic and regular replacement fail
    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException replacementFailure) {
                replacementFailure.addSuppressed(atomicFailure);
                throw replacementFailure;
            }
        }
    }

    /// Aggregates an additional cleanup failure.
    ///
    /// @param current    the current aggregate, or `null`
    /// @param additional the additional failure
    /// @return the resulting aggregate
    private static IOException accumulate(@Nullable IOException current, IOException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    /// Combines two optional failure aggregates.
    ///
    /// @param current    the current aggregate, or `null`
    /// @param additional the additional aggregate, or `null`
    /// @return the combined aggregate, or `null` when both arguments are `null`
    private static @Nullable IOException accumulateNullable(
            @Nullable IOException current,
            @Nullable IOException additional) {
        if (additional == null) {
            return current;
        }
        return accumulate(current, additional);
    }

    /// Ensures the draft accepts changes.
    ///
    /// @throws IllegalStateException if the draft is not open
    private void checkOpen() {
        if (state != GameRepositoryDraftState.OPEN) {
            throw new IllegalStateException("Draft is " + state.name().toLowerCase());
        }
    }

    /// Records enough information to roll back one manifest replacement.
    ///
    /// @param targetFile the permanent manifest path
    /// @param backupFile the prior manifest backup, or `null` when no prior file existed
    private record AppliedManifest(
            Path targetFile,
            @Nullable Path backupFile) {
    }

    /// Records an instance rename requested by the draft.
    ///
    /// @param from the source instance id
    /// @param to   the target instance id
    private record RenameOperation(GameInstanceID from, GameInstanceID to) {
    }

    /// Records an instance root moved aside for rollback during commit.
    ///
    /// @param originalRoot the published instance root
    /// @param rollbackRoot the temporary rollback path
    private record RemovedRoot(Path originalRoot, Path rollbackRoot) {
    }
}
