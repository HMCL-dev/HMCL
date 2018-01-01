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
package org.jackhuang.hmcl.game

import org.jackhuang.hmcl.mod.*
import org.jackhuang.hmcl.setting.EnumGameDirectory
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.toStringOrEmpty
import java.io.File

fun readModpackManifest(f: File): Modpack {
    try {
        return CurseManifest.readCurseForgeModpackManifest(f);
    } catch (e: Exception) {
        // ignore it, not a valid CurseForge modpack.
    }

    try {
        val manifest = readHMCLModpackManifest(f)
        return manifest
    } catch (e: Exception) {
        // ignore it, not a valid HMCL modpack.
    }

    try {
        val manifest = MultiMCInstanceConfiguration.readMultiMCModpackManifest(f)
        return manifest
    } catch (e: Exception) {
        // ignore it, not a valid MMC modpack.
    }

    throw IllegalArgumentException("Modpack file $f is not supported.")
}

fun MultiMCInstanceConfiguration.toVersionSetting(vs: VersionSetting) {
    vs.usesGlobal = false
    vs.gameDirType = EnumGameDirectory.VERSION_FOLDER

    if (isOverrideJavaLocation) {
        vs.javaDir = javaPath.toStringOrEmpty()
    }

    if (isOverrideMemory) {
        vs.permSize = permGen.toStringOrEmpty()
        if (maxMemory != null)
            vs.maxMemory = maxMemory!!
        vs.minMemory = minMemory
    }

    if (isOverrideCommands) {
        vs.wrapper = wrapperCommand.orEmpty()
        vs.precalledCommand = preLaunchCommand.orEmpty()
    }

    if (isOverrideJavaArgs) {
        vs.javaArgs = jvmArgs.orEmpty()
    }

    if (isOverrideConsole) {
        vs.showLogs = isShowConsole
    }

    if (isOverrideWindow) {
        vs.fullscreen = isFullscreen
        if (width != null)
            vs.width = width!!
        if (height != null)
            vs.height = height!!
    }
}

class MMCInstallVersionSettingTask(private val profile: Profile, val manifest: MultiMCInstanceConfiguration, private val version: String): Task() {
    override fun getScheduler() = Schedulers.javafx()
    override fun execute() {
        val vs = profile.specializeVersionSetting(version)!!
        manifest.toVersionSetting(vs)
    }
}