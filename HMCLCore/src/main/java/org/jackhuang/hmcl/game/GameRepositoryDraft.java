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

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;

/// Provides the exclusive write session for a game repository.
///
/// A draft owns an unpublished working snapshot initialized from [#getBaseSnapshot()]. Changes made
/// by [#put(GameInstanceManifest)] are immediately visible through [#getSnapshot()] but remain
/// invisible through [GameRepository#getSnapshot()] until [#commit()] succeeds. Manifest JSON is
/// written to draft-private temporary storage and moved into the repository during commit.
///
/// A repository permits at most one open draft. Repository refreshes, layout changes, and other
/// writes are rejected while the draft is open. Aborting a draft removes instance directories that
/// were first created by that draft. Shared library, asset, and download caches are not reverted.
/// Drafts are not thread-safe; callers must serialize all operations on a draft.
///
/// @see GameRepository#openDraft()
@NotNullByDefault
public interface GameRepositoryDraft extends AutoCloseable {

    /// Returns the repository that owns this draft.
    ///
    /// @return the repository
    GameRepository getRepository();

    /// Returns the immutable published snapshot captured when this draft was opened.
    ///
    /// @return the base snapshot
    GameRepositorySnapshot getBaseSnapshot();

    /// Returns the current unpublished working snapshot, or the snapshot published by a successful
    /// [#commit()].
    ///
    /// @return the working or committed snapshot
    /// @throws IllegalStateException if the draft was aborted or failed
    GameRepositorySnapshot getSnapshot();

    /// Returns this draft's lifecycle state.
    ///
    /// @return the current state
    GameRepositoryDraftState getState();

    /// Returns whether this draft still accepts mutations.
    ///
    /// @return whether the draft is open
    boolean isOpen();

    /// Returns whether this draft has been committed.
    ///
    /// @return whether [#commit()] has completed successfully
    boolean isCommitted();

    /// Returns whether the working snapshot contains `instanceId`.
    ///
    /// @param instanceId the instance id
    /// @return whether the id is present in the working snapshot
    /// @throws IllegalStateException if the draft is not open
    boolean hasInstance(GameInstanceID instanceId);

    /// Adds or replaces a stored manifest in the unpublished working snapshot.
    ///
    /// The manifest JSON is written to draft-private temporary storage. The returned [GameInstance]
    /// belongs to the working snapshot and may be used by installation code before commit. It may
    /// become stale after another call to this method for the same id.
    ///
    /// @param manifest the persistent instance manifest
    /// @return the instance in the updated working snapshot
    /// @throws IOException           if the temporary manifest cannot be written
    /// @throws IllegalStateException if the draft is not open
    GameInstance put(GameInstanceManifest manifest) throws IOException;

    /// Removes an instance from the unpublished working snapshot.
    ///
    /// Its instance root is retained until commit and moved out of the repository before the new
    /// snapshot is published. Aborting leaves the published instance and its files unchanged.
    ///
    /// @param instanceId the instance to remove
    /// @throws NoSuchGameInstanceException if the working snapshot does not contain the instance
    /// @throws IllegalStateException       if the draft is not open
    void remove(GameInstanceID instanceId);

    /// Renames an instance in the unpublished working snapshot.
    ///
    /// Direct inheritance references managed by the repository are updated in the same draft. The
    /// source directory remains at its published location until commit.
    ///
    /// @param from the current instance id
    /// @param to   the target instance id
    /// @throws IOException                 if the target directory already exists or a temporary
    ///                                     manifest cannot be written
    /// @throws NoSuchGameInstanceException if the working snapshot does not contain `from`
    /// @throws IllegalArgumentException    if the working snapshot already contains `to`
    /// @throws IllegalStateException       if the draft is not open
    void rename(GameInstanceID from, GameInstanceID to) throws IOException;

    /// Applies staged manifest files and publishes the working snapshot.
    ///
    /// After this method returns, [GameRepository#getInstance(GameInstanceID)] will resolve staged
    /// ids from the published index.
    ///
    /// @return the newly published snapshot
    /// @throws IOException           if staged filesystem changes cannot be applied
    /// @throws IllegalStateException if the draft is not open or is not the repository's active draft
    GameRepositorySnapshot commit() throws IOException;

    /// Discards staged changes without publishing a new snapshot.
    ///
    /// Removes temporary manifests and instance directories created only by this draft. Global
    /// caches (libraries, assets) are not reverted. This method is idempotent after a successful
    /// abort.
    ///
    /// @throws IOException           if cleanup fails
    /// @throws IllegalStateException if the draft was already committed
    void abort() throws IOException;

    /// Aborts this draft when it is still open.
    ///
    /// @see #abort()
    @Override
    void close() throws IOException;
}
