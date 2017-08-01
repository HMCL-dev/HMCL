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

import org.jackhuang.hmcl.download.game.GameAssetDownloadTask
import org.jackhuang.hmcl.download.game.GameLibrariesTask
import org.jackhuang.hmcl.download.game.GameLoggingDownloadTask
import org.jackhuang.hmcl.download.game.VersionJSONSaveTask
import org.jackhuang.hmcl.game.*
import org.jackhuang.hmcl.task.*
import org.jackhuang.hmcl.util.*
import java.util.*

class DefaultGameBuilder(val dependencyManager: DefaultDependencyManager): GameBuilder() {
    val repository = dependencyManager.repository
    val downloadProvider = dependencyManager.downloadProvider

    override fun buildAsync(): Task {
        return VersionJSONDownloadTask(gameVersion = gameVersion) then { task ->
            var version = GSON.fromJson<Version>(task.result!!)
            version = version.copy(jar = version.id, id = name)
            var result = ParallelTask(
                    GameAssetDownloadTask(dependencyManager, version),
                    GameLoggingDownloadTask(dependencyManager, version),
                    GameDownloadTask(version),
                    GameLibrariesTask(dependencyManager, version) // Game libraries will be downloaded for multiple times partly, this time is for vanilla libraries.
            ) then VersionJSONSaveTask(dependencyManager, version)

            if (toolVersions.containsKey("forge"))
                result = result then libraryTaskHelper(version, "forge")
            if (toolVersions.containsKey("liteloader"))
                result = result then libraryTaskHelper(version, "liteloader")
            if (toolVersions.containsKey("optifine"))
                result = result then libraryTaskHelper(version, "optifine")
            result
        }
    }

    private fun libraryTaskHelper(version: Version, libraryId: String): Task.(Task) -> Task = { prev ->
        var thisVersion = version
        if (prev is TaskResult<*> && prev.result is Version) {
            thisVersion = prev.result as Version
        }
        dependencyManager.installLibraryAsync(thisVersion, libraryId, toolVersions[libraryId]!!)
    }

    inner class VersionJSONDownloadTask(val gameVersion: String): Task() {
        override val dependents: MutableCollection<Task> = LinkedList()
        override val dependencies: MutableCollection<Task> = LinkedList()
        var httpTask: GetTask? = null
        val result: String? get() = httpTask?.result

        val gameVersionList: VersionList<*> = dependencyManager.getVersionList("game")
        init {
            if (!gameVersionList.loaded)
                dependents += gameVersionList.refreshAsync(downloadProvider)
        }

        override fun execute() {
            val remoteVersion = gameVersionList.getVersions(gameVersion).firstOrNull()
                    ?: throw Error("Cannot find specific version $gameVersion in remote repository")

            val jsonURL = downloadProvider.injectURL(remoteVersion.url)
            httpTask = GetTask(jsonURL.toURL(), proxy = dependencyManager.proxy)
            dependencies += httpTask!!
        }
    }

    inner class GameDownloadTask(var version: Version) : Task() {
        override val dependencies: MutableCollection<Task> = LinkedList()
        override fun execute() {
            val jar = repository.getVersionJar(version)

            dependencies += FileDownloadTask(
                    url = downloadProvider.injectURL(version.download.url).toURL(),
                    file = jar,
                    hash = version.download.sha1,
                    proxy = dependencyManager.proxy)
        }
    }
}