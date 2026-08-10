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

/// A write session that stages stored instance manifests against an immutable base snapshot.
///
/// The draft holds the repository's published [GameRepositorySnapshot] at open time. That snapshot
/// is never modified. [#put(GameInstanceManifest)] only records a staged stored manifest and writes
/// its JSON. [#commit()] builds a new snapshot from the base plus staged manifests and publishes it
/// once; only then does the repository index contain the corresponding [GameInstance] values.
/// [#abort()] discards staged changes, restores JSON for edited base instances, and removes
/// directories created only in this draft.
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

    /// Returns the immutable base snapshot captured when this draft was opened, or the snapshot
    /// published by a successful [#commit()].
    ///
    /// While the draft is open, staged [#put] results are not part of this snapshot. After commit,
    /// this method returns the newly published index.
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

    /// Returns whether a stored manifest for `instanceId` is staged or already present in the base
    /// snapshot.
    ///
    /// This does not imply a [GameInstance] is available from the published repository until
    /// [#commit()].
    ///
    /// @param instanceId the instance id
    /// @return whether the id is staged or present in the base snapshot
    boolean hasInstance(GameInstanceID instanceId);

    /// Stages a stored instance manifest without creating a repository [GameInstance].
    ///
    /// Writes the manifest JSON under the repository layout and records the change for
    /// [#commit()]. No instance index entry exists for a newly staged id until commit. Callers that
    /// need a [GameInstance] must [#commit()] and then use [GameRepository#getInstance(GameInstanceID)].
    ///
    /// @param manifest the persistent instance manifest
    /// @throws IOException           if the manifest cannot be written
    /// @throws IllegalStateException if the draft is closed
    void put(GameInstanceManifest manifest) throws IOException;

    /// Builds a new snapshot from the base plus staged manifests and publishes it.
    ///
    /// After this method returns, [GameRepository#getInstance(GameInstanceID)] will resolve staged
    /// ids from the published index.
    ///
    /// @throws IllegalStateException if the draft is closed or already committed
    void commit();

    /// Discards staged changes without publishing a new snapshot.
    ///
    /// Restores JSON for instances that existed in the base and were modified, and removes instance
    /// directories that were created only in this draft. Global caches (libraries, assets) are not
    /// reverted. Idempotent when already aborted.
    ///
    /// @throws IllegalStateException if the draft was already committed
    void abort();

    /// Aborts this draft when it was not committed.
    ///
    /// @see #abort()
    @Override
    void close();
}
