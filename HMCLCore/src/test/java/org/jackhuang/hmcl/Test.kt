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
package org.jackhuang.hmcl

import org.jackhuang.hmcl.auth.OfflineAccount
import org.jackhuang.hmcl.download.DefaultDependencyManager
import org.jackhuang.hmcl.download.liteloader.LiteLoaderVersionList
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.download.MojangDownloadProvider
import org.jackhuang.hmcl.game.DefaultGameRepository
import org.jackhuang.hmcl.launch.DefaultLauncher
import org.jackhuang.hmcl.game.LaunchOptions
import org.jackhuang.hmcl.game.minecraftVersion
import org.jackhuang.hmcl.launch.ProcessListener
import org.jackhuang.hmcl.util.makeCommand
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskListener
import org.jackhuang.hmcl.util.Log4jLevel
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

class Test {
    val ss = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
    val repository = DefaultGameRepository(File(".minecraft").absoluteFile)
    val dependency = DefaultDependencyManager(
            repository = repository,
            downloadProvider = MojangDownloadProvider,
            proxy = ss)

    init {
        repository.refreshVersions()
    }

    fun launch() {
        val launcher = DefaultLauncher(
                repository = repository,
                versionId = "test",
                account = OfflineAccount.fromUsername("player007").logIn(),
                options = LaunchOptions(gameDir = repository.baseDirectory),
                listener = object : ProcessListener {
                    override fun onLog(log: String, level: Log4jLevel) {
                        println(log)
                    }

                    override fun onExit(exitCode: Int, exitType: ProcessListener.ExitType) {
                        println("Process exited then exit code $exitCode")
                    }

                },
                isDaemon = false
        )
        println(makeCommand(launcher.rawCommandLine))
        launcher.launch()
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            return
        }
    }

    fun downloadNewVersion() {
        val thread = Thread.currentThread()
        dependency.gameBuilder()
                .name("test")
                .gameVersion("1.12")
                .version("forge", "14.21.1.2426")
                .version("liteloader", "1.12-SNAPSHOT-4")
                .version("optifine", "HD_U_C4")
                .buildAsync().executor().apply {
            taskListener = taskListener(thread)
        }.start()
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            return
        }
    }

    fun completeGame() {
        val thread = Thread.currentThread()
        val version = repository.getVersion("test").resolve(repository)
        dependency.checkGameCompletionAsync(version).executor().apply {
            taskListener = taskListener(thread)
        }.start()
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            return
        }
    }

    fun installForge() {
        val thread = Thread.currentThread()
        val version = repository.getVersion("test").resolve(repository)
        val minecraftVersion = minecraftVersion(repository.getVersionJar(version)) ?: ""
        // optifine HD_U_C4
        // forge 14.21.1.2426
        // liteloader 1.12-SNAPSHOT-4
        dependency.installLibraryAsync(minecraftVersion, version, "liteloader", "1.12-SNAPSHOT-4").executor().apply {
            taskListener = taskListener(thread)
        }.start()
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            return
        }
    }

    fun refreshAsync() {
        val thread = Thread.currentThread()
        LiteLoaderVersionList.refreshAsync(BMCLAPIDownloadProvider).executor().apply {
            taskListener = taskListener(thread)
        }.start()
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            return
        }
    }

    fun taskListener(thread: Thread) = object : TaskListener {
        override fun onReady(task: Task) {
        }

        override fun onFinished(task: Task) {
        }

        override fun onFailed(task: Task, throwable: Throwable) {
        }

        override fun onTerminate() {
            thread.interrupt()
        }

    }
}