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
import com.jfoenix.controls.JFXTextField
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.ui.loadFXML
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage

class InstallersPage(private val controller: WizardController, private val downloadProvider: DownloadProvider): StackPane(), WizardPage {

    @FXML lateinit var list: JFXListView<VersionsPageItem>
    @FXML lateinit var lblGameVersion: Label
    @FXML lateinit var lblForge: Label
    @FXML lateinit var lblLiteLoader: Label
    @FXML lateinit var lblOptiFine: Label
    @FXML lateinit var txtName: JFXTextField

    init {
        loadFXML("/assets/fxml/download/installers.fxml")

        val gameVersion = controller.settings["game"] as String
        txtName.text = gameVersion

        list.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            controller.settings[INSTALLER_TYPE] = newValue
            controller.onNext(when (newValue){
                0 -> VersionsPage(controller, gameVersion, downloadProvider, "forge") { controller.onPrev(false) }
                1 -> VersionsPage(controller, gameVersion, downloadProvider, "liteloader") { controller.onPrev(false) }
                2 -> VersionsPage(controller, gameVersion, downloadProvider, "optifine") { controller.onPrev(false) }
                else -> throw IllegalStateException()
            })
        }
    }

    override val title: String
        get() = "Choose a game version"

    override fun onNavigate(settings: MutableMap<String, Any>) {
        lblGameVersion.text = "Current Game Version: ${controller.settings["game"]}"
        if (controller.settings.containsKey("forge"))
            lblForge.text = "Forge Versoin: ${controller.settings["forge"]}"
        else
            lblForge.text = "Forge not installed"

        if (controller.settings.containsKey("liteloader"))
            lblLiteLoader.text = "LiteLoader Versoin: ${controller.settings["liteloader"]}"
        else
            lblLiteLoader.text = "LiteLoader not installed"

        if (controller.settings.containsKey("optifine"))
            lblOptiFine.text = "OptiFine Versoin: ${controller.settings["optifine"]}"
        else
            lblOptiFine.text = "OptiFine not installed"
    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        settings.remove(INSTALLER_TYPE)
    }

    fun onInstall() {
        controller.settings["name"] = txtName.text
        controller.onFinish()
    }

    companion object {
        val INSTALLER_TYPE = "INSTALLER_TYPE"
    }
}