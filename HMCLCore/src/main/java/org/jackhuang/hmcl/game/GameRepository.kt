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
package org.jackhuang.hmcl.game

import java.io.File

/**
 * Supports operations on versioning.
 *
 * Note that game repository will not do any tasks that connect to Internet.
 */
interface GameRepository : VersionProvider {
    /**
     * Does the version of id exist?
     * @param id the id of version
     * @return true if the version exists
     */
    override fun hasVersion(id: String): Boolean

    /**
     * Get the version
     * @param id the id of version
     * @return the version you want
     */
    override fun getVersion(id: String): Version

    /**
     * How many version are there?
     */
    fun getVersionCount(): Int

    /**
     * Gets the collection of versions
     * @return the collection of versions
     */
    fun getVersions(): Collection<Version>

    /**
     * Load version list.
     *
     * This method should be called before launching a version.
     * A time-costly operation.
     * You'd better execute this method in a new thread.
     */
    fun refreshVersions()

    /**
     * Gets the current running directory of the given version for game.
     * @param id the version id
     */
    fun getRunDirectory(id: String): File

    /**
     * Get the library file in disk.
     * This method allows versions and libraries that are not loaded by this game repository.
     *
     * @param id version id
     * @param lib the library, [Version.libraries]
     * @return the library file
     */
    fun getLibraryFile(id: Version, lib: Library): File

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
    fun getNativeDirectory(id: String): File

    /**
     * Get minecraft jar
     *
     * @param version resolvedVersion
     * @return the minecraft jar
     */
    fun getVersionJar(version: Version): File

    /**
     * Get minecraft jar
     *
     * @param version version id
     * @return the minecraft jar
     */
    fun getVersionJar(version: String): File = getVersionJar(getVersion(version).resolve(this))

    /**
     * Rename given version to new name.
     *
     * @param from The id of original version
     * @param to The new id of the version
     * @throws UnsupportedOperationException if this game repository does not support renaming a version
     * @throws java.io.IOException if I/O operation fails.
     * @return true if the operation is done successfully.
     */
    fun renameVersion(from: String, to: String): Boolean

    /**
     * Get actual asset directory.
     * Will reconstruct assets or do some blocking tasks if necessary.
     * You'd better create a new thread to invoke this method.
     *
     * @param assetId the asset id, [AssetIndexInfo.id] [Version.assets]
     * @throws java.io.IOException if I/O operation fails.
     * @return the actual asset directory
     */
    fun getActualAssetDirectory(assetId: String): File

    /**
     * Get the asset directory according to the asset id.
     */
    fun getAssetDirectory(assetId: String): File

    /**
     * Get the file that given asset object refers to
     *
     * @param assetId the asset id, [AssetIndexInfo.id] [Version.assets]
     * @param name the asset object name, [AssetIndex.objects.key]
     * @throws java.io.IOException if I/O operation fails.
     * @return the file that given asset object refers to
     */
    fun getAssetObject(assetId: String, name: String): File

    /**
     * Get the file that given asset object refers to
     *
     * @param assetId the asset id, [AssetIndexInfo.id] [Version.assets]
     * @param obj the asset object, [AssetIndex.objects]
     * @return the file that given asset object refers to
     */
    fun getAssetObject(assetId: String, obj: AssetObject): File

    /**
     * Get asset index that assetId represents
     *
     * @param assetId the asset id, [AssetIndexInfo.id] [Version.assets]
     * @return the asset index
     */
    fun getAssetIndex(assetId: String): AssetIndex

    /**
     * Get logging object
     *
     * @param assetId the asset id, [AssetIndexInfo.id] [Version.assets]
     * @param loggingInfo the logging info
     * @return the file that loggingInfo refers to
     */
    fun getLoggingObject(assetId: String, loggingInfo: LoggingInfo): File
}