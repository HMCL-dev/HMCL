/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.task.Task;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Supports operations on versioning.
 *
 * Note that game repository will not do any tasks that connect to Internet, if do, see {@link org.jackhuang.hmcl.download.DependencyManager}
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
     */
    @Override
    Version getVersion(String id);

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

    default Task refreshVersionsAsync() {
        return Task.of(this::refreshVersions);
    }

    /**
     * Gets the root folder of specific version.
     * The root folders the versions must be unique.
     * For example, .minecraft/versions/<version name>/.
     */
    File getVersionRoot(String id);

    /**
     * Gets the current running directory of the given version for game.
     *
     * @param id the version id
     */
    File getRunDirectory(String id);

    /**
     * Get the library file in disk.
     * This method allows versions and libraries that are not loaded by this game repository.
     *
     * @param version versionversion
     * @param lib the library, [Version.libraries]
     * @return the library file
     */
    File getLibraryFile(Version version, Library lib);

    /**
     * Get the directory that native libraries will be unzipped to.
     *
     * You'd better return a unique directory.
     * Or if it returns a temporary directory, [org.jackhuang.hmcl.launch.Launcher.makeLaunchScript] will fail.
     * If you do want to return a temporary directory, make [org.jackhuang.hmcl.launch.Launcher.makeLaunchScript] always fail([UnsupportedOperationException]) and not to use it.
     *
     * @param id version id
     * @return the native directory
     */
    File getNativeDirectory(String id);

    /**
     * Get minecraft jar
     *
     * @param version resolvedVersion
     * @return the minecraft jar
     */
    File getVersionJar(Version version);

    /**
     * Get minecraft jar
     *
     * @param version version id
     * @return the minecraft jar
     */
    default File getVersionJar(String version) {
        return getVersionJar(getVersion(version).resolve(this));
    }

    /**
     * Rename given version to new name.
     *
     * @param from The id of original version
     * @param to The new id of the version
     * @throws UnsupportedOperationException if this game repository does not support renaming a version
     * @throws java.io.IOException if I/O operation fails.
     * @return true if the operation is done successfully.
     */
    boolean renameVersion(String from, String to);

    /**
     * Get actual asset directory.
     * Will reconstruct assets or do some blocking tasks if necessary.
     * You'd better create a new thread to invoke this method.
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.actualAssetIndex.id]
     * @throws java.io.IOException if I/O operation fails.
     * @return the actual asset directory
     */
    File getActualAssetDirectory(String version, String assetId);

    /**
     * Get the asset directory according to the asset id.
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.actualAssetIndex.id]
     * @return the asset directory
     */
    File getAssetDirectory(String version, String assetId);

    /**
     * Get the file that given asset object refers to
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.actualAssetIndex.id]
     * @param name the asset object name, you can find it in [AssetIndex.objects.keys]
     * @throws java.io.IOException if I/O operation fails.
     * @return the file that given asset object refers to
     */
    File getAssetObject(String version, String assetId, String name) throws IOException;

    /**
     * Get the file that given asset object refers to
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.actualAssetIndex.id]
     * @param obj the asset object, you can find it in [AssetIndex.objects]
     * @return the file that given asset object refers to
     */
    File getAssetObject(String version, String assetId, AssetObject obj);

    /**
     * Get asset index that assetId represents
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.actualAssetIndex.id]
     * @return the asset index
     */
    AssetIndex getAssetIndex(String version, String assetId) throws IOException;

    /**
     * Get the asset_index.json which includes asset objects information.
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.actualAssetIndex.id]
     */
    File getIndexFile(String version, String assetId);

    /**
     * Get logging object
     *
     * @param version the id of specific version that is relevant to [assetId]
     * @param assetId the asset id, you can find it in [AssetIndexInfo.id] [Version.assets]
     * @param loggingInfo the logging info
     * @return the file that loggingInfo refers to
     */
    File getLoggingObject(String version, String assetId, LoggingInfo loggingInfo);

}
