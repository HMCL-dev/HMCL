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

import com.google.gson.annotations.SerializedName
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jackhuang.hmcl.download.DefaultDependencyManager
import org.jackhuang.hmcl.download.game.VersionJSONSaveTask
import org.jackhuang.hmcl.game.Library
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*

class InstancePatch @JvmOverloads constructor(
        val name: String = "",
        val version: String = "",
        @SerializedName("mcVersion")
        val gameVersion: String = "",
        val mainClass: String = "",
        val fileId: String = "",
        @SerializedName("+tweakers")
        val tweakers: List<String> = emptyList(),
        @SerializedName("+libraries")
        val libraries: List<Library> = emptyList()
)

class InstanceConfiguration(defaultName: String, contentStream: InputStream) {
    /**
     * The instance's name
     */
    val name: String // name

    /**
     * The game version of the instance
     */
    val gameVersion: String // IntendedVersion

    /**
     * The permanent generation size of JVM.
     */
    val permGen: Int? // PermGen

    /**
     * The command to launch JVM.
     */
    val wrapperCommand: String? // WrapperCommand

    /**
     * The command that will be executed before game launches.
     */
    val preLaunchCommand: String? // PreLaunchCommand

    /**
     * The command that will be executed after game exits.
     */
    val postExitCommand: String? // PostExitCommand

    /**
     * The description of the instance
     */
    val notes: String? // notes

    /**
     * JVM installation location
     */
    val javaPath: String? // JavaPath

    /**
     * The JVM's arguments
     */
    val jvmArgs: String? // JvmArgs

    /**
     * True if Minecraft will start in fullscreen mode.
     */
    val fullscreen: Boolean // LaunchMaximized

    /**
     * The initial width of the game window.
     */
    val width: Int? // MinecraftWinWidth

    /**
     * The initial height of the game window.
     */
    val height: Int? // MinecraftWinHeight

    /**
     * The maximum memory that JVM can allocate.
     */
    val maxMemory: Int? // MaxMemAlloc

    /**
     * The minimum memory that JVM can allocate.
     */
    val minMemory: Int? // MinMemAlloc

    /**
     * True if show the console window when game launches.
     */
    val showConsole: Boolean // ShowConsole

    /**
     * True if show the console window when game crashes.
     */
    val showConsoleOnError: Boolean // ShowConsoleOnError

    /**
     * True if closes the console window when game stops.
     */
    val autoCloseConsole: Boolean // AutoCloseConsole

    /**
     * True if [maxMemory], [minMemory], [permGen] will come info force.
     */
    val overrideMemory: Boolean // OverrideMemory

    /**
     * True if [javaPath] will come info force.
     */
    val overrideJavaLocation: Boolean // OverrideJavaLocation

    /**
     * True if [jvmArgs] will come info force.
     */
    val overrideJavaArgs: Boolean // OverrideJavaArgs

    /**
     * True if [showConsole], [showConsoleOnError], [autoCloseConsole] will come into force.
     */
    val overrideConsole: Boolean // OverrideConsole

    /**
     * True if [preLaunchCommand], [postExitCommand], [wrapperCommand] will come into force.
     */
    val overrideCommands: Boolean // OverrideCommands

    /**
     * True if [height], [width], [fullscreen] will come into force.
     */
    val overrideWindow: Boolean // OverrideWindow

    init {
        val p = Properties()
        p.load(contentStream)

        autoCloseConsole = p.getProperty("AutoCloseConsole") == "true"
        gameVersion = p.getProperty("IntendedVersion")
        javaPath = p.getProperty("JavaPath")
        jvmArgs = p.getProperty("JvmArgs")
        fullscreen = p.getProperty("LaunchMaximized") == "true"
        maxMemory = p.getProperty("MaxMemAlloc")?.toIntOrNull()
        minMemory = p.getProperty("MinMemAlloc")?.toIntOrNull()
        height = p.getProperty("MinecraftWinHeight")?.toIntOrNull()
        width = p.getProperty("MinecraftWinWidth")?.toIntOrNull()
        overrideCommands = p.getProperty("OverrideCommands") == "true"
        overrideConsole = p.getProperty("OverrideConsole") == "true"
        overrideJavaArgs = p.getProperty("OverrideJavaArgs") == "true"
        overrideJavaLocation = p.getProperty("OverrideJavaLocation") == "true"
        overrideMemory = p.getProperty("OverrideMemory") == "true"
        overrideWindow = p.getProperty("OverrideWindow") == "true"
        permGen = p.getProperty("PermGen")?.toIntOrNull()
        postExitCommand = p.getProperty("PostExitCommand")
        preLaunchCommand = p.getProperty("PreLaunchCommand")
        showConsole = p.getProperty("ShowConsole") == "true"
        showConsoleOnError = p.getProperty("ShowConsoleOnError") == "true"
        wrapperCommand = p.getProperty("WrapperCommand")
        name = defaultName
        notes = p.getProperty("notes") ?: ""
    }
}

fun readMMCModpackManifest(f: File): Modpack {
    ZipFile(f).use { zipFile ->
        val firstEntry = zipFile.entries.nextElement()
        val name = firstEntry.name.substringBefore("/")
        val entry = zipFile.getEntry("$name/instance.cfg") ?: throw IOException("`instance.cfg` not found, $f is not a valid MultiMC modpack.")
        val cfg = InstanceConfiguration(name, zipFile.getInputStream(entry))
        return Modpack(
                name = cfg.name,
                version = "",
                author = "",
                gameVersion = cfg.gameVersion,
                description = cfg.notes,
                manifest = cfg
        )
    }
}

class MMCModpackInstallTask(private val dependencyManager: DefaultDependencyManager, private val zipFile: File, private val manifest: InstanceConfiguration, private val name: String): Task() {
    private val repository = dependencyManager.repository
    override val dependencies = mutableListOf<Task>()
    override val dependents = mutableListOf<Task>()

    init {
        check(!repository.hasVersion(name), { "Version $name already exists." })
        dependents += dependencyManager.gameBuilder().name(name).gameVersion(manifest.gameVersion).buildAsync()

        onDone += { event -> if (event.failed) repository.removeVersionFromDisk(name) }
    }

    private val run = repository.getRunDirectory(name)

    override fun execute() {
        var version = repository.readVersionJson(name)!!
        zipFile.uncompressTo(run, subDirectory = "${manifest.name}/minecraft/", ignoreExistentFile = false, allowStoredEntriesWithDataDescriptor = true)

        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries) {
                // ensure that this entry is in folder 'patches' and is a json file.
                if (!entry.isDirectory && entry.name.startsWith("${manifest.name}/patches/") && entry.name.endsWith(".json")) {
                    val patch = GSON.fromJson<InstancePatch>(zip.getInputStream(entry).readFullyAsString())!!
                    val args = StringBuilder(version.minecraftArguments)
                    for (arg in patch.tweakers)
                        args.append(" --tweakClass ").append(arg)
                    version = version.copy(
                            libraries = merge(version.libraries, patch.libraries),
                            mainClass = patch.mainClass,
                            minecraftArguments = args.toString()
                    )
                }
            }
        }

        dependencies += VersionJSONSaveTask(repository, version)
    }
}