// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;

/// Provides repository-scoped services for downloading and installing game components.
public interface DependencyManager {

    /// Returns the game repository used for path resolution and instance updates.
    ///
    /// @return the associated game repository
    GameRepository getGameRepository();

    /// Returns the cache repository used by downloads.
    ///
    /// @return the associated cache repository
    CacheRepository getCacheRepository();

    /// Creates a task that completes the files required to launch an instance.
    ///
    /// The instance fixes snapshot-bound identity and storage paths. `manifest` is the effective
    /// launch manifest and may differ from [GameInstance#getManifest()] after launch-time
    /// maintenance or patching. The instance must belong to [#getGameRepository()].
    ///
    /// @param instance       the fixed registered instance being prepared
    /// @param manifest       the effective launch manifest to inspect
    /// @param integrityCheck whether existing files must be verified
    /// @return the completion task
    /// @throws IllegalArgumentException if `instance` belongs to another repository
    Task<?> checkGameCompletionAsync(GameInstance instance, GameInstanceManifest manifest, boolean integrityCheck);

    /// Creates a task that completes the libraries declared by a manifest.
    ///
    /// @param manifest       the manifest whose libraries are checked
    /// @param integrityCheck whether existing libraries must be verified
    /// @return the library-completion task
    Task<?> checkComponentCompletionAsync(GameInstanceManifest manifest, boolean integrityCheck);

    /// Creates a task that repairs installable patches required by an instance.
    ///
    /// The stored and resolved manifests used to identify installed patches are read from
    /// `instance`; `manifest` supplies the effective launch-time library set. The instance must
    /// belong to [#getGameRepository()].
    ///
    /// @param instance       the fixed registered instance being prepared
    /// @param manifest       the effective launch manifest to inspect
    /// @param integrityCheck whether existing patch libraries must be verified
    /// @return the patch-completion task
    /// @throws IllegalArgumentException if `instance` belongs to another repository
    Task<?> checkPatchCompletionAsync(GameInstance instance, GameInstanceManifest manifest, boolean integrityCheck);

    /// Reserves the id of a new instance in an exclusive repository draft.
    ///
    /// @throws IllegalStateException if the id is already registered, its root cannot be reserved,
    ///                               or another repository draft is open
    GameBuilder newGameBuilder(GameInstanceID instanceId);

    /// Opens an exclusive draft to replace the instance's components with the builder's selections.
    ///
    /// @throws IllegalArgumentException if `instance` belongs to another repository
    /// @throws IllegalStateException    if `instance` is no longer the exact published instance or
    ///                                  another repository draft is open
    GameBuilder newGameBuilder(GameInstance instance);

}
