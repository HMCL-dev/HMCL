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

import org.jackhuang.hmcl.download.forge.ForgeInstallTask
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask
import org.jackhuang.hmcl.download.game.GameLibrariesTask
import org.jackhuang.hmcl.download.game.GameLoggingDownloadTask
import org.jackhuang.hmcl.download.game.VersionJSONSaveTask
import org.jackhuang.hmcl.download.liteloader.LiteLoaderInstallTask
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask
import org.jackhuang.hmcl.game.DefaultGameRepository
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.task.ParallelTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.then
import java.net.Proxy

/**
 * This class has no state.
 */
class DefaultDependencyManager(override val repository: DefaultGameRepository, override var downloadProvider: DownloadProvider, override val proxy: Proxy = Proxy.NO_PROXY)
    : AbstractDependencyManager() {

    override fun gameBuilder(): GameBuilder = DefaultGameBuilder(this)

    override fun checkGameCompletionAsync(version: Version): Task {
        val tasks: Array<Task> = arrayOf(
                GameAssetDownloadTask(this, version),
                GameLoggingDownloadTask(this, version),
                GameLibrariesTask(this, version)
        )
        return ParallelTask(*tasks)
    }

    override fun installLibraryAsync(gameVersion: String, version: Version, libraryId: String, libraryVersion: String): Task {
        if (libraryId == "forge")
            return ForgeInstallTask(this, gameVersion, version, libraryVersion)
                    .then { VersionJSONSaveTask(this, it["version"]) }
        else if (libraryId == "liteloader")
            return LiteLoaderInstallTask(this, gameVersion, version, libraryVersion)
                    .then { VersionJSONSaveTask(this, it["version"]) }
        else if (libraryId == "optifine")
            return OptiFineInstallTask(this, gameVersion, version, libraryVersion)
                    .then { VersionJSONSaveTask(this, it["version"]) }
        else
            throw IllegalArgumentException("Library id $libraryId is unrecognized.")
    }
}