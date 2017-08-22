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

import com.google.gson.JsonParseException
import org.jackhuang.hmcl.download.game.VersionJSONSaveTask
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.fromJson
import org.jackhuang.hmcl.util.readTextFromZipFile
import org.jackhuang.hmcl.util.unzipSubDirectory
import java.io.File
import java.io.IOException

/**
 * Read the manifest in a HMCL modpack.
 *
 * @param f a HMCL modpack file.
 * @throws IOException if the file is not a valid zip file.
 * @throws JsonParseException if the manifest.json is missing or malformed.
 * @return the manifest of HMCL modpack.
 */
@Throws(IOException::class, JsonParseException::class)
fun readHMCLModpackManifest(f: File): Modpack {
    val json = readTextFromZipFile(f, "modpack.json")
    return GSON.fromJson<Modpack>(json)?.copy(manifest = HMCLModpackManifest) ?: throw JsonParseException("`modpack.json` not found. Not a valid CurseForge modpack.")
}

object HMCLModpackManifest

class HMCLModpackInstallTask(profile: Profile, private val zipFile: File, private val name: String): Task() {
    private val dependency = profile.dependency
    private val repository = profile.repository
    override val dependencies = mutableListOf<Task>()
    override val dependents = mutableListOf<Task>()

    init {
        check(!repository.hasVersion(name), { "Version $name already exists." })
        val json = readTextFromZipFile(zipFile, "minecraft/pack.json")
        var version = GSON.fromJson<Version>(json)!!
        val jar = version.jar!!
        version = version.copy(jar = null)
        dependents += dependency.gameBuilder().name(name).gameVersion(jar).buildAsync()
        dependencies += dependency.checkGameCompletionAsync(version)
        dependencies += VersionJSONSaveTask(dependency, version)
    }

    private val run = repository.getRunDirectory(name)

    override fun execute() {
        unzipSubDirectory(zipFile, run, "minecraft/", false)
        val json = run.resolve("pack.json")
        if (repository.getVersionJson(name) != json)
            json.delete()
    }
}