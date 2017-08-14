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

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.setting.VersionSetting

class VersionListItem(val versionName: String, val gameVersion: String) : StackPane() {

    @FXML lateinit var lblVersionName: Label
    @FXML lateinit var lblGameVersion: Label
    @FXML lateinit var btnSettings: Button

    private var handler: () -> Unit = {}

    init {
        loadFXML("/assets/fxml/version-list-item.fxml")
        lblVersionName.text = versionName
        lblGameVersion.text = gameVersion
    }

    fun onSettings() {
        handler()
    }

    fun onSettingsButtonClicked(handler: () -> Unit) {
        this.handler = handler
    }
}