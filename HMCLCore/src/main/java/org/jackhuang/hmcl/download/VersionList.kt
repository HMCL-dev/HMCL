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

import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.SimpleMultimap
import java.util.*
import kotlin.collections.HashMap

abstract class VersionList<T> {
    @JvmField
    protected val versions = SimpleMultimap<String, RemoteVersion<T>>(::HashMap, ::TreeSet)

    val loaded = versions.isNotEmpty

    abstract fun refreshAsync(downloadProvider: DownloadProvider): Task

    protected open fun getVersionsImpl(gameVersion: String): Collection<RemoteVersion<T>> {
        val ans = versions[gameVersion]
        return if (ans.isEmpty()) versions.values else ans
    }

    /**
     * @param gameVersion the Minecraft version that remote versions belong to
     * @return the collection of specific remote versions
     */
    fun getVersions(gameVersion: String): Collection<RemoteVersion<T>> {
        return Collections.unmodifiableCollection(getVersionsImpl(gameVersion))
    }

    fun getVersion(gameVersion: String, remoteVersion: String): RemoteVersion<T>? {
        var result : RemoteVersion<T>? = null
        versions[gameVersion].forEach {
            if (it.selfVersion == remoteVersion)
                result = it
        }
        return result
    }

}