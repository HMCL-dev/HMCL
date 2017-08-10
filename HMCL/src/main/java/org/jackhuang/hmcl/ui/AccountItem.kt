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
import com.jfoenix.controls.JFXRadioButton
import com.jfoenix.effects.JFXDepthManager
import javafx.beans.binding.Bindings
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import java.util.concurrent.Callable

class AccountItem(i: Int, width: Double, height: Double, group: ToggleGroup) : StackPane() {
    @FXML lateinit var icon: Pane
    @FXML lateinit var content: VBox
    @FXML lateinit var header: StackPane
    @FXML lateinit var body: StackPane
    @FXML lateinit var btnDelete: JFXButton
    @FXML lateinit var lblUser: Label
    @FXML lateinit var chkSelected: JFXRadioButton
    @FXML lateinit var lblType: Label

    init {
        loadFXML("/assets/fxml/account-item.fxml")

        JFXDepthManager.setDepth(this, 1)

        chkSelected.toggleGroup = group
        btnDelete.graphic = SVG.delete("white", 15.0, 15.0)

        // create content
        val headerColor = getDefaultColor(i % 12)
        header.style = "-fx-background-radius: 5 5 0 0; -fx-background-color: " + headerColor
        body.minHeight = Math.random() * 20 + 50

        // create image view
        icon.translateYProperty().bind(Bindings.createDoubleBinding(Callable { header.boundsInParent.height - icon.height / 2 }, header.boundsInParentProperty(), icon.heightProperty()))
    }

    private fun getDefaultColor(i: Int): String {
        var color = "#FFFFFF"
        when (i) {
            0 -> color = "#8F3F7E"
            1 -> color = "#B5305F"
            2 -> color = "#CE584A"
            3 -> color = "#DB8D5C"
            4 -> color = "#DA854E"
            5 -> color = "#E9AB44"
            6 -> color = "#FEE435"
            7 -> color = "#99C286"
            8 -> color = "#01A05E"
            9 -> color = "#4A8895"
            10 -> color = "#16669B"
            11 -> color = "#2F65A5"
            12 -> color = "#4E6A9C"
            else -> {
            }
        }
        return color
    }
}