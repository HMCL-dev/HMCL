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

import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.runOnUiThread
import org.jackhuang.hmcl.util.Constants
import org.jackhuang.hmcl.util.Logging.LOG
import org.jackhuang.hmcl.util.NetworkUtils
import org.jackhuang.hmcl.util.OperatingSystem
import java.io.File
import java.util.logging.Level

fun i18n(key: String): String {
    try {
        return Main.RESOURCE_BUNDLE.getString(key)
    } catch (e: Exception) {
        LOG.log(Level.SEVERE, "Cannot find key $key in resource bundle", e)
        return key
    }
}

class Main : Application() {

    override fun start(stage: Stage) {
        // When launcher visibility is set to "hide and reopen" without [Platform.implicitExit] = false,
        // Stage.show() cannot work again because JavaFX Toolkit have already shut down.
        Platform.setImplicitExit(false)

        Controllers.initialize(stage)

        stage.isResizable = false
        stage.scene = Controllers.scene
        stage.show()
    }

    companion object {

        @JvmField val VERSION = "@HELLO_MINECRAFT_LAUNCHER_VERSION_FOR_GRADLE_REPLACING@"
        @JvmField val NAME = "HMCL"
        @JvmField val TITLE = "$NAME $VERSION"
        @JvmField val APPDATA = getWorkingDirectory("hmcl")

        @JvmStatic
        fun main(args: Array<String>) {
            NetworkUtils.setUserAgentSupplier { "Hello Minecraft! Launcher" }
            Constants.UI_THREAD_SCHEDULER = Constants.JAVAFX_UI_THREAD_SCHEDULER;

            launch(Main::class.java, *args)
        }

        fun getWorkingDirectory(folder: String): File {
            val userhome = System.getProperty("user.home", ".")
            return when (OperatingSystem.CURRENT_OS) {
                OperatingSystem.LINUX -> File(userhome, ".$folder/")
                OperatingSystem.WINDOWS -> {
                    val appdata: String? = System.getenv("APPDATA")
                    File(appdata ?: userhome, ".$folder/")
                }
                OperatingSystem.OSX -> File(userhome, "Library/Application Support/" + folder)
                else -> File(userhome, "$folder/")
            }
        }

        fun getMinecraftDirectory(): File = getWorkingDirectory("minecraft")

        fun stop() = runOnUiThread {
            stopWithoutJavaFXPlatform()
            Platform.exit()
        }

        fun stopWithoutJavaFXPlatform() = runOnUiThread {
            Controllers.stage.close()
            Schedulers.shutdown()
        }

        val RESOURCE_BUNDLE = Settings.locale.resourceBundle
    }
}