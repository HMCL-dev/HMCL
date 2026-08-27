/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameComponentType;
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

    /// Creates a builder for installing a new game instance and optional components.
    ///
    /// The target id must be absent when the builder starts the installation.
    ///
    /// @param instanceId the id of the new instance
    /// @return a new game builder
    GameBuilder newGameBuilder(GameInstanceID instanceId);

    /// Creates a builder for replacing the components of an existing game instance.
    ///
    /// The instance selects update mode and fixes the target repository and id. The currently
    /// published instance with that id is used when the builder starts the update, so the supplied
    /// snapshot-bound object need not remain current. The resulting manifest is rebuilt from the
    /// components configured on the builder; components that are not configured are not retained.
    ///
    /// @param instance the existing game instance to update
    /// @return a game builder targeting the existing instance
    /// @throws IllegalArgumentException if `instance` belongs to another repository
    GameBuilder newGameBuilder(GameInstance instance);

    /// Returns a registered remote-version list.
    ///
    /// @param componentType the component type, such as `game`, `forge`, or `optifine`
    /// @return the registered version list
    /// @throws IllegalArgumentException if no list is registered for `id`
    ComponentVersionList<?> getVersionList(GameComponentType componentType);
}
