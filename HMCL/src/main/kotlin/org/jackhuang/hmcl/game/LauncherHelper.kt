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

import org.jackhuang.hmcl.auth.AuthenticationException
import org.jackhuang.hmcl.launch.DefaultLauncher
import org.jackhuang.hmcl.mod.CurseForgeModpackCompletionTask
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.*
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.DialogController
import org.jackhuang.hmcl.ui.LaunchingStepsPane
import org.jackhuang.hmcl.ui.runOnUiThread


object LauncherHelper {
    val launchingStepsPane = LaunchingStepsPane()

    fun launch() {
        val profile = Settings.selectedProfile
        val repository = profile.repository
        val dependency = profile.dependency
        val account = Settings.selectedAccount ?: throw IllegalStateException("No account here")
        val version = repository.getVersion(profile.selectedVersion)
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
                            options = profile.getVersionSetting(profile.selectedVersion).toLaunchOptions(profile.gameDir),
                            account = it["account"]
                    )
                })
                .then { it.get<DefaultLauncher>("launcher").launchAsync() }

                .then(task(Scheduler.JAVAFX) { emitStatus(LoadingState.DONE) })
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

                        override fun end() {
                            runOnUiThread { Controllers.closeDialog() }
                        }
                    }
                }.start()
    }

    fun emitStatus(state: LoadingState) {
        launchingStepsPane.lblCurrentState.text = state.toString()
        launchingStepsPane.lblSteps.text = "${state.ordinal + 1} / ${LoadingState.values().size}"
    }

    enum class LoadingState {
        DEPENDENCIES,
        MODS,
        LOGIN,
        LAUNCHING,
        DONE
    }
}