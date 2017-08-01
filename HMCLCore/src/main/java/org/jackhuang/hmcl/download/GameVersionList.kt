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

import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.asVersion
import org.jackhuang.hmcl.util.fromJson
import org.jackhuang.hmcl.task.GetTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.toURL


object GameVersionList : VersionList<GameRemoteVersionTag>() {

    override fun refreshAsync(downloadProvider: DownloadProvider): Task {
        return RefreshTask(downloadProvider)
    }

    private class RefreshTask(provider: DownloadProvider) : Task() {
        val task = GetTask(provider.versionListURL.toURL())
        override val dependents: Collection<Task> = listOf(task)
        override fun execute() {
            versionMap.clear()
            versions.clear()

            val root = GSON.fromJson<GameRemoteVersions>(task.result!!) ?: return
            for (remoteVersion in root.versions) {
                val gg = remoteVersion.gameVersion.asVersion() ?: continue
                val x = RemoteVersion(
                        gameVersion = remoteVersion.gameVersion,
                        selfVersion = remoteVersion.gameVersion,
                        url = remoteVersion.url,
                        tag = GameRemoteVersionTag(
                                type = remoteVersion.type,
                                time = remoteVersion.releaseTime
                        )
                )
                versions.add(x)
                versionMap[gg] = listOf(x)
            }
        }
    }
}