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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Supports operations on versioning.
 *
 * Note that game repository will not do any operations which need connection with Internet, if do,
 * see {@link org.jackhuang.hmcl.download.DependencyManager}
 *
 * @author huangyuhui
 */
public interface GameRepository extends VersionProvider {

    /**
     * Does the version of id exist?
     *
     * @param id the id of version
     * @return true if the version exists
     */
    @Override
    boolean hasVersion(String id);

    /**
     * Get the version
     *
     * @param id the id of version
     * @return the version you want
     * @throws VersionNotFoundException if no version is id.
     */
    @Override
    Version getVersion(String id) throws VersionNotFoundException;

    default Version getResolvedVersion(String id) throws VersionNotFoundException {
        return getVersion(id).resolve(this);
    }

    default Version getResolvedPreservingPatchesVersion(String id) throws VersionNotFoundException {
        return getVersion(id).resolvePreservingPatches(this);
    }

    /**
     * How many version are there?
     */
    int getVersionCount();

    /**
     * Gets the collection of versions
     *
     * @return the collection of versions
     */
    Collection<Version> getVersions();

    /**
     * Load version list.
     *
     * This method should be called before launching a version.
     * A time-costly operation.
     * You'd better execute this method in a new thread.
     */
    void refreshVersions();

    default Task<Void> refreshVersionsAsync() {
        return Task.runAsync(this::refreshVersions);
    }

    /**
     * Gets the root folder of specific version.
     * The root folders the versions must be unique.
     * For example, .minecraft/versions/&lt;version name&gt;/.
     */
    File getVersionRoot(String id);

    /**
     * Gets the current running directory of the given version for game.
     *
     * @param id the version id
     */
    File getRunDirectory(String id);

    File getLibrariesDirectory(Version version);

    /**
     * Get the library file in disk.
     * This method allows versions and libraries that are not loaded by this game repository.
     *
     * @param version the reference of game version
     * @param lib the library, {@link Version#getLibraries()}
     * @return the library file
     */
    File getLibraryFile(Version version, Library lib);

    /**
     * Get the directory that native libraries will be unzipped to.
     *
     * You'd better return a unique directory.
     * Or if it returns a temporary directory, {@link org.jackhuang.hmcl.launch.Launcher#makeLaunchScript} will fail.
     * If you do want to return a temporary directory, make {@link org.jackhuang.hmcl.launch.Launcher#makeLaunchScript}
     * always fail({@code UnsupportedOperationException}) and not to use it.
     *
     * @param id version id
     * @param platform the platform of native libraries
     * @return the native directory
     */
    File getNativeDirectory(String id, Platform platform);

    /**
     * Get minecraft jar
     *
     * @param version resolvedVersion
     * @return the minecraft jar
     */
    File getVersionJar(Version version);

    /**
     * Detect game version.
     *
     * This method is time-consuming, but the result will be cached.
     * Consider running this job in IO scheduler.
     *
     * @param version version
     * @return game version, or empty if an error occurred in detection.
     */
    Optional<String> getGameVersion(Version version);

    /**
     * Detect game version.
     *
     * This method is time-consuming, but the result will be cached.
     * Consider running this job in IO scheduler.
     *
     * @param versionId id of version
     * @return game version, or empty if an error occurred in detection.
     */
    default Optional<String> getGameVersion(String versionId) throws VersionNotFoundException {
        return getGameVersion(getVersion(versionId));
    }

    /**
     * Get minecraft jar
     *
     * @param version version id
     * @return the minecraft jar
     */
    default File getVersionJar(String version) throws VersionNotFoundException {
        return getVersionJar(getVersion(version).resolve(this));
    }

    /**
     * Rename given version to new name.
     *
     * @param from The id of original version
     * @param to The new id of the version
     * @throws UnsupportedOperationException if this game repository does not support renaming a version
     * @return true if the operation is done successfully, false if version `from` not found, version json is malformed or I/O errors occurred.
     */
    boolean renameVersion(String from, String to);

    /**
     * Get actual asset directory.
     * Will reconstruct assets or do some blocking tasks if necessary.
     * You'd better create a new thread to invoke this method.
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     * @return the actual asset directory
     */
    Path getActualAssetDirectory(String version, String assetId);

    /**
     * Get the asset directory according to the asset id.
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     * @return the asset directory
     */
    Path getAssetDirectory(String version, String assetId);

    /**
     * Get the file that given asset object refers to
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     * @param name the asset object name, you can find it in keys of {@link AssetIndex#getObjects()}
     * @throws java.io.IOException if I/O operation fails.
     * @return the file that given asset object refers to
     */
    Optional<Path> getAssetObject(String version, String assetId, String name) throws IOException;

    /**
     * Get the file that given asset object refers to
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     * @param obj the asset object, you can find it in {@link AssetIndex#getObjects()}
     * @return the file that given asset object refers to
     */
    Path getAssetObject(String version, String assetId, AssetObject obj);

    /**
     * Get asset index that assetId represents
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     * @return the asset index
     */
    AssetIndex getAssetIndex(String version, String assetId) throws IOException;

    /**
     * Get the asset_index.json which includes asset objects information.
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     */
    Path getIndexFile(String version, String assetId);

    /**
     * Get logging object
     *
     * @param version the id of specific version that is relevant to {@code assetId}
     * @param assetId the asset id, you can find it in {@link AssetIndexInfo#getId()} {@link Version#getAssetIndex()}
     * @param loggingInfo the logging info
     * @return the file that loggingInfo refers to
     */
    Path getLoggingObject(String version, String assetId, LoggingInfo loggingInfo);

    default List<String> getClasspath(Version version) {
        List<String> classpath = new ArrayList<>();
        for (Library library : version.getLibraries())
            if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                File f = getLibraryFile(version, library);
                if (f.exists() && f.isFile())
                    classpath.add(f.getAbsolutePath());
            }
        return classpath;
    }
}
