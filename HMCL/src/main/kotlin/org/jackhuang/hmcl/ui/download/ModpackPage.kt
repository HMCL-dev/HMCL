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
import com.jfoenix.controls.JFXTextField
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.stage.FileChooser
import org.jackhuang.hmcl.game.ModpackHelper
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.ui.*
import org.jackhuang.hmcl.ui.construct.Validator
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage
import org.jackhuang.hmcl.util.onInvalidated

class ModpackPage(private val controller: WizardController): StackPane(), WizardPage {
    override val title: String = i18n("modpack.task.install")

    @FXML lateinit var borderPane: Region
    @FXML lateinit var lblName: Label
    @FXML lateinit var lblVersion: Label
    @FXML lateinit var lblAuthor: Label
    @FXML lateinit var lblModpackLocation: Label
    @FXML lateinit var txtModpackName: JFXTextField
    @FXML lateinit var btnInstall: JFXButton
    var manifest: Modpack? = null

    init {
        loadFXML("/assets/fxml/download/modpack.fxml")

        val profile = controller.settings["PROFILE"] as Profile

        val chooser = FileChooser()
        chooser.title = i18n("modpack.choose")
        chooser.extensionFilters += FileChooser.ExtensionFilter(i18n("modpack"), "*.zip")
        val selectedFile = chooser.showOpenDialog(Controllers.stage)
        if (selectedFile == null) Platform.runLater { controller.onFinish() }
        else {
            // TODO: original HMCL modpack support.
            controller.settings[MODPACK_FILE] = selectedFile
            lblModpackLocation.text = selectedFile.absolutePath
            txtModpackName.validators += Validator { !profile.repository.hasVersion(it) && it.isNotBlank() }.apply { message = i18n("version.already_exists") }
            txtModpackName.textProperty().onInvalidated { btnInstall.isDisable = !txtModpackName.validate() }

            try {
                manifest = ModpackHelper.readModpackManifest(selectedFile)
                controller.settings[MODPACK_CURSEFORGE_MANIFEST] = manifest!!
                lblName.text = manifest!!.name
                lblVersion.text = manifest!!.version
                lblAuthor.text = manifest!!.author
                txtModpackName.text = manifest!!.name + (if (manifest!!.version.isNullOrBlank()) "" else ("-" + manifest!!.version))
            } catch (e: Exception) {
                // TODO
                txtModpackName.text = i18n("modpack.task.install.error")
            }
        }

        //borderPane.limitHeight(100.0)
        borderPane.limitWidth(500.0)
    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        settings.remove(MODPACK_FILE)
    }

    fun onInstall() {
        if (!txtModpackName.validate()) return
        controller.settings[MODPACK_NAME] = txtModpackName.text
        controller.onFinish()
    }

    fun onDescribe() {
        if (manifest != null)
            WebStage().apply {
                webView.engine.loadContent(manifest!!.description)
                title = i18n("modpack.wizard.step.3")
            }.showAndWait()
    }

    companion object {
        val MODPACK_FILE = "MODPACK_FILE"
        val MODPACK_NAME = "MODPACK_NAME"
        val MODPACK_CURSEFORGE_MANIFEST = "CURSEFORGE_MANIFEST"
    }
}