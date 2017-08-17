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

import org.jackhuang.hmcl.game.AssetIndex
import org.jackhuang.hmcl.game.AssetObject
import org.jackhuang.hmcl.game.DownloadType
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.task.FileDownloadTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.util.*
import java.util.logging.Level


/**
 * This task is to download game libraries.
 * This task should be executed last(especially after game downloading, Forge, LiteLoader and OptiFine install task)
 * @param resolvedVersion the <b>resolved</b> version
 */
class GameLibrariesTask(private val dependencyManager: DefaultDependencyManager, private val resolvedVersion: Version): Task() {
    override val dependencies: MutableCollection<Task> = LinkedList()
    override fun execute() {
        for (library in resolvedVersion.libraries)
            if (library.appliesToCurrentEnvironment) {
                val file = dependencyManager.repository.getLibraryFile(resolvedVersion, library)
                if (!file.exists())
                    dependencies += FileDownloadTask(dependencyManager.downloadProvider.injectURL(library.download.url).toURL(), file, library.download.sha1, proxy = dependencyManager.proxy)
            }
    }

}

class GameLoggingDownloadTask(private val dependencyManager: DefaultDependencyManager, private val version: Version) : Task() {
    override val dependencies: MutableCollection<Task> = LinkedList()
    override fun execute() {
        val logging = version.logging?.get(DownloadType.CLIENT) ?: return
        val file = dependencyManager.repository.getLoggingObject(version.id, version.actualAssetIndex.id, logging)
        if (!file.exists())
            dependencies += FileDownloadTask(logging.file.url.toURL(), file, proxy = dependencyManager.proxy)
    }
}

class GameAssetIndexDownloadTask(private val dependencyManager: DefaultDependencyManager, private val version: Version) : Task() {
    override val dependencies: MutableCollection<Task> = LinkedList()
    override fun execute() {
        val assetIndexInfo = version.actualAssetIndex
        val assetDir = dependencyManager.repository.getAssetDirectory(version.id, assetIndexInfo.id)
        if (!assetDir.makeDirectory())
            throw IOException("Cannot create directory: $assetDir")

        val assetIndexFile = dependencyManager.repository.getIndexFile(version.id, assetIndexInfo.id)
        dependencies += FileDownloadTask(dependencyManager.downloadProvider.injectURL(assetIndexInfo.url).toURL(), assetIndexFile, proxy = dependencyManager.proxy)
    }
}

class GameAssetRefreshTask(private val dependencyManager: DefaultDependencyManager, private val version: Version) : TaskResult<Collection<Pair<File, AssetObject>>>() {
    private val assetIndexTask = GameAssetIndexDownloadTask(dependencyManager, version)
    private val assetIndexInfo = version.actualAssetIndex
    private val assetIndexFile = dependencyManager.repository.getIndexFile(version.id, assetIndexInfo.id)
    override val dependents: MutableCollection<Task> = LinkedList()

    init {
        if (!assetIndexFile.exists())
            dependents += assetIndexTask
    }

    override fun execute() {
        val index = GSON.fromJson<AssetIndex>(assetIndexFile.readText())
        val res = LinkedList<Pair<File, AssetObject>>()
        var progress = 0
        index?.objects?.entries?.forEach { (_, assetObject) ->
            res += Pair(dependencyManager.repository.getAssetObject(version.id, assetIndexInfo.id, assetObject), assetObject)
            updateProgress(++progress, index.objects.size)
        }
        result = res
    }
}

class GameAssetDownloadTask(private val dependencyManager: DefaultDependencyManager, private val version: Version) : Task() {
    private val refreshTask = GameAssetRefreshTask(dependencyManager, version)
    override val dependents: Collection<Task> = listOf(refreshTask)
    override val dependencies: MutableCollection<Task> = LinkedList()
    override fun execute() {
        val size = refreshTask.result?.size ?: 0
        refreshTask.result?.forEach single@{ (file, assetObject) ->
            val url = dependencyManager.downloadProvider.assetBaseURL + assetObject.location
            if (!file.absoluteFile.parentFile.makeDirectory()) {
                LOG.severe("Unable to create new file $file, because parent directory cannot be created")
                return@single
            }
            if (file.isDirectory)
                return@single

            var flag = true
            var downloaded = 0
            try {
                // check the checksum of file to ensure that the file is not need to re-download.
                if (file.exists()) {
                    val sha1 = DigestUtils.sha1Hex(file.readBytes())
                    if (assetObject.hash == sha1) {
                        ++downloaded
                        LOG.finest("File $file has been downloaded successfully, skipped downloading")
                        updateProgress(downloaded, size)
                        return@single
                    }
                }
            } catch (e: IOException) {
                LOG.log(Level.WARNING, "Unable to get hash code of file $file", e)
                flag = !file.exists()
            }
            if (flag)
                dependencies += FileDownloadTask(url.toURL(), file, assetObject.hash, proxy = dependencyManager.proxy).apply {
                    title = assetObject.hash
                }
        }
    }
}

class VersionJSONSaveTask(private val dependencyManager: DefaultDependencyManager, private val version: Version): Task() {
    override fun execute() {
        val json = dependencyManager.repository.getVersionJson(version.id).absoluteFile
        if (!json.makeFile())
            throw IOException("Cannot create file $json")
        json.writeText(GSON.toJson(version))
    }
}
