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

import org.jackhuang.hmcl.mod.CurseForgeModpackCompletionTask
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Scheduler

object LauncherHelper {
    fun launch() {
        val profile = Settings.selectedProfile
        val repository = profile.repository
        val dependency = profile.dependency
        val account = Settings.selectedAccount ?: throw IllegalStateException("No account here")
        val version = repository.getVersion(profile.selectedVersion)
        val launcher = HMCLGameLauncher(
                repository = repository,
                versionId = profile.selectedVersion,
                options = profile.getVersionSetting(profile.selectedVersion).toLaunchOptions(profile.gameDir),
                account = account.logIn(Settings.proxy)
        )

        dependency.checkGameCompletionAsync(version)
                .then(CurseForgeModpackCompletionTask(dependency, profile.selectedVersion))
                .then(launcher.launchAsync())
                .subscribe(Scheduler.JAVAFX) { println("lalala") }
    }
}