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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default exclusive [GameRepositoryDraft] implementation.
///
/// Manifest changes are retained in memory until commit. A successful commit writes the final
/// manifests and primary JARs, applies removals and renames, and publishes one new immutable
/// snapshot. Missing new-instance roots are created while committing. Shared library and asset
/// cache writes are outside the rollback boundary. Instances of this class are not thread-safe.
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

    /// Completed primary JAR sources to copy into instance roots during commit.
    private final Map<GameInstanceID, Path> primaryJarSources = new TreeMap<>();

    /// Instance ids whose root directories were absent before this draft first added them.
    private final Set<GameInstanceID> createdIds = new TreeSet<>();

    /// Instance ids absent from the final manifest set.
    private final Set<GameInstanceID> removedIds = new TreeSet<>();

    /// Ordered instance renames applied to the filesystem during commit.
    private final List<RenameOperation> renames = new ArrayList<>();

    /// Current lifecycle state.
    private GameRepositoryDraft.State state = GameRepositoryDraft.State.OPEN;

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

    @Override
    public DefaultGameRepositorySnapshot getBaseSnapshot() {
        return baseSnapshot;
    }

    /// {@inheritDoc}
    @Override
    public GameRepositoryDraft.State getState() {
        return state;
    }

    /// {@inheritDoc}
    @Override
    public boolean isOpen() {
        return state == GameRepositoryDraft.State.OPEN;
    }

    /// {@inheritDoc}
    @Override
    public boolean isCommitted() {
        return state == GameRepositoryDraft.State.COMMITTED;
    }

    /// {@inheritDoc}
    @Override
    public void put(GameInstanceManifest manifest) throws IOException {
        checkOpen();
        putManifest(manifest, true);
    }

    /// {@inheritDoc}
    @Override
    public void putPrimaryJar(GameInstanceID instanceId, Path source) throws IOException {
        checkOpen();
        if (!manifests.containsKey(instanceId)) {
            throw new NoSuchGameInstanceException(instanceId);
        }

        Path normalizedSource = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedSource)) {
            throw new IOException("Primary JAR source is not a regular file: " + normalizedSource);
        }

        Path target = getPrimaryJarTarget(instanceId);
        if (normalizedSource.equals(target)) {
            primaryJarSources.remove(instanceId);
        } else {
            primaryJarSources.put(instanceId, normalizedSource);
        }
    }

    /// {@inheritDoc}
    @Override
    public void remove(GameInstanceID instanceId) {
        checkOpen();
        if (manifests.remove(instanceId) == null) {
            throw new NoSuchGameInstanceException(instanceId);
        }

        modifiedIds.remove(instanceId);
        primaryJarSources.remove(instanceId);
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

        Path targetRoot = baseSnapshot.getLayout().getInstanceRoot(to);
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

        @Nullable Path primaryJarSource = primaryJarSources.remove(from);
        if (primaryJarSource != null) {
            primaryJarSources.put(to, primaryJarSource);
        }

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
    /// @throws IOException if a new instance root cannot be reserved
    private void putManifest(
            GameInstanceManifest manifest,
            boolean claimNewRoot) throws IOException {

        GameInstanceID id = manifest.id();
        if (claimNewRoot
                && !manifests.containsKey(id)
                && baseSnapshot.get(id) == null
                && !createdIds.contains(id)) {
            Path root = baseSnapshot.getLayout().getInstanceRoot(id)
                    .toAbsolutePath()
                    .normalize();
            if (!Files.notExists(root)) {
                throw new FileAlreadyExistsException(root.toString(), null,
                        "An unregistered instance directory already exists");
            }
            createdIds.add(id);
        }

        manifests.put(id, manifest);
        removedIds.remove(id);
        modifiedIds.add(id);
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepositorySnapshot commit() throws IOException {
        checkOpen();
        repository.checkActiveDraft(this);
        state = GameRepositoryDraft.State.COMMITTING;

        List<RenameOperation> appliedRenames = new ArrayList<>();
        List<RemovedRoot> removedRoots = new ArrayList<>();
        List<AppliedFile> appliedFiles = new ArrayList<>();
        @Nullable Path rollbackDirectory = null;
        try {
            DefaultGameRepositorySnapshot committedSnapshot = buildCommittedSnapshot();
            if (!renames.isEmpty() || !removedIds.isEmpty()) {
                repository.flushPendingInstanceWrites();
            }
            for (RenameOperation rename : renames) {
                applyRename(rename, appliedRenames);
            }
            materializeCreatedInstanceRoots();

            if (!removedIds.isEmpty() || !modifiedIds.isEmpty() || !primaryJarSources.isEmpty()) {
                Path currentRollbackDirectory = createRollbackDirectory();
                rollbackDirectory = currentRollbackDirectory;
                for (GameInstanceID id : removedIds) {
                    removeInstanceRoot(id, currentRollbackDirectory, removedRoots);
                }
                for (Map.Entry<GameInstanceID, Path> entry : primaryJarSources.entrySet()) {
                    applyPrimaryJar(
                            entry.getKey(),
                            entry.getValue(),
                            currentRollbackDirectory,
                            appliedFiles);
                }
                for (GameInstanceID id : modifiedIds) {
                    @Nullable GameInstanceManifest manifest = manifests.get(id);
                    if (manifest == null) {
                        throw new IllegalStateException("Modified manifest is missing: " + id);
                    }
                    applyManifest(id, manifest, currentRollbackDirectory, appliedFiles);
                }
            }

            repository.publishDraftSnapshot(this, committedSnapshot);
            state = GameRepositoryDraft.State.COMMITTED;
            repository.releaseDraft(this);
            cleanupRollbackDirectoryAfterCommit(rollbackDirectory);
            return committedSnapshot;
        } catch (IOException | RuntimeException e) {
            // rollback applied files
            List<AppliedFile> reversedAppliedFiles = new ArrayList<>(appliedFiles);
            Collections.reverse(reversedAppliedFiles);
            for (AppliedFile file : reversedAppliedFiles) {
                try {
                    Files.deleteIfExists(file.targetFile());
                    if (file.backupFile() != null) {
                        moveReplacing(file.backupFile(), file.targetFile());
                    }
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                }
            }

            // rollback removed roots
            List<RemovedRoot> reversedRemovedRoots = new ArrayList<>(removedRoots);
            Collections.reverse(reversedRemovedRoots);
            for (RemovedRoot root : reversedRemovedRoots) {
                try {
                    moveReplacing(root.rollbackRoot(), root.originalRoot());
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                }
            }

            // rollback renames
            List<RenameOperation> reversed = new ArrayList<>(appliedRenames);
            Collections.reverse(reversed);
            for (RenameOperation rename : reversed) {
                try {
                    DefaultGameRepository.moveInstanceFiles(
                            baseSnapshot.getLayout().getBaseDirectory(),
                            rename.to(),
                            rename.from());
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                }
            }

            state = GameRepositoryDraft.State.FAILED;
            repository.releaseDraft(this);

            try {
                cleanupCreatedInstanceRoots();
            } catch (Exception e1) {
                e.addSuppressed(e1);
            }

            // cleanup rollback directory
            if (rollbackDirectory != null) {
                try {
                    FileUtils.deleteDirectory(rollbackDirectory);
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                }
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
        committedSnapshot.removeInvalidInstances();
        committedSnapshot.seal();
        return committedSnapshot;
    }

    /// Creates roots reserved for instances added by this draft.
    ///
    /// @throws IOException if a root cannot be created
    private void materializeCreatedInstanceRoots() throws IOException {
        for (GameInstanceID id : createdIds) {
            if (!manifests.containsKey(id)) {
                continue;
            }
            Path root = baseSnapshot.getLayout().getInstanceRoot(id)
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(root);
        }
    }

    /// {@inheritDoc}
    @Override
    public void abort() throws IOException {
        if (state == GameRepositoryDraft.State.ABORTED) {
            return;
        }
        if (state == GameRepositoryDraft.State.COMMITTED) {
            throw new IllegalStateException("Draft is already committed");
        }
        if (state == GameRepositoryDraft.State.COMMITTING) {
            throw new IllegalStateException("Draft is committing");
        }
        if (state == GameRepositoryDraft.State.FAILED) {
            return;
        }

        try {
            cleanupCreatedInstanceRoots();
            state = GameRepositoryDraft.State.ABORTED;
        } catch (Exception e) {
            state = GameRepositoryDraft.State.FAILED;
            throw e;
        } finally {
            repository.releaseDraft(this);
        }
    }

    /// {@inheritDoc}
    @Override
    public void close() throws IOException {
        if (state == GameRepositoryDraft.State.OPEN) {
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

    /// Returns and validates the permanent target for an instance's own primary JAR.
    ///
    /// Existing instances retain a non-conventional JAR path discovered during refresh. New and
    /// renamed instances use the conventional path from the repository layout.
    ///
    /// @param id the instance id
    /// @return the normalized primary JAR target
    /// @throws IOException if the target escapes the instance root
    private Path getPrimaryJarTarget(GameInstanceID id) throws IOException {
        @Nullable DefaultGameInstance existing = baseSnapshot.get(id);
        Path target = (existing != null
                ? existing.getOwnJarFile()
                : baseSnapshot.getLayout().getInstanceJarFile(id))
                .toAbsolutePath()
                .normalize();
        validateInstanceFileTarget(id, target, "Primary JAR");
        return target;
    }

    /// Verifies that a file target is a strict descendant of its instance root.
    ///
    /// @param id          the owning instance id
    /// @param target      the normalized target path
    /// @param description description used in an exception message
    /// @throws IOException if the target is outside the instance root
    private void validateInstanceFileTarget(
            GameInstanceID id,
            Path target,
            String description) throws IOException {
        Path expectedRoot = baseSnapshot.getLayout().getInstanceRoot(id)
                .toAbsolutePath()
                .normalize();
        if (target.equals(expectedRoot) || !target.startsWith(expectedRoot)) {
            throw new IOException(description + " path escapes instance root: " + target);
        }
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
        Path sourceRoot = baseSnapshot.getLayout().getInstanceRoot(rename.from());
        Path targetRoot = baseSnapshot.getLayout().getInstanceRoot(rename.to());
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
        Path root = baseSnapshot.getLayout().getInstanceRoot(id);
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
            List<AppliedFile> applied) throws IOException {
        String json = JsonUtils.GSON.toJson(manifest);
        Path target = getManifestTarget(id);
        validateInstanceFileTarget(id, target, "Manifest");

        Files.createDirectories(target.getParent());
        @Nullable Path backup = backupFile(target, rollbackDirectory, "manifest-", ".json");
        applied.add(new AppliedFile(target, backup));
        Files.writeString(target, json);
    }

    /// Copies a completed primary JAR into its permanent instance location while retaining a
    /// rollback copy of an existing target.
    ///
    /// @param id                the instance receiving the JAR
    /// @param source            the completed source JAR
    /// @param rollbackDirectory the directory holding rollback files
    /// @param applied           rollback records for files already changed
    /// @throws IOException if the source or target cannot be read or written
    private void applyPrimaryJar(
            GameInstanceID id,
            Path source,
            Path rollbackDirectory,
            List<AppliedFile> applied) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Primary JAR source is not a regular file: " + source);
        }

        Path target = getPrimaryJarTarget(id);
        Files.createDirectories(target.getParent());
        @Nullable Path backup = backupFile(target, rollbackDirectory, "jar-", ".jar");
        applied.add(new AppliedFile(target, backup));
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /// Moves an existing target into rollback storage.
    ///
    /// @param target            the file about to be replaced
    /// @param rollbackDirectory the directory holding rollback files
    /// @param prefix            the backup file prefix
    /// @param suffix            the backup file suffix
    /// @return the backup path, or `null` when the target did not exist
    /// @throws IOException if the target cannot be backed up
    private static @Nullable Path backupFile(
            Path target,
            Path rollbackDirectory,
            String prefix,
            String suffix) throws IOException {
        if (Files.notExists(target)) {
            return null;
        }

        Path backups = rollbackDirectory.resolve("backups");
        Files.createDirectories(backups);
        Path backup = Files.createTempFile(backups, prefix, suffix);
        Files.delete(backup);
        moveReplacing(target, backup);
        return backup;
    }

    /// Removes instance roots first created by this draft.
    private void cleanupCreatedInstanceRoots() throws IOException {
        ArrayList<IOException> exceptions = null;
        for (GameInstanceID id : createdIds) {
            try {
                Path root = baseSnapshot.getLayout().getInstanceRoot(id);
                if (Files.exists(root)) {
                    FileUtils.deleteDirectory(root);
                }
            } catch (IOException | RuntimeException e) {
                if (exceptions == null)
                    exceptions = new ArrayList<>(1);

                IOException cleanupException = e instanceof IOException ioException
                        ? ioException
                        : new IOException("Failed to remove draft-created instance " + id, e);

                exceptions.add(cleanupException);
            }
        }

        if (exceptions != null) {
            if (exceptions.size() == 1) {
                throw exceptions.get(0);
            } else {
                IOException aggregate = new IOException("Failed to remove one or more draft-created instances");
                for (IOException e : exceptions) {
                    aggregate.addSuppressed(e);
                }
                throw aggregate;
            }
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

    /// Ensures the draft accepts changes.
    ///
    /// @throws IllegalStateException if the draft is not open
    private void checkOpen() {
        if (state != GameRepositoryDraft.State.OPEN) {
            throw new IllegalStateException("Draft is " + state.name().toLowerCase(Locale.ROOT));
        }
    }

    /// Records enough information to roll back one file replacement.
    ///
    /// @param targetFile the permanent file path
    /// @param backupFile the prior file backup, or `null` when no prior file existed
    private record AppliedFile(
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
