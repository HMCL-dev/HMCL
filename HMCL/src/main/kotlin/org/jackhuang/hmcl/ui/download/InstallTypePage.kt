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
package org.jackhuang.hmcl.ui.download

import com.jfoenix.controls.JFXListView
import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.ui.loadFXML
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage

class InstallTypePage(private val controller: WizardController): StackPane(), WizardPage {

    @FXML lateinit var list: JFXListView<Any>

    init {
        loadFXML("/assets/fxml/download/dltype.fxml")

        list.setOnMouseClicked {
            controller.settings[INSTALL_TYPE] = list.selectionModel.selectedIndex
            controller.onNext()
        }
    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        settings.remove(INSTALL_TYPE)
    }

    override fun getTitle() = "Select an operation"

    companion object {
        const val INSTALL_TYPE: String = "INSTALL_TYPE"
    }
}