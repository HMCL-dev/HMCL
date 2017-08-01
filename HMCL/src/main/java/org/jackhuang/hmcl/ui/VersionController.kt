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
import com.jfoenix.controls.JFXScrollPane
import com.jfoenix.controls.JFXTextField
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.setting.VersionSetting

class VersionController {
    @FXML
    lateinit var titleLabel: Label
    @FXML
    lateinit var backButton: JFXButton
    @FXML
    lateinit var scroll: ScrollPane
    @FXML
    lateinit var settingsPane: GridPane
    @FXML
    lateinit var txtGameDir: JFXTextField

    fun initialize() {
        Controllers.versionController = this

        settingsPane.columnConstraints.addAll(
                ColumnConstraints(),
                ColumnConstraints().apply { hgrow = Priority.ALWAYS },
                ColumnConstraints()
        )

        backButton.ripplerFill = Color.WHITE
        backButton.setOnMouseClicked {
            Controllers.navigate(null)
        }

        JFXScrollPane.smoothScrolling(scroll)
    }

    fun loadVersionSetting(id: String, version: VersionSetting) {
        titleLabel.text = id
    }

    fun onExploreJavaDir() {
        val chooser = DirectoryChooser()
        chooser.title = "Selecting Java Directory"
        val selectedDir = chooser.showDialog(Controllers.stage)
        if (selectedDir != null)
            txtGameDir.text = selectedDir.absolutePath
    }
}