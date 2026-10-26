// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/// An immutable snapshot of a [GameRepository] instance index.
///
/// A snapshot is published as a complete value. After publication it is never mutated: repository
/// writers replace the current snapshot rather than editing a live map. Callers that need a stable
/// view across multiple lookups should retain the snapshot returned by
/// [GameRepository#getSnapshot()] instead of repeatedly querying the repository.
///
/// [GameInstance] values obtained from a snapshot belong to that snapshot. After the repository
/// publishes a newer snapshot, previously obtained instances may be stale; request them again from
/// the current snapshot or repository when up-to-date state is required.
///
/// Snapshot queries describe the instances indexed at publish time.
@NotNullByDefault
public interface GameRepositorySnapshot {
    /// Returns the repository that published this snapshot.
    ///
    /// @return the owning repository
    GameRepository getRepository();

    /// Returns the filesystem layout associated with this snapshot.
    ///
    /// @return the repository layout
    GameRepositoryLayout getLayout();

    /// Returns whether a registered instance with the given id exists in this snapshot.
    ///
    /// @param instanceId the instance id
    /// @return whether the instance is registered
    boolean hasInstance(GameInstanceID instanceId);

    /// Returns the registered instance with the given id.
    ///
    /// @param instanceId the instance id
    /// @return the instance
    /// @throws NoSuchGameInstanceException if the instance is not registered in this snapshot
    GameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    /// Returns the registered instance with the given id, or `null` when absent.
    ///
    /// @param instanceId the instance id
    /// @return the instance, or `null` when not registered
    @Nullable GameInstance findInstance(GameInstanceID instanceId);

    /// Returns the number of registered instances in this snapshot.
    ///
    /// @return the registered instance count
    int getInstanceCount();

    /// Returns the registered instances in this snapshot.
    ///
    /// The returned collection is unmodifiable and reflects only this snapshot.
    ///
    /// @return the registered instances
    Collection<? extends GameInstance> getInstances();

    /// Returns the stored manifests of all registered instances in this snapshot.
    ///
    /// @return the registered instance manifests
    Collection<GameInstanceManifest> getInstanceManifests();
}
