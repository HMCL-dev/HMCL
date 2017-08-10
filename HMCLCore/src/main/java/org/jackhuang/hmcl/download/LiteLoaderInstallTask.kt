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
package org.jackhuang.hmcl.download

import org.jackhuang.hmcl.game.LibrariesDownloadInfo
import org.jackhuang.hmcl.game.Library
import org.jackhuang.hmcl.game.LibraryDownloadInfo
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.task.then
import org.jackhuang.hmcl.util.merge

/**
 * LiteLoader must be installed after Forge.
 */
class LiteLoaderInstallTask(private val dependencyManager: DefaultDependencyManager,
                             private val gameVersion: String,
                             private val version: Version,
                             private val remoteVersion: String): TaskResult<Version>() {
    private val liteLoaderVersionList = dependencyManager.getVersionList("liteloader") as LiteLoaderVersionList
    lateinit var remote: RemoteVersion<LiteLoaderRemoteVersionTag>
    override val dependents: MutableCollection<Task> = mutableListOf()
    override val dependencies: MutableCollection<Task> = mutableListOf()

    init {
        if (!liteLoaderVersionList.loaded)
            dependents += LiteLoaderVersionList.refreshAsync(dependencyManager.downloadProvider) then {
                remote = liteLoaderVersionList.getVersion(gameVersion, remoteVersion) ?: throw IllegalArgumentException("Remote LiteLoader version $gameVersion, $remoteVersion not found")
                null
            }
        else {
            remote = liteLoaderVersionList.getVersion(gameVersion, remoteVersion) ?: throw IllegalArgumentException("Remote LiteLoader version $gameVersion, $remoteVersion not found")
        }
    }

    override fun execute() {
        val library = Library(
                groupId = "com.mumfrey",
                artifactId = "liteloader",
                version = remote.selfVersion,
                url = "http://dl.liteloader.com/versions/",
                downloads = LibrariesDownloadInfo(
                        artifact = LibraryDownloadInfo(
                                url = remote.url
                        )
                )
        )
        val tempVersion = version.copy(libraries = merge(remote.tag.libraries, listOf(library)))
        result = version.copy(
                mainClass = "net.minecraft.launchwrapper.Launch",
                minecraftArguments = version.minecraftArguments + " --tweakClass " + remote.tag.tweakClass,
                libraries = merge(tempVersion.libraries, version.libraries)
        )
        dependencies += GameLibrariesTask(dependencyManager, tempVersion)
    }

}