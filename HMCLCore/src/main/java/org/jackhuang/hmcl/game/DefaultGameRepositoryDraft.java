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
/// Manifest changes are reflected in an unpublished working snapshot and serialized below a
/// draft-private directory. Instance installers may use the returned working [GameInstance] and
/// write instance-owned files before commit. A successful commit moves all staged manifests into
/// place and publishes the working snapshot once. Shared library and asset cache writes are outside
/// the rollback boundary.
@NotNullByDefault
public final class DefaultGameRepositoryDraft implements GameRepositoryDraft {

    /// Repository whose published snapshot will be replaced on commit.
    private final DefaultGameRepository repository;

    /// Immutable published snapshot captured when this draft was opened.
    private final DefaultGameRepositorySnapshot baseSnapshot;

    /// Mutable snapshot containing the draft's unpublished manifest changes.
    private final DefaultGameRepositorySnapshot workingSnapshot;

    /// Staged manifest files keyed by instance id.
    private final Map<GameInstanceID, StagedManifest> stagedManifests = new TreeMap<>();

    /// Instance ids whose root directories were absent before this draft first staged them.
    private final Set<GameInstanceID> createdIds = new TreeSet<>();

    /// Instance ids removed from the working snapshot.
    private final Set<GameInstanceID> removedIds = new TreeSet<>();

    /// Ordered instance renames applied to the filesystem during commit.
    private final List<RenameOperation> renames = new ArrayList<>();

    /// Draft-private directory containing staged manifests and commit backups.
    private @Nullable Path stagingDirectory;

    /// Current lifecycle state.
    private GameRepositoryDraftState state = GameRepositoryDraftState.OPEN;

