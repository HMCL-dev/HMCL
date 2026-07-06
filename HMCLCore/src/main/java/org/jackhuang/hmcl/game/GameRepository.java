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

    /// Returns a resolved instance manifest by string id.
    ///
    /// @param id the instance id string
    /// @return the resolved manifest
    default GameInstanceManifest getResolvedInstanceManifest(String id) throws NoSuchGameInstanceException {
        return getResolvedInstanceManifest(new GameInstanceID(id)).manifest();
    }

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
    default GameInstanceManifest getResolvedPreservingPatchesManifest(String id) throws NoSuchGameInstanceException {
        return getResolvedPreservingPatchesInstanceManifest(new GameInstanceID(id)).manifest();
    }

    int getInstanceCount();

    Collection<GameInstanceManifest> getInstanceManifests();

    void refresh();

    default Task<Void> refreshAsync() {
        return Task.runAsync(this::refresh);
    }

    Path getInstanceRoot(GameInstanceID instanceId);

    /// Returns the instance root by string id.
    ///
    /// @param id the instance id string
    /// @return the instance root
    default Path getInstanceRoot(String id) {
        return getInstanceRoot(new GameInstanceID(id));
    }

    Path getRunDirectory(GameInstanceID instanceId);

    /// Returns the run directory by string id.
    ///
    /// @param id the instance id string
    /// @return the run directory
    default Path getRunDirectory(String id) {
        return getRunDirectory(new GameInstanceID(id));
    }

    Path getLibrariesDirectory(GameInstanceManifest manifest);

    Path getLibraryFile(GameInstanceManifest manifest, Library lib);

    Path getNativeDirectory(GameInstanceID instanceId, Platform platform);

    /// Returns the native directory by string id.
    default Path getNativeDirectory(String id, Platform platform) {
        return getNativeDirectory(new GameInstanceID(id), platform);
    }

    Path getModsDirectory(GameInstanceID instanceId);

    /// Returns the mods directory by string id.
    default Path getModsDirectory(String id) {
        return getModsDirectory(new GameInstanceID(id));
    }

    Path getResourcePackDirectory(GameInstanceID instanceId);

    /// Returns the resource pack directory by string id.
    default Path getResourcePackDirectory(String id) {
        return getResourcePackDirectory(new GameInstanceID(id));
    }

    Path getInstanceJar(GameInstanceManifest manifest);

    /// Returns the instance jar path by string id.
    default Path getInstanceJar(String id) {
        try {
            return getInstanceJar(new GameInstanceID(id));
        } catch (NoSuchGameInstanceException e) {
            throw new IllegalArgumentException(e);
        }
    }

    Optional<String> getGameVersion(GameInstanceManifest manifest);

    default Optional<String> getGameVersion(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getGameVersion(getInstanceManifest(instanceId));
    }

    /// Returns the detected game version by string id.
    default Optional<String> getGameVersion(String id) {
        try {
            return getGameVersion(new GameInstanceID(id));
        } catch (NoSuchGameInstanceException e) {
            return Optional.empty();
        }
    }

    default Path getInstanceJar(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getInstanceJar(getResolvedInstanceManifest(instanceId).manifest());
    }

    boolean renameInstance(GameInstanceID from, GameInstanceID to);

    /// Renames an instance by string id.
    default boolean renameInstance(String from, String to) {
        return renameInstance(new GameInstanceID(from), new GameInstanceID(to));
    }

    Path getActualAssetDirectory(GameInstanceID instanceId, String assetId);

    /// Returns the actual asset directory by string id.
    default Path getActualAssetDirectory(String id, String assetId) {
        return getActualAssetDirectory(new GameInstanceID(id), assetId);
    }

    Path getAssetDirectory(GameInstanceID instanceId, String assetId);

    /// Returns the asset directory by string id.
    default Path getAssetDirectory(String id, String assetId) {
        return getAssetDirectory(new GameInstanceID(id), assetId);
    }

    Optional<Path> getAssetObject(GameInstanceID instanceId, String assetId, String name) throws IOException;

    /// Returns an asset object by string id.
    default Optional<Path> getAssetObject(String id, String assetId, String name) throws IOException {
        return getAssetObject(new GameInstanceID(id), assetId, name);
    }

    Path getAssetObject(GameInstanceID instanceId, String assetId, AssetObject obj);

    /// Returns an asset object by string id.
    default Path getAssetObject(String id, String assetId, AssetObject obj) {
        return getAssetObject(new GameInstanceID(id), assetId, obj);
    }

    AssetIndex getAssetIndex(GameInstanceID instanceId, String assetId) throws IOException;

    /// Returns an asset index by string id.
    default AssetIndex getAssetIndex(String id, String assetId) throws IOException {
        return getAssetIndex(new GameInstanceID(id), assetId);
    }

    Path getIndexFile(GameInstanceID instanceId, String assetId);

    /// Returns an asset index path by string id.
    default Path getIndexFile(String id, String assetId) {
        return getIndexFile(new GameInstanceID(id), assetId);
    }

    Path getLoggingObject(GameInstanceID instanceId, String assetId, LoggingInfo loggingInfo);

    /// Returns a logging object path by string id.
    default Path getLoggingObject(String id, String assetId, LoggingInfo loggingInfo) {
        return getLoggingObject(new GameInstanceID(id), assetId, loggingInfo);
    }

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
