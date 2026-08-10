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

/// A write session over a [GameRepository] that stages instance-index changes without mutating
/// snapshots before publish.
///
/// The draft holds the repository's published [GameRepositorySnapshot] at open time as an immutable
/// base. [#put(GameInstanceManifest)] records staged manifests and writes instance JSON; it does
/// not modify that base snapshot. [#commit()] builds a new snapshot from the base plus staged
/// changes and publishes it once. [#abort()] discards staged changes, restores JSON for edited
/// instances, and removes directories created only in this draft.
///
/// [#getSnapshot()] returns the immutable base while the draft is open. Staged state is visible
/// through [#getInstance(GameInstanceID)] and [#hasInstance(GameInstanceID)], which overlay the
/// base. After a successful [#commit()], [#getSnapshot()] returns the newly published snapshot.
///
/// Drafts do not roll back global library or asset downloads outside instance roots.
///
/// @see GameRepository#openDraft()
@NotNullByDefault
public interface GameRepositoryDraft extends AutoCloseable {

    /// Returns the repository that owns this draft.
    ///
    /// @return the repository
    GameRepository getRepository();

    /// Returns the immutable base snapshot captured when this draft was opened, or the published
    /// snapshot after a successful [#commit()].
    ///
    /// While the draft is open, this is not a mutable working copy: staged [#put] results are not
    /// reflected here. Use [#getInstance(GameInstanceID)] for the draft's effective instance view.
    ///
    /// @return the base snapshot, or the committed published snapshot
    /// @throws IllegalStateException if the draft was aborted or closed without commit
    GameRepositorySnapshot getSnapshot();

    /// Returns whether this draft still accepts mutations.
    ///
    /// @return whether the draft is open
    boolean isOpen();

    /// Returns whether this draft has been committed.
    ///
    /// @return whether [#commit()] has completed successfully
    boolean isCommitted();

    /// Returns whether the draft's effective index contains an instance with the given id.
    ///
    /// An id is present if it is staged by [#put] or present in the base snapshot and not removed
    /// by this draft.
    ///
    /// @param instanceId the instance id
    /// @return whether the instance exists in the draft view
    boolean hasInstance(GameInstanceID instanceId);

    /// Returns the instance as seen by this draft (staged manifest over the base snapshot).
    ///
    /// @param instanceId the instance id
    /// @return the effective instance for this draft
    /// @throws NoSuchGameInstanceException if the instance is absent from the draft view
    GameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    /// Stages a stored instance manifest without modifying the base snapshot.
    ///
    /// Writes the manifest JSON under the repository layout and records the change for
    /// [#commit()]. The repository's published index is unchanged until commit.
    ///
    /// @param manifest the persistent instance manifest
    /// @return an instance view reflecting `manifest` in this draft
    /// @throws IOException            if the manifest cannot be written
    /// @throws IllegalStateException  if the draft is closed
    GameInstance put(GameInstanceManifest manifest) throws IOException;

    /// Builds a new snapshot from the base plus staged changes and publishes it.
    ///
    /// @throws IllegalStateException if the draft is closed or already committed
    void commit();

    /// Discards staged changes without publishing a new snapshot.
    ///
    /// Restores JSON for instances that existed in the base and were modified, and removes
    /// instance directories that were created only in this draft. Global caches (libraries, assets)
    /// are not reverted. Idempotent when already aborted.
    ///
    /// @throws IllegalStateException if the draft was already committed
    void abort();

    /// Aborts this draft when it was not committed.
    ///
    /// @see #abort()
    @Override
    void close();
}
