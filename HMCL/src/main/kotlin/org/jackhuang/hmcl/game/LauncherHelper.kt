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

import org.jackhuang.hmcl.Main
import org.jackhuang.hmcl.auth.AuthInfo
import org.jackhuang.hmcl.auth.AuthenticationException
import org.jackhuang.hmcl.launch.DefaultLauncher
import org.jackhuang.hmcl.launch.ProcessListener
import org.jackhuang.hmcl.mod.CurseForgeModpackCompletionTask
import org.jackhuang.hmcl.setting.LauncherVisibility
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.task.*
import org.jackhuang.hmcl.ui.*
import org.jackhuang.hmcl.util.JavaProcess
import org.jackhuang.hmcl.util.Log4jLevel
import java.util.concurrent.ConcurrentSkipListSet


object LauncherHelper {
    private val launchingStepsPane = LaunchingStepsPane()
    val PROCESS = ConcurrentSkipListSet<JavaProcess>()

    fun launch() {
        val profile = Settings.selectedProfile
        val repository = profile.repository
        val dependency = profile.dependency
        val account = Settings.selectedAccount ?: throw IllegalStateException("No account here")
        val version = repository.getVersion(profile.selectedVersion)
        val setting = profile.getVersionSetting(profile.selectedVersion)
        var finished = 0

        Controllers.dialog(launchingStepsPane)
        task(Scheduler.JAVAFX) { emitStatus(LoadingState.DEPENDENCIES) }
                .then(dependency.checkGameCompletionAsync(version))

                .then(task(Scheduler.JAVAFX) { emitStatus(LoadingState.MODS) })
                .then(CurseForgeModpackCompletionTask(dependency, profile.selectedVersion))

                .then(task(Scheduler.JAVAFX) { emitStatus(LoadingState.LOGIN) })
                .then(task {
                    try {
                        it["account"] = account.logIn(Settings.proxy)
                    } catch (e: AuthenticationException) {
                        it["account"] = DialogController.logIn(account)
                        runOnUiThread { Controllers.dialog(launchingStepsPane) }
                    }
                })

                .then(task(Scheduler.JAVAFX) { emitStatus(LoadingState.LAUNCHING) })
                .then(task {
                    it["launcher"] = HMCLGameLauncher(
                            repository = repository,
                            versionId = profile.selectedVersion,
                            options = setting.toLaunchOptions(profile.gameDir),
                            listener = HMCLProcessListener(it["account"], setting),
                            account = it["account"]
                    )
                })
                .then { it.get<DefaultLauncher>("launcher").launchAsync() }
                .then(task {
                    if (setting.launcherVisibility == LauncherVisibility.CLOSE)
                        Main.stop()
                })

                .executor()
                .apply {
                    taskListener = object : TaskListener {
                        override fun onFinished(task: Task) {
                            ++finished
                            runOnUiThread { launchingStepsPane.pgsTasks.progress = 1.0 * finished / totTask.get() }
                        }

                        override fun onTerminate() {
                            runOnUiThread { Controllers.closeDialog() }
                        }
                    }
                }.start()
    }

    fun emitStatus(state: LoadingState) {
        if (state == LoadingState.DONE) {
            Controllers.closeDialog()
        }

        launchingStepsPane.lblCurrentState.text = state.toString()
        launchingStepsPane.lblSteps.text = "${state.ordinal + 1} / ${LoadingState.values().size}"
    }

    /**
     * The managed process listener.
     * Guarantee that one [JavaProcess], one [HMCLProcessListener].
     * Because every time we launched a game, we generates a new [HMCLProcessListener]
     */
    class HMCLProcessListener(authInfo: AuthInfo?, private val setting: VersionSetting) : ProcessListener {
        val forbiddenTokens: List<Pair<String, String>> = if (authInfo == null) emptyList() else
            listOf(
                    authInfo.authToken to "<access token>",
                    authInfo.userId to "<uuid>",
                    authInfo.username to "<player>"
            )
        private val launcherVisibility = setting.launcherVisibility
        private lateinit var process: JavaProcess
        private var lwjgl = false
        private var logWindow: LogWindow? = null
        override fun setProcess(process: JavaProcess) {
            this.process = process

            if (setting.showLogs) {
                runOnUiThread { logWindow = LogWindow(); logWindow?.show() }
            }
        }

        override fun onLog(log: String, level: Log4jLevel) {
            if (level.lessOrEqual(Log4jLevel.ERROR))
                System.err.print(log)
            else
                System.out.print(log)

            runOnUiThread { logWindow?.logLine(log, level) }

            if (!lwjgl && log.contains("LWJGL Version: ")) {
                lwjgl = true
                when (launcherVisibility) {
                    LauncherVisibility.HIDE_AND_REOPEN -> {
                        runOnUiThread {
                            Controllers.stage.hide()
                            emitStatus(LoadingState.DONE)
                        }
                    }
                    LauncherVisibility.CLOSE -> {
                        throw Error("Never come to here")
                    }
                    LauncherVisibility.KEEP -> {
                        // No operations here.
                    }
                    LauncherVisibility.HIDE -> {
                        runOnUiThread {
                            Controllers.stage.close()
                            emitStatus(LoadingState.DONE)
                        }
                    }
                }
            }
        }

        override fun onExit(exitCode: Int, exitType: ProcessListener.ExitType) {
            checkExit(launcherVisibility)
        }

    }

    private fun checkExit(launcherVisibility: LauncherVisibility) {
        when (launcherVisibility) {
            LauncherVisibility.HIDE_AND_REOPEN -> runOnUiThread { Controllers.stage.show() }
            LauncherVisibility.KEEP -> {}
            LauncherVisibility.CLOSE -> {}
            LauncherVisibility.HIDE -> Main.stop()
        }
    }

    fun stopManagedProcess() {
        PROCESS.forEach(JavaProcess::stop)
    }

    enum class LoadingState {
        DEPENDENCIES,
        MODS,
        LOGIN,
        LAUNCHING,
        DONE
    }
}