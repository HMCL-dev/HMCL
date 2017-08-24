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

import org.jackhuang.hmcl.mod.InstanceConfiguration
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.mod.readCurseForgeModpackManifest
import org.jackhuang.hmcl.mod.readMMCModpackManifest
import org.jackhuang.hmcl.setting.EnumGameDirectory
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.toStringOrEmpty
import java.io.File

fun readModpackManifest(f: File): Modpack {
    try {
        return readCurseForgeModpackManifest(f)
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
        val manifest = readMMCModpackManifest(f)
        return manifest
    } catch (e: Exception) {
        // ignore it, not a valid MMC modpack.
    }

    throw IllegalArgumentException("Modpack file $f is not supported.")
}

fun InstanceConfiguration.toVersionSetting(vs: VersionSetting) {
    vs.usesGlobal = false
    vs.gameDirType = EnumGameDirectory.VERSION_FOLDER

    if (overrideJavaLocation) {
        vs.javaDir = javaPath.toStringOrEmpty()
    }

    if (overrideMemory) {
        vs.permSize = permGen.toStringOrEmpty()
        if (maxMemory != null)
            vs.maxMemory = maxMemory!!
        vs.minMemory = minMemory
    }

    if (overrideCommands) {
        vs.wrapper = wrapperCommand.orEmpty()
        vs.precalledCommand = preLaunchCommand.orEmpty()
    }

    if (overrideJavaArgs) {
        vs.javaArgs = jvmArgs.orEmpty()
    }

    if (overrideConsole) {
        vs.showLogs = showConsole
    }

    if (overrideWindow) {
        vs.fullscreen = fullscreen
        if (width != null)
            vs.width = width!!
        if (height != null)
            vs.height = height!!
    }
}

class MMCInstallVersionSettingTask(private val profile: Profile, val manifest: InstanceConfiguration, val name: String): Task() {
    override val scheduler = Scheduler.JAVAFX
    override fun execute() {
        val vs = profile.specializeVersionSetting(name)!!
        manifest.toVersionSetting(vs)
    }
}