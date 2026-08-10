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

/// A mutable draft of [GameRepository] instance-index state.
///
/// A draft is opened from a repository's published snapshot, accumulates instance creates and
/// manifest updates, then either [#commit()]s once (write-through JSON is already on disk; the
/// index is published) or [#abort()]s (restores previous JSON for edited instances and removes
/// directories created only in this draft).
///
/// Drafts do not roll back global library or asset downloads that install tasks may have written
/// outside instance roots.
///
/// @see GameRepository#openDraft()
@NotNullByDefault
public interface GameRepositoryDraft extends AutoCloseable {

    /// Returns the repository that owns this draft.
    ///
    /// @return the repository
    GameRepository getRepository();

    /// Returns whether this draft still accepts mutations.
    ///
    /// @return whether the draft is open
    boolean isOpen();

    /// Returns whether this draft has been committed.
    ///
    /// @return whether [#commit()] has completed successfully
    boolean isCommitted();

    /// Returns whether the working index contains an instance with the given id.
    ///
    /// @param instanceId the instance id
    /// @return whether the instance exists in this draft
    boolean hasInstance(GameInstanceID instanceId);

    /// Returns the instance as seen in this draft's working snapshot.
    ///
    /// @param instanceId the instance id
    /// @return the working instance
    /// @throws NoSuchGameInstanceException if the instance is absent from this draft
    GameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    /// Stages a stored instance manifest into this draft.
    ///
    /// Writes the manifest JSON under the repository layout and updates the working snapshot so
    /// subsequent [#getInstance(GameInstanceID)] calls observe the new state. The published
    /// repository index is unchanged until [#commit()].
    ///
    /// @param manifest the persistent instance manifest
    /// @return the working instance after the update
    /// @throws IOException          if the manifest cannot be written
    /// @throws IllegalStateException if the draft is closed
    GameInstance put(GameInstanceManifest manifest) throws IOException;

    /// Publishes this draft's working snapshot as the repository's current index.
    ///
    /// Instance JSON files are expected to already match the working state from prior [#put] calls.
    ///
    /// @throws IllegalStateException if the draft is closed or already committed
    void commit();

    /// Discards this draft without publishing.
    ///
    /// Restores JSON for instances that existed before the draft and were modified, and removes
    /// instance directories that were created only in this draft. Global caches (libraries, assets)
    /// are not reverted.
    ///
    /// @throws IllegalStateException if the draft was already committed
    void abort();

    /// Aborts this draft when it was not committed.
    ///
    /// @see #abort()
    @Override
    void close();
}
