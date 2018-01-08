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
package org.jackhuang.hmcl.ui

import com.jfoenix.controls.JFXDialog
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.Region
import javafx.stage.Stage
import org.jackhuang.hmcl.Main
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.util.JavaVersion
import org.jackhuang.hmcl.util.task

object Controllers {
    lateinit var scene: Scene private set
    lateinit var stage: Stage private set

    val mainPane = MainPage()
    val settingsPane by lazy { SettingsPage() }
    val versionPane by lazy { VersionPage() }

    lateinit var leftPaneController: LeftPaneController

    lateinit var decorator: Decorator

    fun initialize(stage: Stage) {
        this.stage = stage

        decorator = Decorator(stage, mainPane, Main.TITLE, max = false)
        decorator.showPage(null)
        leftPaneController = LeftPaneController(decorator.leftPane)

        Settings.onProfileLoading()
        task { JavaVersion.initialize() }.start()

        decorator.isCustomMaximize = false

        scene = Scene(decorator, 804.0, 521.0)
        scene.stylesheets.addAll(*stylesheets)
        stage.minWidth = 800.0
        stage.maxWidth = 800.0
        stage.maxHeight = 480.0
        stage.minHeight = 480.0

        stage.icons += Image("/assets/img/icon.png")
        stage.title = Main.TITLE
    }

    fun dialog(content: Region): JFXDialog {
        return decorator.showDialog(content)
    }

    fun dialog(text: String) {
        dialog(MessageDialogPane(text, decorator.dialog))
    }

    fun closeDialog() {
        decorator.dialog.close()
    }

    fun navigate(node: Node?) {
        decorator.showPage(node)
    }
}