    /// Creates an open draft over the repository's current published snapshot.
    ///
    /// @param repository the repository that owns this draft
    DefaultGameRepositoryDraft(DefaultGameRepository repository) {
        this.repository = repository;
        this.baseSnapshot = repository.getSnapshot();
        this.workingSnapshot = baseSnapshot.clone();
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    /// {@inheritDoc}
    @Override
    public GameRepositorySnapshot getBaseSnapshot() {
        return baseSnapshot;
    }

    /// {@inheritDoc}
    @Override
    public synchronized GameRepositorySnapshot getSnapshot() {
        if (state == GameRepositoryDraftState.ABORTED || state == GameRepositoryDraftState.FAILED) {
            throw new IllegalStateException("Draft is " + state.name().toLowerCase());
        }
        return workingSnapshot;
    }

    /// {@inheritDoc}
    @Override
    public synchronized GameRepositoryDraftState getState() {
        return state;
    }

    /// {@inheritDoc}
    @Override
    public synchronized boolean isOpen() {
        return state == GameRepositoryDraftState.OPEN;
    }

    /// {@inheritDoc}
    @Override
    public synchronized boolean isCommitted() {
        return state == GameRepositoryDraftState.COMMITTED;
    }

    /// {@inheritDoc}
    @Override
    public synchronized boolean hasInstance(GameInstanceID instanceId) {
        checkOpen();
        return workingSnapshot.hasInstance(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public synchronized DefaultGameInstance put(GameInstanceManifest manifest) throws IOException {
        checkOpen();
        return stageManifest(manifest, true);
    }

    /// {@inheritDoc}
    @Override
    public synchronized void remove(GameInstanceID instanceId) {
        checkOpen();
        if (workingSnapshot.get(instanceId) == null) {
            throw new NoSuchGameInstanceException(instanceId);
        }

        workingSnapshot.remove(instanceId);
        stagedManifests.remove(instanceId);
        removedIds.add(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public synchronized void rename(GameInstanceID from, GameInstanceID to) throws IOException {
        checkOpen();
        DefaultGameInstance source = workingSnapshot.get(from);
        if (source == null) {
            throw new NoSuchGameInstanceException(from);
        }
        if (createdIds.contains(from)) {
            throw new IllegalStateException("Cannot rename an instance created by the same draft");
        }
        if (workingSnapshot.get(to) != null) {
            throw new IllegalArgumentException("Target instance already exists: " + to);
        }

        Path targetRoot = getValidatedInstanceRoot(to);
        if (Files.exists(targetRoot)) {
            throw new FileAlreadyExistsException(targetRoot.toString());
        }

        GameInstanceManifest renamedManifest = source.getManifest();
        if (from.equals(renamedManifest.jar())) {
            renamedManifest = renamedManifest.withJar(null);
        }
        renamedManifest = renamedManifest.withId(to);

        workingSnapshot.remove(from);
        stagedManifests.remove(from);
        removedIds.remove(from);
        DefaultGameInstance renamed = repository.createInstance(workingSnapshot, to, renamedManifest);
        workingSnapshot.put(renamed);
        stageManifest(renamedManifest, false);

        for (DefaultGameInstance instance : List.copyOf(workingSnapshot.values())) {
            GameInstanceManifest manifest = instance.getManifest();
            if (from.equals(manifest.inheritsFrom())) {
                stageManifest(manifest.withInheritsFrom(to), false);
            }
        }
        renames.add(new RenameOperation(from, to));
    }

    /// Stages one manifest and updates the working snapshot.
    ///
    /// @param manifest     the manifest to stage
    /// @param claimNewRoot whether a previously absent instance root should become draft-owned
    /// @return the updated working instance
    /// @throws IOException if the root cannot be claimed or the temporary manifest cannot be written
    private DefaultGameInstance stageManifest(
            GameInstanceManifest manifest,
            boolean claimNewRoot) throws IOException {

        GameInstanceID id = manifest.id();
        DefaultGameInstance existing = workingSnapshot.get(id);
        if (claimNewRoot && existing == null && !stagedManifests.containsKey(id)) {
            Path root = getValidatedInstanceRoot(id);
            if (!repository.mayClaimDraftInstanceRoot(id, root)) {
                throw new FileAlreadyExistsException(root.toString(), null,
                        "An unregistered instance directory already exists");
            }
            createdIds.add(id);
            repository.initializeDraftInstanceRoot(id, root);
        }

        StagedManifest previous = stagedManifests.get(id);
        Path targetFile = previous != null ? previous.targetFile() : getManifestTarget(id);
        Path stagedFile = previous != null ? previous.stagedFile() : createStagedManifestPath();
        FileUtils.saveSafely(stagedFile, JsonUtils.GSON.toJson(manifest));
        stagedManifests.put(id, new StagedManifest(stagedFile, targetFile));

        DefaultGameInstance updated;
        if (existing != null) {
            updated = existing.withManifest(workingSnapshot, manifest);
        } else {
            updated = repository.createInstance(workingSnapshot, id, manifest);
        }
        workingSnapshot.put(updated);
        return updated;
    }

    /// {@inheritDoc}
    @Override
    public synchronized DefaultGameRepositorySnapshot commit() throws IOException {
        checkOpen();
        repository.checkActiveDraft(this);
        state = GameRepositoryDraftState.COMMITTING;

        List<AppliedRename> appliedRenames = new ArrayList<>();
        List<RemovedRoot> removedRoots = new ArrayList<>();
        List<AppliedManifest> applied = new ArrayList<>();
        try {
            for (RenameOperation rename : renames) {
                applyRename(rename, appliedRenames);
            }
            for (GameInstanceID id : removedIds) {
                removeInstanceRoot(id, removedRoots);
            }
            for (Map.Entry<GameInstanceID, StagedManifest> entry : stagedManifests.entrySet()) {
                applyManifest(entry.getKey(), entry.getValue(), applied);
            }

            repository.publishDraftSnapshot(this, workingSnapshot);
            state = GameRepositoryDraftState.COMMITTED;
            repository.releaseDraft(this);
            cleanupStagingAfterCommit();
            return workingSnapshot;
        } catch (IOException | RuntimeException e) {
            IOException rollbackFailure = rollbackAppliedManifests(applied);
            rollbackFailure = accumulateNullable(rollbackFailure, rollbackRemovedRoots(removedRoots));
            rollbackFailure = accumulateNullable(rollbackFailure, rollbackRenames(appliedRenames));
            state = GameRepositoryDraftState.FAILED;
            repository.releaseDraft(this);
            IOException cleanupFailure = cleanupOwnedFiles();
            if (rollbackFailure != null) {
                e.addSuppressed(rollbackFailure);
            }
            if (cleanupFailure != null) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    /// {@inheritDoc}
    @Override
    public synchronized void abort() throws IOException {
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

        IOException failure = cleanupOwnedFiles();
        state = failure == null ? GameRepositoryDraftState.ABORTED : GameRepositoryDraftState.FAILED;
        repository.releaseDraft(this);
        if (failure != null) {
            throw failure;
        }
    }

    /// {@inheritDoc}
    @Override
    public synchronized void close() throws IOException {
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
        DefaultGameInstance existing = baseSnapshot.get(id);
        return (existing != null ? existing.getManifestFile() : baseSnapshot.getLayout().getInstanceJson(id))
                .toAbsolutePath()
                .normalize();
    }

    /// Creates a unique path for a staged manifest.
    ///
    /// @return the staged manifest path
    /// @throws IOException if the staging directory cannot be created
    private Path createStagedManifestPath() throws IOException {
        Path manifests = getOrCreateStagingDirectory().resolve("manifests");
        Files.createDirectories(manifests);
        return Files.createTempFile(manifests, "manifest-", ".json");
    }

    /// Returns the draft-private staging directory, creating it when necessary.
    ///
    /// @return the staging directory
    /// @throws IOException if the directory cannot be created
    private Path getOrCreateStagingDirectory() throws IOException {
        Path current = stagingDirectory;
        if (current != null) {
            return current;
        }

        Path parent = baseSnapshot.getLayout().getBaseDirectory()
                .toAbsolutePath()
                .normalize()
                .resolve(".hmcl")
                .resolve("repository-drafts");
        Files.createDirectories(parent);
        stagingDirectory = Files.createTempDirectory(parent, "draft-");
        return stagingDirectory;
    }

    /// Applies one instance directory rename.
    ///
    /// @param rename  the requested rename
    /// @param applied rollback records for completed renames
    /// @throws IOException if the source files cannot be renamed
    private void applyRename(RenameOperation rename, List<AppliedRename> applied) throws IOException {
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
        applied.add(new AppliedRename(rename.from(), rename.to()));
    }

    /// Moves one removed instance root into the draft staging directory.
    ///
    /// @param id      the removed instance id
    /// @param removed rollback records for roots moved out of the repository
    /// @throws IOException if the root cannot be staged
    private void removeInstanceRoot(GameInstanceID id, List<RemovedRoot> removed) throws IOException {
        Path root = getValidatedInstanceRoot(id);
        if (Files.notExists(root)) {
            return;
        }

        Path removals = getOrCreateStagingDirectory().resolve("removed");
        Files.createDirectories(removals);
        Path stagedRoot = Files.createTempDirectory(removals, "instance-");
        Files.delete(stagedRoot);
        moveReplacing(root, stagedRoot);
        removed.add(new RemovedRoot(root, stagedRoot));
    }

    /// Moves one staged manifest into place while retaining a rollback copy.
    ///
    /// @param id      the instance id
    /// @param staged  the staged and target paths
    /// @param applied rollback records for changes already started
    /// @throws IOException if the target cannot be backed up or replaced
    private void applyManifest(
            GameInstanceID id,
            StagedManifest staged,
            List<AppliedManifest> applied) throws IOException {
        Path target = staged.targetFile();
        Path expectedRoot = getValidatedInstanceRoot(id);
        if (target.equals(expectedRoot) || !target.startsWith(expectedRoot)) {
            throw new IOException("Manifest path escapes instance root: " + target);
        }

        Files.createDirectories(target.getParent());
        boolean hadOriginal = Files.exists(target);
        @Nullable Path backup = null;
        if (hadOriginal) {
            Path backups = getOrCreateStagingDirectory().resolve("backups");
            Files.createDirectories(backups);
            backup = Files.createTempFile(backups, "manifest-", ".json");
            Files.delete(backup);
            moveReplacing(target, backup);
        }

        applied.add(new AppliedManifest(target, backup, hadOriginal));
        moveReplacing(staged.stagedFile(), target);
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
                if (manifest.hadOriginal() && manifest.backupFile() != null) {
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
                moveReplacing(root.stagedRoot(), root.originalRoot());
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
    private @Nullable IOException rollbackRenames(List<AppliedRename> applied) {
        @Nullable IOException failure = null;
        List<AppliedRename> reversed = new ArrayList<>(applied);
        Collections.reverse(reversed);
        for (AppliedRename rename : reversed) {
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

    /// Removes draft-owned instance roots and temporary files.
    ///
    /// @return the aggregated cleanup failure, or `null` when cleanup succeeded
    private @Nullable IOException cleanupOwnedFiles() {
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

        Path currentStagingDirectory = stagingDirectory;
        if (currentStagingDirectory != null) {
            try {
                FileUtils.deleteDirectory(currentStagingDirectory);
            } catch (IOException e) {
                failure = accumulate(failure, e);
            }
        }
        return failure;
    }

    /// Removes temporary files after a successful commit without changing its outcome.
    private void cleanupStagingAfterCommit() {
        Path currentStagingDirectory = stagingDirectory;
        if (currentStagingDirectory == null) {
            return;
        }
        try {
            FileUtils.deleteDirectory(currentStagingDirectory);
        } catch (IOException e) {
            LOG.warning("Failed to remove committed draft staging directory " + currentStagingDirectory, e);
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

    /// Records the temporary and permanent paths for one staged manifest.
    ///
    /// @param stagedFile the draft-private serialized manifest
    /// @param targetFile the permanent repository manifest path
    private record StagedManifest(Path stagedFile, Path targetFile) {
    }

    /// Records enough information to roll back one manifest replacement.
    ///
    /// @param targetFile  the permanent manifest path
    /// @param backupFile  the prior manifest backup, or `null` when no prior file existed
    /// @param hadOriginal whether the permanent manifest existed before commit
    private record AppliedManifest(
            Path targetFile,
            @Nullable Path backupFile,
            boolean hadOriginal) {
    }

    /// Records an instance rename requested by the draft.
    ///
    /// @param from the source instance id
    /// @param to   the target instance id
    private record RenameOperation(GameInstanceID from, GameInstanceID to) {
    }

    /// Records an instance rename completed during commit.
    ///
    /// @param from the original instance id
    /// @param to   the renamed instance id
    private record AppliedRename(GameInstanceID from, GameInstanceID to) {
    }

    /// Records an instance root moved into staging during commit.
    ///
    /// @param originalRoot the published instance root
    /// @param stagedRoot   the temporary removal path
    private record RemovedRoot(Path originalRoot, Path stagedRoot) {
    }
}
