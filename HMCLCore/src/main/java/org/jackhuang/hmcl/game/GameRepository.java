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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/// Provides indexed access to local game instances and the filesystem layout used by those instances.
///
/// Implementations are responsible for loading instance manifests, resolving inheritance and patches,
/// locating instance-owned files, and exposing helper paths used by launch, download, and maintenance code.
@NotNullByDefault
public interface GameRepository {
    /// Resolves inheritance and pending patches into a launch-ready manifest view.
    ///
    /// @param manifest the manifest to resolve
    /// @return the resolved manifest view
    GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException;

    /// Resolves inheritance while leaving patches as manifest data.
    ///
    /// @param manifest the manifest to resolve
    /// @return the standalone manifest view
    GameInstanceManifest.Standalone resolvePreservingPatches(GameInstanceManifest manifest) throws NoSuchGameInstanceException;

    /// Returns whether the instance exists in the current repository index.
    ///
    /// @param instanceId the instance id
    /// @return whether the instance exists
    boolean hasInstance(GameInstanceID instanceId);

    /// Returns whether the instance exists.
    ///
    /// @param id the instance id string
    /// @return whether the instance exists
    default boolean hasInstance(@Nullable String id) {
        if (id == null) {
            return false;
        }

        try {
            return hasInstance(new GameInstanceID(id));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /// Returns the stored manifest for an instance without resolving inheritance or patches.
    ///
    /// @param instanceId the instance id
    /// @return the stored instance manifest
    /// @throws NoSuchGameInstanceException if the instance is not loaded in this repository
    GameInstanceManifest getInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    /// Returns an instance manifest by string id.
    ///
    /// @param id the instance id string
    /// @return the instance manifest
    default GameInstanceManifest getInstanceManifest(String id) throws NoSuchGameInstanceException {
        return getInstanceManifest(new GameInstanceID(id));
    }

    /// Returns a cached launch-ready manifest view for the instance.
    ///
    /// @param instanceId the instance id
    /// @return the resolved manifest view
    GameInstanceManifest.Resolved getResolvedInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    /// Returns a standalone manifest view for the instance.
    ///
    /// @param instanceId the instance id
    /// @return the standalone manifest view
    default GameInstanceManifest.Standalone getResolvedPreservingPatchesInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return resolvePreservingPatches(getInstanceManifest(instanceId));
    }

    /// Returns a standalone manifest by string id.
    ///
    /// @param id the instance id string
    /// @return the standalone manifest
    default GameInstanceManifest getResolvedPreservingPatchesManifest(GameInstanceID id) throws NoSuchGameInstanceException {
        return getResolvedPreservingPatchesInstanceManifest(id).manifest();
    }

    /// Returns the number of loaded instances.
    ///
    /// @return the loaded instance count
    int getInstanceCount();

    /// Returns the stored manifests for all loaded instances.
    ///
    /// @return the loaded instance manifests
    Collection<GameInstanceManifest> getInstanceManifests();

    /// Reloads repository state from the backing storage.
    void refresh();

    /// Creates a task that reloads repository state from the backing storage.
    ///
    /// @return a task that calls [#refresh()]
    default Task<Void> refreshAsync() {
        return Task.runAsync(this::refresh);
    }

    /// Returns the directory that stores files belonging to an instance.
    ///
    /// @param instanceId the instance id
    /// @return the instance root directory
    Path getInstanceRoot(GameInstanceID instanceId);

    /// Returns the instance root by string id.
    ///
    /// @param id the instance id string
    /// @return the instance root
    default Path getInstanceRoot(String id) {
        return getInstanceRoot(new GameInstanceID(id));
    }

    /// Returns the working directory used when launching an instance.
    ///
    /// @param instanceId the instance id
    /// @return the run directory
    Path getRunDirectory(GameInstanceID instanceId);

    /// Returns the run directory by string id.
    ///
    /// @param id the instance id string
    /// @return the run directory
    default Path getRunDirectory(String id) {
        return getRunDirectory(new GameInstanceID(id));
    }

    /// Returns the base directory used to store shared libraries for a manifest.
    ///
    /// @param manifest the manifest whose libraries are being resolved
    /// @return the libraries directory
    Path getLibrariesDirectory(GameInstanceManifest manifest);

    /// Returns the expected filesystem path for a library.
    ///
    /// @param manifest the manifest that owns or references the library
    /// @param lib      the library descriptor
    /// @return the library file path
    Path getLibraryFile(GameInstanceManifest manifest, Library lib);

    /// Returns the directory used for extracted native libraries of an instance and platform.
    ///
    /// @param instanceId the instance id
    /// @param platform   the target platform
    /// @return the native library directory
    Path getNativeDirectory(GameInstanceID instanceId, Platform platform);

    /// Returns the mods directory for an instance.
    ///
    /// @param instanceId the instance id
    /// @return the mods directory
    Path getModsDirectory(GameInstanceID instanceId);

    /// Returns the resource pack directory for an instance.
    ///
    /// @param instanceId the instance id
    /// @return the resource pack directory
    Path getResourcePackDirectory(GameInstanceID instanceId);

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

    /// Returns the primary client jar path for an instance.
    ///
    /// @param instanceId the instance id
    /// @return the primary client jar path
    /// @throws NoSuchGameInstanceException if the instance is not loaded in this repository
    default Path getInstanceJar(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getInstanceJar(getResolvedInstanceManifest(instanceId).manifest());
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

    /// Returns the base asset storage directory for an instance.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @return the asset storage directory
    Path getAssetDirectory(GameInstanceID instanceId, String assetId);

    /// Returns an existing asset object path by logical asset name.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @param name       the logical asset name
    /// @return the asset object path, or empty if the object is not present in the asset index
    /// @throws IOException if the asset index cannot be read
    Optional<Path> getAssetObject(GameInstanceID instanceId, String assetId, String name) throws IOException;

    /// Returns the expected path for an asset object descriptor.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @param obj        the asset object descriptor
    /// @return the asset object path
    Path getAssetObject(GameInstanceID instanceId, String assetId, AssetObject obj);

    /// Reads an asset index.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @return the asset index
    /// @throws IOException if the asset index cannot be read
    AssetIndex getAssetIndex(GameInstanceID instanceId, String assetId) throws IOException;

    /// Returns the path of an asset index file.
    ///
    /// @param instanceId the instance id
    /// @param assetId    the asset index id
    /// @return the asset index file path
    Path getIndexFile(GameInstanceID instanceId, String assetId);

    /// Returns the path of a logging configuration object.
    ///
    /// @param instanceId  the instance id
    /// @param assetId     the asset index id used as the logging object namespace
    /// @param loggingInfo the logging configuration descriptor
    /// @return the logging object path
    Path getLoggingObject(GameInstanceID instanceId, String assetId, LoggingInfo loggingInfo);

    /// Returns the classpath entries whose library files are present on disk.
    ///
    /// @param manifest the manifest whose libraries should be mapped to classpath entries
    /// @return absolute classpath entries for existing non-native libraries
    default Set<String> getClasspath(GameInstanceManifest manifest) {
        Set<String> classpath = new LinkedHashSet<>();
        if (manifest.libraries() != null) {
            for (Library library : manifest.libraries())
                if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                    Path f = getLibraryFile(manifest, library);
                    if (Files.isRegularFile(f))
                        classpath.add(FileUtils.getAbsolutePath(f));
                }
        }

        return classpath;
    }

}
