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

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXCheckBox
import com.jfoenix.effects.JFXDepthManager
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import org.jackhuang.hmcl.mod.ModInfo
import org.jackhuang.hmcl.util.onChange

class ModItem(info: ModInfo, private val deleteCallback: (ModItem) -> Unit) : BorderPane() {
    val lblModFileName = Label().apply { style = "-fx-font-size: 15;" }
    val lblModAuthor = Label().apply { style = "-fx-font-size: 10;" }
    val chkEnabled = JFXCheckBox().apply { BorderPane.setAlignment(this, Pos.CENTER) }

    init {
        left = chkEnabled

        center = VBox().apply {
            BorderPane.setAlignment(this, Pos.CENTER)

            children += lblModFileName
            children += lblModAuthor
        }

        right = JFXButton().apply {
            setOnMouseClicked { onDelete() }
            styleClass += "toggle-icon4"

            BorderPane.setAlignment(this, Pos.CENTER)
            graphic = SVG.close("black", 15.0, 15.0)
        }

        style = "-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;"
        JFXDepthManager.setDepth(this, 1)
        lblModFileName.text = info.fileName
        lblModAuthor.text = "${info.name}, Version: ${info.version}, Game Version: ${info.gameVersion}, Authors: ${info.authors}"
        chkEnabled.isSelected = info.isActive
        chkEnabled.selectedProperty().onChange {
            info.activeProperty().set(it)
        }
    }

    fun onDelete() {
        deleteCallback(this)
    }
}