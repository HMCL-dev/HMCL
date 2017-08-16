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
import javafx.beans.property.Property
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.SVG
import org.jackhuang.hmcl.util.*

class FileItem : BorderPane() {
    val nameProperty = SimpleStringProperty(this, "name")
    var name: String by nameProperty

    val titleProperty = SimpleStringProperty(this, "title")
    var title: String by titleProperty

    val tooltipProperty = SimpleStringProperty(this, "tooltip")
    var tooltip: String by tooltipProperty

    private lateinit var property: Property<String>

    private val x = Label()
    init {
        left = VBox().apply {
            children += Label().apply { textProperty().bind(nameProperty) }
            children += x.apply { style += "-fx-text-fill: gray;" }
        }

        right = JFXButton().apply {
            graphic = SVG.pencil("black", 15.0, 15.0)
            styleClass += "toggle-icon4"
            setOnMouseClicked {
                val chooser = DirectoryChooser()
                chooser.titleProperty().bind(titleProperty)
                val selectedDir = chooser.showDialog(Controllers.stage)
                if (selectedDir != null)
                    property.value = selectedDir.absolutePath
                chooser.titleProperty().unbind()
            }
        }

        Tooltip.install(this, Tooltip().apply {
            textProperty().bind(tooltipProperty)
        })
    }

    fun setProperty(property: Property<String>) {
        this.property = property
        x.textProperty().bind(property)
    }
}