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
import com.jfoenix.controls.JFXRadioButton
import javafx.beans.binding.Bindings
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ToggleGroup
import javafx.scene.effect.BlurType
import javafx.scene.effect.DropShadow
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import java.util.concurrent.Callable

class VersionItem(i: Int, group: ToggleGroup) : StackPane() {
    @FXML lateinit var icon: Pane
    @FXML lateinit var content: VBox
    @FXML lateinit var header: StackPane
    @FXML lateinit var body: StackPane
    @FXML lateinit var btnDelete: JFXButton
    @FXML lateinit var btnSettings: JFXButton
    @FXML lateinit var lblVersionName: Label
    @FXML lateinit var chkSelected: JFXRadioButton
    @FXML lateinit var lblGameVersion: Label
    @FXML lateinit var iconView: ImageView

    init {
        loadFXML("/assets/fxml/version-item.fxml")

        limitWidth(160.0)
        limitHeight(156.0)

        effect = DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 5.0, 0.12, -1.0, 1.0)

        chkSelected.toggleGroup = group
        btnSettings.graphic = SVG.gear("black", 15.0, 15.0)
        btnDelete.graphic = SVG.delete("black", 15.0, 15.0)

        // create content
        val headerColor = getDefaultColor(i % 12)
        header.style = "-fx-background-radius: 2 2 0 0; -fx-background-color: " + headerColor

        // create image view
        icon.translateYProperty().bind(Bindings.createDoubleBinding(Callable { header.boundsInParent.height - icon.height }, header.boundsInParentProperty(), icon.heightProperty()))
        iconView.limitSize(32.0, 32.0)
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