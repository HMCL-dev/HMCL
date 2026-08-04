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
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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

    /// Resolves inheritance into launch and standalone manifest views.
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

    /// Returns a cached launch-ready manifest view for the instance.
    ///
    /// @param instanceId the instance id
    /// @return the resolved manifest view
    default GameInstanceManifest.Resolved getResolvedInstanceManifest(GameInstanceID instanceId)
            throws NoSuchGameInstanceException {
        return getSnapshot().getInstance(instanceId).getResolvedManifest();
    }

    /// Returns the number of loaded instances.
    ///
    /// @return the loaded instance count
    default int getInstanceCount() {
        return getSnapshot().getInstanceCount();
    }

    /// Returns the stored manifests for all loaded instances.
    ///
    /// @return the loaded instance manifests
    default Collection<GameInstanceManifest> getInstanceManifests() {
        return getSnapshot().getInstanceManifests();
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

    /// Returns the working directory used when launching an instance.
    ///
    /// @param instanceId the instance id
    /// @return the run directory
    Path getRunDirectory(GameInstanceID instanceId);

    /// Returns the directory used for extracted native libraries of an instance and platform.
    ///
    /// @param instanceId the instance id
    /// @param platform   the target platform
    /// @return the native library directory
    default Path getNativeDirectory(GameInstanceID instanceId, Platform platform) {
        return getInstanceRoot(instanceId).resolve("natives-" + platform);
    }

    /// Returns the mods directory for an instance.
    ///
    /// @param instanceId the instance id
    /// @return the mods directory below the run directory
    default Path getModsDirectory(GameInstanceID instanceId) {
        return getRunDirectory(instanceId).resolve("mods");
    }

    /// Returns the resource pack directory for an instance.
    ///
    /// @param instanceId the instance id
    /// @return the resource pack directory below the run directory
    default Path getResourcePackDirectory(GameInstanceID instanceId) {
        return getRunDirectory(instanceId).resolve("resourcepacks");
    }

    /// Returns the primary client jar path for a manifest.
    ///
    /// @param manifest the manifest whose jar should be located
    /// @return the primary client jar path
    Path getInstanceJar(GameInstanceManifest manifest);

    /// Detects the Minecraft game version associated with a manifest.
    ///
    /// @param manifest the manifest to inspect
    /// @return the detected Minecraft game version, or empty if it cannot be determined
    Optional<String> getGameVersion(GameInstanceManifest manifest);

    /// Detects the Minecraft game version associated with an instance.
    ///
    /// @param instanceId the instance id
    /// @return the detected Minecraft game version, or empty if it cannot be determined
    /// @throws NoSuchGameInstanceException if the instance is not loaded in this repository
    default Optional<String> getGameVersion(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getGameVersion(getInstanceManifest(instanceId));
    }

    /// Renames an instance and updates repository-managed references.
    ///
    /// @param from the current instance id
    /// @param to   the target instance id
    /// @return whether the instance was renamed
    boolean renameInstance(GameInstanceID from, GameInstanceID to);

    /// Returns the asset directory that should be used at launch time.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @return the actual asset directory
    Path getActualAssetDirectory(GameInstanceID instanceId, String assetId);

    /// Returns an existing asset object path by logical asset name.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @param name       the logical asset name
    /// @return the asset object path, or empty if the object is not present in the asset index
    /// @throws IOException if the asset index cannot be read
    Optional<Path> getAssetObject(GameInstanceID instanceId, String assetId, String name) throws IOException;

    /// Reads an asset index.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @return the asset index
    /// @throws IOException if the asset index cannot be read
    AssetIndex getAssetIndex(GameInstanceID instanceId, String assetId) throws IOException;

    /// Returns the classpath entries whose library files are present on disk.
    ///
    /// @param manifest the manifest whose libraries should be mapped to classpath entries
    /// @return absolute classpath entries for existing non-native libraries
    default Set<String> getClasspath(GameInstanceManifest manifest) {
        Set<String> classpath = new LinkedHashSet<>();
        if (manifest.libraries() != null) {
            for (Library library : manifest.libraries())
                if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                    Path f = getLayout().getLibraryFile(manifest.id(), library);
                    if (Files.isRegularFile(f))
                        classpath.add(FileUtils.getAbsolutePath(f));
                }
        }

        return classpath;
    }
}
