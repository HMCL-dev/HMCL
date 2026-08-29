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
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

/// Provides indexed access to local game instances and the filesystem layout used by those instances.
///
/// The registered instance index is published as immutable [GameRepositorySnapshot] values. Readers
/// that need a consistent view across multiple lookups should retain [#getSnapshot()] rather than
/// interleaving queries with repository writes such as [#refresh()].
///
/// Implementations are responsible for loading instance manifests, resolving inheritance and patches,
/// locating instance-owned files, and exposing helper paths used by launch, download, and maintenance code.
///
/// Path helpers that only forward to [GameRepositoryLayout] describe concepts shared by multiple
/// repository layouts (official and MultiMC-family layouts alike). Layout-specific storage details
/// remain on concrete layout types such as [DefaultGameRepositoryLayout].
@NotNullByDefault
public interface GameRepository {
    /// Returns the filesystem layout used by this repository.
    ///
    /// @return the repository layout
    GameRepositoryLayout getLayout();

    /// Returns the repository base directory.
    ///
    /// @return the base directory from [#getLayout()]
    default Path getBaseDirectory() {
        return getLayout().getBaseDirectory();
    }

    /// Returns the current published snapshot of the registered instance index.
    ///
    /// The snapshot is immutable. Subsequent repository writes publish a replacement snapshot and
    /// do not mutate the returned object.
    ///
    /// @return the current repository snapshot
    GameRepositorySnapshot getSnapshot();

    /// Opens a draft for staging instance creates and manifest updates before a single publish.
    ///
    /// A repository permits at most one open draft. Repository refreshes, layout changes, and other
    /// writes are rejected until the draft is committed, aborted, or closed.
    ///
    /// @return a new open draft based on the current published state
    /// @throws IllegalStateException if this repository is already being modified
    /// @see GameRepositoryDraft
    GameRepositoryDraft openDraft();

    /// Resolves inheritance into a normalized launch view and a patch-preserving standalone view.
    ///
    /// @param manifest the manifest to resolve
    /// @return the resolved manifest view
    GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException;

    /// Returns whether the instance exists in the current repository index.
    ///
    /// @param instanceId the instance id
    /// @return whether the instance exists
    default boolean hasInstance(GameInstanceID instanceId) {
        return getSnapshot().hasInstance(instanceId);
    }

    /// Returns the stored manifest for an instance without resolving inheritance or patches.
    ///
    /// @param instanceId the instance id
    /// @return the stored instance manifest
    /// @throws NoSuchGameInstanceException if the instance is not loaded in this repository
    default GameInstanceManifest getInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getSnapshot().getInstance(instanceId).getManifest();
    }

    /// Returns the number of loaded instances.
    ///
    /// @return the loaded instance count
    default int getInstanceCount() {
        return getSnapshot().getInstanceCount();
    }

    /// Returns the indexed game instance for the given id.
    ///
    /// @param id the instance id
    /// @return the game instance
    /// @throws NoSuchGameInstanceException if the instance is not loaded in this repository
    default GameInstance getInstance(GameInstanceID id) throws NoSuchGameInstanceException {
        return getSnapshot().getInstance(id);
    }

    /// Reloads repository state from the backing storage.
    ///
    /// @throws IllegalStateException if this repository is already being modified
    void refresh();

    /// Creates a task that reloads repository state from the backing storage.
    ///
    /// @return a task that calls [#refresh()]
    default Task<Void> refreshAsync() {
        return Task.runAsync(this::refresh);
    }

    /// Returns the directory containing the files owned by an instance.
    ///
    /// @param instanceId the instance id
    /// @return the instance root directory
    default Path getInstanceRoot(GameInstanceID instanceId) {
        return getLayout().getInstanceRoot(instanceId);
    }

    /// Renames an instance and updates repository-managed references.
    ///
    /// @param from the current instance id
    /// @param to   the target instance id
    /// @return whether the instance was renamed
    boolean renameInstance(GameInstanceID from, GameInstanceID to);

}
