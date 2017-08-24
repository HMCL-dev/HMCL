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

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXListView
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.game.GameRepository
import org.jackhuang.hmcl.ui.loadFXML
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage

class AdditionalInstallersPage(private val provider: InstallWizardProvider, private val controller: WizardController, private val repository: GameRepository, private val downloadProvider: DownloadProvider): StackPane(), WizardPage {

    @FXML lateinit var list: VBox
    @FXML lateinit var btnForge: JFXButton
    @FXML lateinit var btnLiteLoader: JFXButton
    @FXML lateinit var btnOptiFine: JFXButton
    @FXML lateinit var lblGameVersion: Label
    @FXML lateinit var lblVersionName: Label
    @FXML lateinit var lblForge: Label
    @FXML lateinit var lblLiteLoader: Label
    @FXML lateinit var lblOptiFine: Label
    @FXML lateinit var btnInstall: JFXButton

    init {
        loadFXML("/assets/fxml/download/additional-installers.fxml")

        lblGameVersion.text = provider.gameVersion
        lblVersionName.text = provider.version.id

        btnForge.setOnMouseClicked {
            controller.settings[INSTALLER_TYPE] = 0
            controller.onNext(VersionsPage(controller, provider.gameVersion, downloadProvider, "forge") { controller.onPrev(false) })
        }

        btnLiteLoader.setOnMouseClicked {
            controller.settings[INSTALLER_TYPE] = 1
            controller.onNext(VersionsPage(controller, provider.gameVersion, downloadProvider, "liteloader") { controller.onPrev(false) })
        }

        btnOptiFine.setOnMouseClicked {
            controller.settings[INSTALLER_TYPE] = 2
            controller.onNext(VersionsPage(controller, provider.gameVersion, downloadProvider, "optifine") { controller.onPrev(false) })
        }
    }

    override val title: String
        get() = "Choose a game version"

    override fun onNavigate(settings: MutableMap<String, Any>) {
        lblGameVersion.text = "Current Game Version: ${provider.gameVersion}"
        btnForge.isDisable = provider.forge != null
        if (provider.forge != null || controller.settings.containsKey("forge"))
            lblForge.text = "Forge Versoin: ${provider.forge ?: controller.settings["forge"]}"
        else
            lblForge.text = "Forge not installed"

        btnLiteLoader.isDisable = provider.liteloader != null
        if (provider.liteloader != null || controller.settings.containsKey("liteloader"))
            lblLiteLoader.text = "LiteLoader Versoin: ${provider.liteloader ?: controller.settings["liteloader"]}"
        else
            lblLiteLoader.text = "LiteLoader not installed"

        btnOptiFine.isDisable = provider.optifine != null
        if (provider.optifine != null || controller.settings.containsKey("optifine"))
            lblOptiFine.text = "OptiFine Versoin: ${provider.optifine ?: controller.settings["optifine"]}"
        else
            lblOptiFine.text = "OptiFine not installed"

    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        settings.remove(INSTALLER_TYPE)
    }

    fun onInstall() {
        controller.onFinish()
    }

    companion object {
        val INSTALLER_TYPE = "INSTALLER_TYPE"
    }
}