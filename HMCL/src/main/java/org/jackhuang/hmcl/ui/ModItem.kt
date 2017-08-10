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

import com.jfoenix.controls.JFXCheckBox
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import org.jackhuang.hmcl.mod.ModInfo

class ModItem(info: ModInfo, private val deleteCallback: () -> Unit) : BorderPane() {
    @FXML lateinit var lblModFileName: Label
    @FXML lateinit var lblModAuthor: Label
    @FXML lateinit var chkEnabled: JFXCheckBox

    init {
        loadFXML("/assets/fxml/mod-item.fxml")

        lblModFileName.text = info.fileName
        lblModAuthor.text = "${info.name}, Version: ${info.version}, Game Version: ${info.mcversion}, Authors: ${info.authors}"
        chkEnabled.isSelected = info.isActive
        chkEnabled.selectedProperty().addListener { _, _, newValue ->
            info.activeProperty.set(newValue)
        }
    }

    fun onDelete() {
        deleteCallback()
    }
}