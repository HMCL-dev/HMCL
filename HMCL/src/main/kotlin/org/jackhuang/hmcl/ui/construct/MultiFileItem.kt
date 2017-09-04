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
package org.jackhuang.hmcl.ui.construct

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXRadioButton
import com.jfoenix.controls.JFXTextField
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.SVG
import org.jackhuang.hmcl.ui.limitHeight
import org.jackhuang.hmcl.util.*

class MultiFileItem : ComponentList() {
    val customTextProperty = SimpleStringProperty(this, "customText", "Custom")
    var customText by customTextProperty

    val chooserTitleProperty = SimpleStringProperty(this, "chooserTitle", "Select a file")
    var chooserTitle by chooserTitleProperty

    val group = ToggleGroup()
    val txtCustom = JFXTextField().apply {
        BorderPane.setAlignment(this, Pos.CENTER_RIGHT)
    }
    val btnSelect = JFXButton().apply {
        graphic = SVG.folderOpen("black", 15.0, 15.0)
        setOnMouseClicked {
            // TODO
        }
    }
    val radioCustom = JFXRadioButton().apply {
        textProperty().bind(customTextProperty)
        toggleGroup = group
    }
    val custom = BorderPane().apply {
        left = radioCustom
        style = "-fx-padding: 3;"
        right = HBox().apply {
            spacing = 3.0
            children += txtCustom
            children += btnSelect
        }
        limitHeight(20.0)
    }

    val pane = VBox().apply {
        style = "-fx-padding: 0 0 10 0;"
        spacing = 8.0
        children += custom
    }

    init {
        addChildren(pane)

        txtCustom.disableProperty().bind(radioCustom.selectedProperty().not())
        btnSelect.disableProperty().bind(radioCustom.selectedProperty().not())
    }

    @JvmOverloads
    fun createChildren(title: String, subtitle: String = "", userData: Any? = null): Node {
        return BorderPane().apply {
            style = "-fx-padding: 3;"
            limitHeight(20.0)
            left = JFXRadioButton(title).apply {
                toggleGroup = group
                this.userData = userData
            }
            right = Label(subtitle).apply {
                styleClass += "subtitle-label"
                style += "-fx-font-size: 10;"
            }
        }
    }

    fun loadChildren(list: Collection<Node>) {
        pane.children.setAll(list)
        pane.children += custom
    }

    fun onExploreJavaDir() {
        val chooser = DirectoryChooser()
        chooser.title = i18n(chooserTitle)
        val selectedDir = chooser.showDialog(Controllers.stage)
        if (selectedDir != null)
            txtCustom.text = selectedDir.absolutePath
    }
}