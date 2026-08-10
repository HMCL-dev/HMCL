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

/// A mutable draft of repository instance-index state, held as a working [GameRepositorySnapshot].
///
/// A draft is opened from a repository's published snapshot (typically by cloning it). Mutations
/// such as [#put(GameInstanceManifest)] update that working snapshot. [#commit()] publishes it as
/// the repository's current index; [#abort()] discards it and restores on-disk JSON for instances
/// modified in the draft.
///
/// While the draft is open, [#getSnapshot()] is the draft's working snapshot and may still be
/// mutable. After [#commit()], the same snapshot object is sealed and becomes the repository's
/// published index. After [#abort()] or [#close()] without commit, the working snapshot must not be
/// used.
///
/// Drafts do not roll back global library or asset downloads that install tasks may have written
/// outside instance roots.
///
/// @see GameRepository#openDraft()
/// @see #getSnapshot()
@NotNullByDefault
public interface GameRepositoryDraft extends AutoCloseable {

    /// Returns the repository that owns this draft.
    ///
    /// @return the repository
    GameRepository getRepository();

    /// Returns the working snapshot held by this draft.
    ///
    /// This is the sole instance index mutated by the draft. Instances obtained from it belong to
    /// this snapshot in the same way as for a published [GameRepositorySnapshot].
    ///
    /// @return the working snapshot
    /// @throws IllegalStateException if the draft is closed without having been committed
    GameRepositorySnapshot getSnapshot();

    /// Returns whether this draft still accepts mutations.
    ///
    /// @return whether the draft is open
    boolean isOpen();

    /// Returns whether this draft has been committed.
    ///
    /// @return whether [#commit()] has completed successfully
    boolean isCommitted();

    /// Returns whether the working snapshot contains an instance with the given id.
    ///
    /// @param instanceId the instance id
    /// @return whether the instance exists in [#getSnapshot()]
    default boolean hasInstance(GameInstanceID instanceId) {
        return getSnapshot().hasInstance(instanceId);
    }

    /// Returns the instance as seen in [#getSnapshot()].
    ///
    /// @param instanceId the instance id
    /// @return the working instance
    /// @throws NoSuchGameInstanceException if the instance is absent from this draft
    default GameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getSnapshot().getInstance(instanceId);
    }

    /// Stages a stored instance manifest into this draft's snapshot.
    ///
    /// Writes the manifest JSON under the repository layout and updates [#getSnapshot()] so
    /// subsequent lookups observe the new state. The repository's published index is unchanged
    /// until [#commit()].
    ///
    /// @param manifest the persistent instance manifest
    /// @return the working instance after the update (from [#getSnapshot()])
    /// @throws IOException           if the manifest cannot be written
    /// @throws IllegalStateException if the draft is closed
    GameInstance put(GameInstanceManifest manifest) throws IOException;

    /// Publishes [#getSnapshot()] as the repository's current index.
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
