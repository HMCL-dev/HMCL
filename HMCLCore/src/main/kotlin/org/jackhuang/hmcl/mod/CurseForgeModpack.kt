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
package org.jackhuang.hmcl.mod

import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.download.DefaultDependencyManager
import org.jackhuang.hmcl.download.DependencyManager
import org.jackhuang.hmcl.game.GameException
import org.jackhuang.hmcl.task.FileDownloadTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.task
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.logging.Level
import java.util.zip.ZipFile

class CurseForgeModpackManifest @JvmOverloads constructor(
        @SerializedName("manifestType")
        val manifestType: String = MINECRAFT_MODPACK,
        @SerializedName("manifestVersion")
        val manifestVersion: Int = 1,
        @SerializedName("name")
        val name: String = "",
        @SerializedName("version")
        val version: String = "1.0",
        @SerializedName("author")
        val author: String = "",
        @SerializedName("overrides")
        val overrides: String = "overrides",
        @SerializedName("minecraft")
        val minecraft: CurseForgeModpackManifestMinecraft = CurseForgeModpackManifestMinecraft(),
        @SerializedName("files")
        val files: List<CurseForgeModpackManifestFile> = emptyList()
): Validation {
    override fun validate() {
        check(manifestType == MINECRAFT_MODPACK, { "Only support Minecraft modpack" })
    }

    companion object {
        val MINECRAFT_MODPACK = "minecraftModpack"
    }
}

class CurseForgeModpackManifestMinecraft (
        @SerializedName("version")
        val gameVersion: String = "",
        @SerializedName("modLoaders")
        val modLoaders: List<CurseForgeModpackManifestModLoader> = emptyList()
): Validation {
    override fun validate() {
        check(gameVersion.isNotBlank())
    }
}

class CurseForgeModpackManifestModLoader (
        @SerializedName("id")
        val id: String = "",
        @SerializedName("primary")
        val primary: Boolean = false
): Validation {
    override fun validate() {
        check(id.isNotBlank(), { "Curse Forge modpack manifest Mod loader id cannot be blank." })
    }
}

class CurseForgeModpackManifestFile (
        @SerializedName("projectID")
        val projectID: Int = 0,
        @SerializedName("fileID")
        val fileID: Int = 0,
        @SerializedName("fileName")
        var fileName: String = "",
        @SerializedName("required")
        val required: Boolean = true
): Validation {
    override fun validate() {
        check(projectID != 0)
        check(fileID != 0)
    }

    val url: URL get() = "https://minecraft.curseforge.com/projects/$projectID/files/$fileID/download".toURL()
}

/**
 * @param f the CurseForge modpack file.
 * @return the manifest.
 */
fun readCurseForgeModpackManifest(f: File): CurseForgeModpackManifest {
    ZipFile(f).use { zipFile ->
        val entry = zipFile.getEntry("manifest.json") ?: throw IOException("`manifest.json` not found. Not a valid CurseForge modpack.")
        val json = zipFile.getInputStream(entry).readFullyAsString()
        return GSON.fromJson<CurseForgeModpackManifest>(json) ?: throw JsonParseException("`manifest.json` not found. Not a valid CurseForge modpack.")
    }
}

/**
 * Install a downloaded CurseForge modpack.
 *
 * @param dependencyManager the dependency manager.
 * @param zipFile the CurseForge modpack file.
 * @param manifest The manifest content of given CurseForge modpack.
 * @param name the new version name
 * @see readCurseForgeModpackManifest
 */
class CurseForgeModpackInstallTask(private val dependencyManager: DefaultDependencyManager, private val zipFile: File, private val manifest: CurseForgeModpackManifest, private val name: String): Task() {
    val repository = dependencyManager.repository
    init {
        if (repository.hasVersion(name))
            throw IllegalStateException("Version $name already exists.")
    }

    val root = repository.getVersionRoot(name)
    val run = repository.getRunDirectory(name)
    override val dependents = mutableListOf<Task>()
    override val dependencies = mutableListOf<Task>()
    init {
        val builder = dependencyManager.gameBuilder().name(name).gameVersion(manifest.minecraft.gameVersion)
        manifest.minecraft.modLoaders.forEach {
            if (it.id.startsWith("forge-"))
                builder.version("forge", it.id.substring("forge-".length))
        }
        dependents += builder.buildAsync()
    }

    override fun execute() {
        unzipSubDirectory(zipFile, run, manifest.overrides)

        var finished = 0
        for (f in manifest.files) {
            try {
                f.fileName = f.url.detectFileName(dependencyManager.proxy)
                dependencies += FileDownloadTask(f.url, run.resolve("mods").resolve(f.fileName), proxy = dependencyManager.proxy)
            } catch (e: IOException) {
                // Because in China, the CurseForge is too difficult to visit.
                // So if failed, ignore it and retry next time.
            }
            ++finished
            updateProgress(1.0 * finished / manifest.files.size)
        }

        root.resolve("manifest.json").writeText(GSON.toJson(manifest))
    }
}

/**
 * Complete the CurseForge version.
 *
 * @param dependencyManager the dependency manager.
 * @param version the existent and physical version.
 */
class CurseForgeModpackCompletionTask(dependencyManager: DependencyManager, version: String): Task() {
    val repository = dependencyManager.repository
    val run = repository.getRunDirectory(version)
    private var manifest: CurseForgeModpackManifest?
    private val proxy = dependencyManager.proxy
    override val dependents = mutableListOf<Task>()
    override val dependencies = mutableListOf<Task>()

    init {
        try {
            val manifestFile = repository.getVersionRoot(version).resolve("manifest.json")
            if (!manifestFile.exists()) manifest = null
            else {
                manifest = GSON.fromJson<CurseForgeModpackManifest>(repository.getVersionRoot(version).resolve("manifest.json").readText())!!

                // Because in China, the CurseForge is too difficult to visit.
                // So caching the file name is necessary.
                for (f in manifest!!.files) {
                    if (f.fileName.isBlank())
                        dependents += task { f.fileName = f.url.detectFileName(proxy) }
                }
            }
        } catch (e: Exception) {
            LOG.log(Level.WARNING, "Unable to read CurseForge modpack manifest.json", e)
            manifest = null
        }
    }

    override fun execute() {
        if (manifest == null) return
        for (f in manifest!!.files) {
            if (f.fileName.isBlank())
                throw GameException("Unable to download mod, cannot continue")
            val file = run.resolve("mods").resolve(f.fileName)
            if (!file.exists())
                dependencies += FileDownloadTask(f.url, file, proxy = proxy)
        }
    }

}