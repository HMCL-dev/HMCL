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
package org.jackhuang.hmcl.download.liteloader

import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.RemoteVersion
import org.jackhuang.hmcl.download.VersionList
import org.jackhuang.hmcl.task.GetTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.asVersion
import org.jackhuang.hmcl.util.fromJson
import org.jackhuang.hmcl.util.toURL

object LiteLoaderVersionList : VersionList<LiteLoaderRemoteVersionTag>() {
    @JvmField
    val LITELOADER_LIST = "http://dl.liteloader.com/versions/versions.json"

    override fun refreshAsync(downloadProvider: DownloadProvider): Task {
        return RefreshTask(downloadProvider)
    }

    internal class RefreshTask(val downloadProvider: DownloadProvider) : Task() {
        val task = GetTask(downloadProvider.injectURL(LITELOADER_LIST).toURL())
        override val dependents: Collection<Task> = listOf(task)
        override fun execute() {
            val root = GSON.fromJson<LiteLoaderVersionsRoot>(task.result!!) ?: return
            versions.clear()

            for ((gameVersion, liteLoader) in root.versions.entries) {
                val gg = gameVersion.asVersion() ?: continue
                doBranch(gg, gameVersion, liteLoader.repo, liteLoader.artifacts, false)
                doBranch(gg, gameVersion, liteLoader.repo, liteLoader.snapshots, true)
            }
        }

        private fun doBranch(key: String, gameVersion: String, repository: LiteLoaderRepository?, branch: LiteLoaderBranch?, snapshot: Boolean) {
            if (branch == null || repository == null)
                return
            for ((branchName, v) in branch.liteLoader.entries) {
                if ("latest" == branchName)
                    continue
                val iv = RemoteVersion<LiteLoaderRemoteVersionTag>(
                        selfVersion = v.version.replace("SNAPSHOT", "SNAPSHOT-" + v.lastSuccessfulBuild),
                        gameVersion = gameVersion,
                        url = if (snapshot)
                            "http://jenkins.liteloader.com/view/$gameVersion/job/LiteLoader $gameVersion/lastSuccessfulBuild/artifact/build/libs/liteloader-${v.version}-release.jar"
                        else
                            downloadProvider.injectURL(repository.url + "com/mumfrey/liteloader/" + gameVersion + "/" + v.file),
                        tag = LiteLoaderRemoteVersionTag(tweakClass = v.tweakClass, libraries = v.libraries)
                )

                versions.put(key, iv)
            }
        }
    }

}