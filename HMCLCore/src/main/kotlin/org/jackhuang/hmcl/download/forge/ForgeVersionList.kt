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
package org.jackhuang.hmcl.download.forge

import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.RemoteVersion
import org.jackhuang.hmcl.download.VersionList
import org.jackhuang.hmcl.task.GetTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.*

object ForgeVersionList : VersionList<Unit>() {
    @JvmField
    val FORGE_LIST = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/json"

    override fun refreshAsync(downloadProvider: DownloadProvider): Task {
        return RefreshTask(downloadProvider)
    }

    private class RefreshTask(val downloadProvider: DownloadProvider): Task() {
        val task = GetTask(downloadProvider.injectURL(FORGE_LIST).toURL())
        override val dependents: Collection<Task> = listOf(task)
        override fun execute() {
            val root = GSON.fromJson<ForgeVersionRoot>(task.result!!) ?: return
            versions.clear()

            for ((x, versions) in root.mcversion!!.entries) {
                val gameVersion = x.asVersion() ?: continue
                for (v in versions) {
                    val version = root.number!![v] ?: continue
                    var jar: String? = null
                    for (file in version.files!!)
                        if (file.getOrNull(1) == "installer") {
                            val classifier = "${version.mcversion}-${version.version}" + (
                                    if (isNotBlank(version.branch))
                                        "-${version.branch}"
                                    else
                                        ""
                                    )
                            val fileName = "${root.artifact}-$classifier-${file[1]}.${file[0]}"
                            jar = downloadProvider.injectURL("${root.webpath}$classifier/$fileName")
                        }

                    if (jar == null) continue
                    val remoteVersion = RemoteVersion<Unit>(
                            gameVersion = version.mcversion!!,
                            selfVersion = version.version!!,
                            url = jar,
                            tag = Unit
                    )
                    ForgeVersionList.versions.put(gameVersion, remoteVersion)
                }
            }
        }
    }

}