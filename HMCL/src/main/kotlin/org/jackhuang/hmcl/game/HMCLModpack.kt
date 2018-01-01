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
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask
import org.jackhuang.hmcl.game.GameVersion.minecraftVersion
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.task.Task
import java.io.File
import java.io.IOException
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.util.*
import org.jackhuang.hmcl.util.Constants.GSON
import org.jackhuang.hmcl.util.Logging.LOG
import java.util.ArrayList

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
    val manifestJson = CompressingUtils.readTextZipEntry(f, "modpack.json")
    val manifest = GSON.fromJson<Modpack>(manifestJson) ?: throw JsonParseException("`modpack.json` not found. $f is not a valid HMCL modpack.")
    val gameJson = CompressingUtils.readTextZipEntry(f, "minecraft/pack.json")
    val game = GSON.fromJson<Version>(gameJson) ?: throw JsonParseException("`minecraft/pack.json` not found. $f iot a valid HMCL modpack.")
    return if (game.jar == null)
        if (manifest.gameVersion.isNullOrBlank()) throw JsonParseException("Cannot recognize the game version of modpack $f.")
        else manifest.setManifest(HMCLModpackManifest)
    else manifest.setManifest(HMCLModpackManifest).setGameVersion(game.jar)
}

object HMCLModpackManifest

class HMCLModpackInstallTask(profile: Profile, private val zipFile: File, modpack: Modpack, private val version: String): Task() {
    private val dependency = profile.dependency
    private val repository = profile.repository
    private val dependencies = mutableListOf<Task>()
    private val dependents = mutableListOf<Task>()

    override fun getDependencies() = dependencies
    override fun getDependents() = dependents

    init {
        check(!repository.hasVersion(version), { "Version $version already exists." })
        val json = CompressingUtils.readTextZipEntry(zipFile, "minecraft/pack.json")
        var version = GSON.fromJson<Version>(json)!!
        version = version.setJar(null)
        dependents += dependency.gameBuilder().name(this.version).gameVersion(modpack.gameVersion!!).buildAsync()
        dependencies += VersionJsonSaveTask(repository, version) // override the json created by buildAsync()

        onDone() += { event -> if (event.isFailed) repository.removeVersionFromDisk(this.version) }
    }

    private val run = repository.getRunDirectory(version)

    override fun execute() {
        CompressingUtils.unzip(zipFile, run, "minecraft/", { it != "minecraft/pack.json" }, false)
    }
}

val MODPACK_BLACK_LIST = listOf("usernamecache.json", "asm", "logs", "backups", "versions", "assets", "usercache.json", "libraries", "crash-reports", "launcher_profiles.json", "NVIDIA", "AMD", "TCNodeTracker", "screenshots", "natives", "native", "\$native", "pack.json", "launcher.jar", "minetweaker.log", "launcher.pack.lzma", "hmclmc.log")
val MODPACK_NORMAL_LIST = listOf("fonts", "saves", "servers.dat", "options.txt", "optionsof.txt", "journeymap", "optionsshaders.txt", "mods/VoxelMods")

/**
 *
 * @author huang
 */
interface ModAdviser {

    fun advise(fileName: String, isDirectory: Boolean): ModSuggestion

    enum class ModSuggestion {
        SUGGESTED,
        NORMAL,
        HIDDEN
    }

}

private fun match(l: List<String>, fileName: String, isDirectory: Boolean): Boolean {
    for (s in l)
        if (isDirectory) {
            if (fileName.startsWith(s + "/"))
                return true
        } else if (fileName == s)
            return true
    return false
}

var MODPACK_PREDICATE = object : ModAdviser {
    override fun advise(fileName: String, isDirectory: Boolean): ModAdviser.ModSuggestion {
        return if (match(MODPACK_BLACK_LIST, fileName, isDirectory))
            ModAdviser.ModSuggestion.HIDDEN
        else if (match(MODPACK_NORMAL_LIST, fileName, isDirectory))
            ModAdviser.ModSuggestion.NORMAL
        else
            ModAdviser.ModSuggestion.SUGGESTED
    }
}

class HMCLModpackExportTask @JvmOverloads constructor(
        private val repository: DefaultGameRepository,
        private val version: String,
        private val whitelist: List<String>,
        private val modpack: Modpack,
        private val output: File,
        private val id: String = ID): TaskResult<ZipEngine>() {

    override fun getId() = id

    init {
        onDone() += { event -> if (event.isFailed) output.delete() }
    }

    override fun execute() {
        val blackList = ArrayList<String>(MODPACK_BLACK_LIST)
        blackList.add(version + ".jar")
        blackList.add(version + ".json")
        LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker")
        ZipEngine(output).use { zip ->
            zip.putDirectory(repository.getRunDirectory(version)) a@ { pathName: String, isDirectory: Boolean ->
                for (s in blackList) {
                    if (isDirectory) {
                        if (pathName.startsWith(s + "/"))
                            return@a null
                    } else if (pathName == s)
                        return@a null
                }
                for (s in whitelist)
                    if (s + (if (isDirectory) "/" else "") == pathName)
                        return@a "minecraft/" + pathName
                null
            }

            val mv = repository.getVersion(version).resolve(repository)
            val gameVersion = minecraftVersion(repository.getVersionJar(version)) ?: throw IllegalStateException("Cannot parse the version of $version")
            zip.putTextFile(GSON.toJson(mv.setJar(gameVersion)), "minecraft/pack.json") // Making "jar" to gameVersion is to be compatible with old HMCL.
            zip.putTextFile(GSON.toJson(modpack.setGameVersion(gameVersion)), "modpack.json") // Newer HMCL only reads 'gameVersion' field.
        }
    }

    companion object {
        val ID = "zip_engine"
    }
}