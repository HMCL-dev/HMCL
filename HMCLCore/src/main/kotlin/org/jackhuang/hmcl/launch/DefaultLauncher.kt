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
package org.jackhuang.hmcl.launch

import org.jackhuang.hmcl.auth.AuthInfo
import org.jackhuang.hmcl.game.*
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

/**
 * @param versionId The version to be launched.
 * @param account The user account
 * @param options The launching configuration
 */
open class DefaultLauncher(repository: GameRepository, versionId: String, account: AuthInfo, options: LaunchOptions, listener: ProcessListener? = null, isDaemon: Boolean = true)
    : Launcher(repository, versionId, account, options, listener, isDaemon) {

    protected val native: File by lazy { repository.getNativeDirectory(version.id) }

    init {
        if (version.inheritsFrom != null)
            throw IllegalArgumentException("Version must be resolved")
        if (version.minecraftArguments == null)
            throw NullPointerException("Version minecraft argument can not be null")
        if (version.mainClass == null || version.mainClass.isBlank())
            throw NullPointerException("Version main class can not be null")
    }

    protected open val defaultJVMArguments = Arguments.DEFAULT_JVM_ARGUMENTS
    protected open val defaultGameArguments = Arguments.DEFAULT_GAME_ARGUMENTS

    /**
     * Note: the [account] must have logged in when calling this property
     */
    override val rawCommandLine: List<String> by lazy {
        val res = LinkedList<String>()

        // Executable
        if (options.wrapper != null && options.wrapper.isNotBlank())
            res.add(options.wrapper)

        res.add(options.java.binary.toString())

        if (options.javaArgs != null && options.javaArgs.isNotBlank())
            res.addAll(options.javaArgs.tokenize())

        // JVM Args
        if (!options.noGeneratedJVMArgs) {
            appendJvmArgs(res)

            res.add("-Dminecraft.client.jar=${repository.getVersionJar(version)}")

            if (OS.CURRENT_OS == OS.OSX) {
                res.add("-Xdock:name=Minecraft ${version.id}")
                res.add("-Xdock:icon=" + repository.getAssetObject(version.id, version.actualAssetIndex.id, "icons/minecraft.icns").absolutePath)
            }

            val logging = version.logging
            if (logging != null) {
                val loggingInfo = logging[DownloadType.CLIENT]
                if (loggingInfo != null) {
                    val loggingFile = repository.getLoggingObject(version.id, version.actualAssetIndex.id, loggingInfo)
                    if (loggingFile.exists())
                        res.add(loggingInfo.argument.replace("\${path}", loggingFile.absolutePath))
                }
            }

            if (OS.CURRENT_OS != OS.WINDOWS)
                res.add("-Duser.home=${options.gameDir.parent}")

            if (options.java.version >= JavaVersion.JAVA_7)
                res.add("-XX:+UseG1GC")
            else
                res.add("-Xincgc")

            if (options.metaspace != null && options.metaspace > 0) {
                if (options.java.version < JavaVersion.JAVA_8)
                    res.add("-XX:PermSize=${options.metaspace}m")
                else
                    res.add("-XX:MetaspaceSize=${options.metaspace}m")
            }

            res.add("-XX:-UseAdaptiveSizePolicy")
            res.add("-XX:-OmitStackTraceInFastThrow")
            res.add("-Xmn128m")

            if (options.maxMemory != null && options.maxMemory > 0)
                res.add("-Xmx${options.maxMemory}m")
            if (options.minMemory != null && options.minMemory > 0)
                res.add("-Xms${options.minMemory}m")

            res.add("-Dfml.ignoreInvalidMinecraftCertificates=true")
            res.add("-Dfml.ignorePatchDiscrepancies=true")
        }

        val lateload = LinkedList<File>()
        val classpath = StringBuilder()
        for (library in version.libraries)
            if (library.appliesToCurrentEnvironment && !library.isNative) {
                val f = repository.getLibraryFile(version, library)
                if (f.exists() && f.isFile) {
                    if (library.lateload)
                        lateload += f
                    else classpath.append(f.absolutePath).append(OS.PATH_SEPARATOR)
                }
            }
        for (library in lateload)
            classpath.append(library.absolutePath).append(OS.PATH_SEPARATOR)

        val jar = repository.getVersionJar(version)
        if (!jar.exists() || !jar.isFile)
            throw GameException("Minecraft jar does not exist")
        classpath.append(jar.absolutePath)

        // Provided Minecraft arguments
        val gameAssets = repository.getActualAssetDirectory(version.id, version.actualAssetIndex.id)
        val configuration = getConfigurations()
        configuration["\${classpath}"] = classpath.toString()
        configuration["\${natives_directory}"] = native.absolutePath
        configuration["\${game_assets}"] = gameAssets.absolutePath
        configuration["\${assets_root}"] = gameAssets.absolutePath

        res.addAll(Arguments.parseArguments(version.arguments?.jvm ?: defaultJVMArguments, configuration))
        res.add(version.mainClass!!)

        val features = getFeatures()
        res.addAll(Arguments.parseArguments(version.arguments?.game ?: defaultGameArguments, configuration, features))
        res.addAll(Arguments.parseStringArguments(version.minecraftArguments?.tokenize()?.toList() ?: emptyList(), configuration))

        // Optional Minecraft arguments
        if (options.height != null && options.height != 0 && options.width != null && options.width != 0) {
            res.add("--height")
            res.add(options.height.toString())
            res.add("--width")
            res.add(options.width.toString())
        }

        if (options.serverIp != null && options.serverIp.isNotBlank()) {
            val args = options.serverIp.split(":")
            res.add("--server")
            res.add(args[0])
            res.add("--port")
            res.add(if (args.size > 1) args[1] else "25565")
        }

        if (options.fullscreen)
            res.add("--fullscreen")

        if (options.proxyHost != null && options.proxyHost.isNotBlank() &&
                options.proxyPort != null && options.proxyPort.isNotBlank()) {
            res.add("--proxyHost")
            res.add(options.proxyHost)
            res.add("--proxyPort")
            res.add(options.proxyPort)
            if (options.proxyUser != null && options.proxyUser.isNotBlank() &&
                    options.proxyPass != null && options.proxyPass.isNotBlank()) {
                res.add("--proxyUser")
                res.add(options.proxyUser)
                res.add("--proxyPass")
                res.add(options.proxyPass)
            }
        }

        if (options.minecraftArgs != null && options.minecraftArgs.isNotBlank())
            res.addAll(options.minecraftArgs.tokenize())

        res
    }

    /**
     * Do something here.
     * i.e.
     * -Dminecraft.launcher.version=<Your launcher name>
     * -Dminecraft.launcher.brand=<Your launcher version>
     * -Dlog4j.configurationFile=<Your custom log4j configuration
     */
    protected open fun appendJvmArgs(res: MutableList<String>) {}

    open fun decompressNatives() {
        version.libraries.filter { it.isNative }.forEach { library ->
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            repository.getLibraryFile(version, library).uncompressTo(
                    dest = native,
                    callback = if (library.extract == null) null
                    else library.extract!!::shouldExtract,
                    ignoreExistentFile = false)
        }
    }

    open fun getConfigurations(): MutableMap<String, String> = mutableMapOf(
            "\${auth_player_name}" to account.username,
            "\${auth_session}" to account.authToken,
            "\${auth_access_token}" to account.authToken,
            "\${auth_uuid}" to account.userId,
            "\${version_name}" to (options.versionName ?: version.id),
            "\${profile_name}" to (options.profileName ?: "Minecraft"),
            "\${version_type}" to version.type.id,
            "\${game_directory}" to repository.getRunDirectory(version.id).absolutePath,
            "\${user_type}" to account.userType.toString().toLowerCase(),
            "\${assets_index_name}" to version.actualAssetIndex.id,
            "\${user_properties}" to account.userProperties
    )

    open fun getFeatures(): MutableMap<String, Boolean> = mutableMapOf(
            "has_custom_resolution" to (options.height != null && options.height != 0 && options.width != null && options.width != 0)
    )

    override fun launch(): ManagedProcess {

        // To guarantee that when failed to generate code, we will not call precalled command
        val builder = ProcessBuilder(rawCommandLine)

        decompressNatives()

        if (options.precalledCommand != null && options.precalledCommand.isNotBlank()) {
            try {
                val process = Runtime.getRuntime().exec(options.precalledCommand)
                if (process.isAlive)
                    process.waitFor()
            } catch (e: IOException) {
                // TODO: alert precalledCommand is wrong.
                // rethrow InterruptedException
            }
        }

        builder.directory(repository.getRunDirectory(version.id))
                .environment().put("APPDATA", options.gameDir.absoluteFile.parent)
        val p = ManagedProcess(builder.start(), rawCommandLine)
        if (listener == null)
            startMonitors(p)
        else
            startMonitors(p, listener, isDaemon)
        return p
    }

    fun launchAsync(): TaskResult<ManagedProcess> {
        return object : TaskResult<ManagedProcess>() {
            override val id = LAUNCH_ASYNC_ID
            override fun execute() {
                result = launch()
            }
        }
    }

    override fun makeLaunchScript(file: String): File {
        val isWindows = OS.WINDOWS == OS.CURRENT_OS
        val scriptFile = File(file + (if (isWindows) ".bat" else ".sh"))
        if (!scriptFile.makeFile())
            throw IOException("Script file: $scriptFile cannot be created.")
        scriptFile.bufferedWriter().use { writer ->
            if (isWindows) {
                writer.write("@echo off")
                writer.newLine()
                writer.write("set APPDATA=" + options.gameDir.parent)
                writer.newLine()
                writer.write("cd /D %APPDATA%")
                writer.newLine()
            }
            if (options.precalledCommand != null && options.precalledCommand.isNotBlank()) {
                writer.write(options.precalledCommand)
                writer.newLine()
            }
            writer.write(makeCommand(rawCommandLine))
        }
        if (!scriptFile.setExecutable(true))
            throw IOException("Cannot make script file '$scriptFile' executable.")
        return scriptFile
    }

    private fun startMonitors(managedProcess: ManagedProcess) {
        managedProcess.relatedThreads += thread(name = "stdout-pump", isDaemon = true, block = StreamPump(managedProcess.process.inputStream)::run)
        managedProcess.relatedThreads += thread(name = "stderr-pump", isDaemon = true, block = StreamPump(managedProcess.process.errorStream)::run)
    }

    private fun startMonitors(managedProcess: ManagedProcess, processListener: ProcessListener, isDaemon: Boolean = true) {
        val enablesLoggingInfo = version.logging?.containsKey(DownloadType.CLIENT) ?: false
        if (enablesLoggingInfo)
            startMonitorsWithLoggingInfo(managedProcess, processListener, isDaemon)
        else
            startMonitorsWithoutLoggingInfo(managedProcess, processListener, isDaemon)
    }

    private fun startMonitorsWithLoggingInfo(managedProcess: ManagedProcess, processListener: ProcessListener, isDaemon: Boolean = true) {
        processListener.setProcess(managedProcess)
        val logHandler = Log4jHandler { line, level -> processListener.onLog(line, level); managedProcess.lines += line }.apply { start() }
        managedProcess.relatedThreads += logHandler
        val stdout = thread(name = "stdout-pump", isDaemon = isDaemon, block = StreamPump(managedProcess.process.inputStream, { logHandler.newLine(it) })::run)
        managedProcess.relatedThreads += stdout
        val stderr = thread(name = "stderr-pump", isDaemon = isDaemon, block = StreamPump(managedProcess.process.errorStream, { processListener.onLog(it + OS.LINE_SEPARATOR, Log4jLevel.ERROR); managedProcess.lines += it })::run)
        managedProcess.relatedThreads += stderr
        managedProcess.relatedThreads += thread(name = "exit-waiter", isDaemon = isDaemon, block = ExitWaiter(managedProcess, listOf(stdout, stderr), { exitCode, exitType -> logHandler.onStopped(); processListener.onExit(exitCode, exitType) })::run)
    }

    private fun startMonitorsWithoutLoggingInfo(managedProcess: ManagedProcess, processListener: ProcessListener, isDaemon: Boolean = true) {
        processListener.setProcess(managedProcess)
        val stdout = thread(name = "stdout-pump", isDaemon = isDaemon, block = StreamPump(managedProcess.process.inputStream, { processListener.onLog(it + OS.LINE_SEPARATOR, Log4jLevel.guessLevel(it) ?: Log4jLevel.INFO); managedProcess.lines += it })::run)
        managedProcess.relatedThreads += stdout
        val stderr = thread(name = "stderr-pump", isDaemon = isDaemon, block = StreamPump(managedProcess.process.errorStream, { processListener.onLog(it + OS.LINE_SEPARATOR, Log4jLevel.ERROR); managedProcess.lines += it })::run)
        managedProcess.relatedThreads += stderr
        managedProcess.relatedThreads += thread(name = "exit-waiter", isDaemon = isDaemon, block = ExitWaiter(managedProcess, listOf(stdout, stderr), { exitCode, exitType -> processListener.onExit(exitCode, exitType) })::run)
    }

    companion object {
        const val LAUNCH_ASYNC_ID = "process"
    }
}