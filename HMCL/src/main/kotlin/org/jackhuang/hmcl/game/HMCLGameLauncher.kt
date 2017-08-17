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
import org.jackhuang.hmcl.launch.DefaultLauncher
import org.jackhuang.hmcl.launch.ProcessListener

class HMCLGameLauncher(repository: GameRepository, versionId: String, account: AuthInfo, options: LaunchOptions, listener: ProcessListener? = null, isDaemon: Boolean = true)
    : DefaultLauncher(repository, versionId, account, options, listener, isDaemon) {

    override fun appendJvmArgs(res: MutableList<String>) {
        super.appendJvmArgs(res)

        res.add("-Dminecraft.launcher.version=" + Main.VERSION);
        res.add("-Dminecraft.launcher.brand=" + Main.NAME);
    }
}