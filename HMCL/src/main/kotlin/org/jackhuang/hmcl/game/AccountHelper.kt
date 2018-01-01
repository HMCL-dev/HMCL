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

import javafx.geometry.Rectangle2D
import javafx.scene.image.Image
import org.jackhuang.hmcl.Main
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.FileDownloadTask
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.ui.DEFAULT_ICON
import org.jackhuang.hmcl.ui.DialogController
import org.jackhuang.hmcl.util.toURL
import java.net.Proxy

object AccountHelper {
    val SKIN_DIR = Main.APPDATA.resolve("skins")

    fun loadSkins(proxy: Proxy = Settings.proxy) {
        for (account in Settings.getAccounts().values) {
            if (account is YggdrasilAccount) {
                SkinLoadTask(account, proxy, false).start()
            }
        }
    }

    fun loadSkinAsync(account: YggdrasilAccount, proxy: Proxy = Settings.proxy): Task =
            SkinLoadTask(account, proxy, false)

    fun refreshSkinAsync(account: YggdrasilAccount, proxy: Proxy = Settings.proxy): Task =
            SkinLoadTask(account, proxy, true)

    private class SkinLoadTask(val account: YggdrasilAccount, val proxy: Proxy, val refresh: Boolean = false): Task() {

        override fun getScheduler() = Schedulers.io()
        private val dependencies = mutableListOf<Task>()
        override fun getDependencies() = dependencies

        override fun execute() {
            if (account.canLogIn() && (account.selectedProfile == null || refresh))
                DialogController.logIn(account)
            val profile = account.selectedProfile ?: return
            val name = profile.name ?: return
            val url = "http://skins.minecraft.net/MinecraftSkins/$name.png"
            val file = getSkinFile(name)
            if (!refresh && file.exists())
                return
            dependencies += FileDownloadTask(url.toURL(), file, proxy)
        }
    }

    private fun getSkinFile(name: String) = SKIN_DIR.resolve("$name.png")

    fun getSkin(account: YggdrasilAccount, scaleRatio: Double = 1.0): Image {
        if (account.selectedProfile == null) return DEFAULT_ICON
        val name = account.selectedProfile?.name ?: return DEFAULT_ICON
        val file = getSkinFile(name)
        if (file.exists()) {
            val original = Image("file:" + file.absolutePath)
            return Image("file:" + file.absolutePath, original.width * scaleRatio, original.height * scaleRatio, false, false)
        }
        else return DEFAULT_ICON
    }

    fun getViewport(scaleRatio: Double): Rectangle2D {
        val size = 8.0 * scaleRatio
        return Rectangle2D(size, size, size, size)
    }
}