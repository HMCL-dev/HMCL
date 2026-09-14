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
import java.nio.file.Path;

/// Provides the exclusive write session for a game repository.
///
/// A draft records an unpublished manifest write set. It does not expose a snapshot or
/// [GameInstance]: callers retain their working [GameInstanceManifest] values while the repository's
/// immutable snapshot remains unchanged until [#commit()] succeeds. Manifest JSON is written only
/// during commit.
///
/// A repository permits at most one open draft. Repository refreshes, layout changes, and other
/// writes are rejected while the draft is open. Draft mutation methods do not materialize new
/// instance directories; [#commit()] creates missing roots as needed. Shared library, asset, and
/// download caches are not reverted.
/// Drafts are not thread-safe; callers must serialize all operations on a draft.
///
/// @see GameRepository#openDraft()
@NotNullByDefault
public interface GameRepositoryDraft extends AutoCloseable {

    /// Describes a draft's lifecycle state.
    @NotNullByDefault
    enum State {
        /// The draft accepts changes and may be committed or aborted.
        OPEN,

        /// The draft is applying files and publishing its immutable successor snapshot.
        COMMITTING,

        /// The draft completed its commit and no longer accepts changes.
        COMMITTED,

        /// The draft discarded its changes and no longer accepts changes.
        ABORTED,

        /// The draft could not complete a commit or cleanup operation.
        FAILED
    }

    /// Returns the repository that owns this draft.
    ///
    /// @return the repository
    GameRepository getRepository();

    GameRepositorySnapshot getBaseSnapshot();

    /// Returns this draft's lifecycle state.
    ///
    /// @return the current state
    State getState();

    /// Returns whether this draft still accepts mutations.
    ///
    /// @return whether the draft is open
    boolean isOpen();

    /// Returns whether this draft has been committed.
    ///
    /// @return whether [#commit()] has completed successfully
    boolean isCommitted();

    /// Adds or replaces a stored manifest in the unpublished draft state.
    ///
    /// The manifest is retained in memory until commit. This operation does not serialize the
    /// manifest or create or expose a [GameInstance]. For a new id, the draft reserves its instance
    /// root but does not create it before commit.
    ///
    /// @param manifest the persistent instance manifest
    /// @throws IOException           if a new instance root cannot be reserved
    /// @throws IllegalStateException if the draft is not open
    void put(GameInstanceManifest manifest) throws IOException;

    /// Records a primary client JAR to copy into an instance when this draft commits.
    ///
    /// The source remains outside the instance tree and is not modified by this draft. It must stay
    /// available and unchanged until commit. No instance directory or target JAR is created by this
    /// operation.
    ///
    /// @param instanceId the instance receiving its own primary JAR
    /// @param source      the completed source JAR
    /// @throws IOException                 if the source is not a regular file or its target escapes
    ///                                     the instance root
    /// @throws NoSuchGameInstanceException if the draft does not contain `instanceId`
    /// @throws IllegalStateException       if the draft is not open
    void putPrimaryJar(GameInstanceID instanceId, Path source) throws IOException;

    /// Removes an instance from the unpublished draft state.
    ///
    /// Its instance root is retained until commit and moved out of the repository before the new
    /// snapshot is published. Aborting leaves the published instance and its files unchanged.
    ///
    /// @param instanceId the instance to remove
    /// @throws NoSuchGameInstanceException if the draft does not contain the instance
    /// @throws IllegalStateException       if the draft is not open
    void remove(GameInstanceID instanceId);

    /// Renames an instance in the unpublished draft state.
    ///
    /// Direct inheritance references managed by the repository are updated in the same draft. The
    /// source directory remains at its published location until commit.
    ///
    /// @param from the current instance id
    /// @param to   the target instance id
    /// @throws IOException                 if the target root is invalid or already exists
    /// @throws NoSuchGameInstanceException if the draft does not contain `from`
    /// @throws IllegalArgumentException    if the draft already contains `to`
    /// @throws IllegalStateException       if the draft is not open
    void rename(GameInstanceID from, GameInstanceID to) throws IOException;

    /// Materializes reserved instance roots, writes recorded primary JARs and modified manifests,
    /// then publishes a new immutable snapshot.
    ///
    /// After this method returns, [GameRepository#getInstance(GameInstanceID)] will resolve modified
    /// ids from the published index.
    ///
    /// @return the newly published snapshot
    /// @throws IOException           if filesystem changes cannot be applied
    /// @throws IllegalStateException if the draft is not open or is not the repository's active draft
    GameRepositorySnapshot commit() throws IOException;

    /// Discards pending changes without publishing a new snapshot.
    ///
    /// Removes files placed under roots reserved by this draft by other installation work. Global
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
