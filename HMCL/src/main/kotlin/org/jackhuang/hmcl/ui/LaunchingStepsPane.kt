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

import com.jfoenix.controls.JFXProgressBar
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane

class LaunchingStepsPane(): StackPane() {
    @FXML lateinit var pgsTasks: JFXProgressBar
    @FXML lateinit var lblCurrentState: Label
    @FXML lateinit var lblSteps: Label
    init {
        loadFXML("/assets/fxml/launching-steps.fxml")

        limitHeight(200.0)
        limitWidth(400.0)
    }

}