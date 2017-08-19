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

import org.jackhuang.hmcl.game.*
import org.jackhuang.hmcl.task.*
import org.jackhuang.hmcl.util.*
import java.util.*

class DefaultGameBuilder(val dependencyManager: DefaultDependencyManager): GameBuilder() {
    val repository = dependencyManager.repository
    val downloadProvider = dependencyManager.downloadProvider

    override fun buildAsync(): Task {
        val gameVersion = gameVersion
        return VersionJSONDownloadTask(gameVersion, dependencyManager, "raw_version_json")
                .then {
                    var version = GSON.fromJson<Version>(it["raw_version_json"])!!
                    it["version"] = version
                    version = version.copy(id = name, jar = null)
                    var result = ParallelTask(
                            GameAssetDownloadTask(dependencyManager, version),
                            GameLoggingDownloadTask(dependencyManager, version),
                            GameDownloadTask(version),
                            GameLibrariesTask(dependencyManager, version) // Game libraries will be downloaded for multiple times partly, this time is for vanilla libraries.
                    ) then VersionJSONSaveTask(dependencyManager, version)

                    if (toolVersions.containsKey("forge"))
                        result = result then libraryTaskHelper(gameVersion, "forge")
                    if (toolVersions.containsKey("liteloader"))
                        result = result then libraryTaskHelper(gameVersion, "liteloader")
                    if (toolVersions.containsKey("optifine"))
                        result = result then libraryTaskHelper(gameVersion, "optifine")
                    result
                }
    }

    private fun libraryTaskHelper(gameVersion: String, libraryId: String): (AutoTypingMap<String>) -> Task = {
        dependencyManager.installLibraryAsync(gameVersion, it["version"], libraryId, toolVersions[libraryId]!!)
    }

    private class VersionJSONDownloadTask(val gameVersion: String, val dependencyManager: DefaultDependencyManager, val id: String): Task() {
        override val dependents: MutableCollection<Task> = LinkedList()
        override val dependencies: MutableCollection<Task> = LinkedList()

        private val gameVersionList: VersionList<*> = dependencyManager.getVersionList("game")
        init {
            if (!gameVersionList.loaded)
                dependents += gameVersionList.refreshAsync(dependencyManager.downloadProvider)
        }

        override fun execute() {
            val remoteVersion = gameVersionList.getVersions(gameVersion).firstOrNull()
                    ?: throw Error("Cannot find specific version $gameVersion in remote repository")

            val jsonURL = dependencyManager.downloadProvider.injectURL(remoteVersion.url)
            dependencies += GetTask(jsonURL.toURL(), proxy = dependencyManager.proxy, id = id)
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