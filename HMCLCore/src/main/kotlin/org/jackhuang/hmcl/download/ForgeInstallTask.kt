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

import org.jackhuang.hmcl.game.Library
import org.jackhuang.hmcl.game.SimpleVersionProvider
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.task.FileDownloadTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.task.then
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class ForgeInstallTask(private val dependencyManager: DefaultDependencyManager,
                        private val gameVersion: String,
                        private val version: Version,
                        private val remoteVersion: String) : TaskResult<Version>() {
    private val forgeVersionList = dependencyManager.getVersionList("forge")
    private val installer: File = File("forge-installer.jar").absoluteFile
    lateinit var remote: RemoteVersion<*>
    override val dependents = mutableListOf<Task>()
    override val dependencies = mutableListOf<Task>()
    override val id = ID

    init {
        if (!forgeVersionList.loaded)
            dependents += forgeVersionList.refreshAsync(dependencyManager.downloadProvider) then {
                remote = forgeVersionList.getVersion(gameVersion, remoteVersion) ?: throw IllegalArgumentException("Remote forge version $gameVersion, $remoteVersion not found")
                FileDownloadTask(remote.url.toURL(), installer)
            }
        else {
            remote = forgeVersionList.getVersion(gameVersion, remoteVersion) ?: throw IllegalArgumentException("Remote forge version $gameVersion, $remoteVersion not found")
            dependents += FileDownloadTask(remote.url.toURL(), installer)
        }
    }

    override fun execute() {
        ZipFile(installer).use { zipFile ->
            val installProfile = GSON.fromJson<InstallProfile>(zipFile.getInputStream(zipFile.getEntry("install_profile.json")).readFullyAsString()) ?: throw IOException("install_profile.json is not found.")

            // unpack the universal jar in the installer file.

            val forgeLibrary = Library.fromName(installProfile.install!!.path!!)
            val forgeFile = dependencyManager.repository.getLibraryFile(version, forgeLibrary)
            if (!forgeFile.makeFile())
                throw IOException("Cannot make directory ${forgeFile.parentFile}")

            val forgeEntry = zipFile.getEntry(installProfile.install.filePath)
            zipFile.getInputStream(forgeEntry).copyToAndClose(forgeFile.outputStream())

            // resolve the version
            val versionProvider = SimpleVersionProvider()
            versionProvider.addVersion(version)

            result = installProfile.versionInfo!!.copy(inheritsFrom = version.id).resolve(versionProvider).copy(id = version.id)
            dependencies += GameLibrariesTask(dependencyManager, installProfile.versionInfo)
        }

        check(installer.delete(), { "Unable to delete installer file $installer" })
    }

    companion object {
        const val ID = "forge_install_task"
    }
}