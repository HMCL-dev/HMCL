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

import org.jackhuang.hmcl.task.GetTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.asVersion
import org.jackhuang.hmcl.util.toURL
import org.jackhuang.hmcl.util.typeOf
import java.util.TreeSet

object OptiFineBMCLVersionList : VersionList<Unit>() {
    override fun refreshAsync(downloadProvider: DownloadProvider): Task {
        return RefreshTask(downloadProvider)
    }

    private class RefreshTask(val downloadProvider: DownloadProvider): Task() {
        val task = GetTask("http://bmclapi.bangbang93.com/optifine/versionlist".toURL())
        override val dependents: Collection<Task> = listOf(task)
        override fun execute() {
            versionMap.clear()
            versions.clear()

            val duplicates = mutableSetOf<String>()
            val root = GSON.fromJson<List<OptiFineVersion>>(task.result!!, typeOf<List<OptiFineVersion>>())
            for (element in root) {
                val version = element.type ?: continue
                val mirror = "http://bmclapi2.bangbang93.com/optifine/${element.gameVersion}/${element.type}/${element.patch}"
                if (duplicates.contains(mirror))
                    continue
                else
                    duplicates += mirror

                val gameVersion = element.gameVersion?.asVersion() ?: continue
                val remoteVersion = RemoteVersion<Unit>(
                        gameVersion = gameVersion,
                        selfVersion = version,
                        url = mirror,
                        tag = Unit
                )

                val set = versionMap.getOrPut(gameVersion, { TreeSet<RemoteVersion<Unit>>() }) as MutableCollection<RemoteVersion<Unit>>
                set.add(remoteVersion)
                versions.add(remoteVersion)
            }
        }

    }

}