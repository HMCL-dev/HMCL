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

import com.google.gson.JsonParseException
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextField
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.FileChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.mod.readCurseForgeModpackManifest
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.construct.Validator
import org.jackhuang.hmcl.ui.loadFXML
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage
import java.io.IOException

class ModpackPage(private val controller: WizardController): StackPane(), WizardPage {
    override val title: String = "Install a modpack"

    @FXML lateinit var lblName: Label
    @FXML lateinit var lblVersion: Label
    @FXML lateinit var lblAuthor: Label
    @FXML lateinit var lblModpackLocation: Label
    @FXML lateinit var txtModpackName: JFXTextField
    @FXML lateinit var btnInstall: JFXButton

    init {
        loadFXML("/assets/fxml/download/modpack.fxml")

        val profile = controller.settings["PROFILE"] as Profile

        val chooser = FileChooser()
        chooser.title = i18n("modpack.choose")
        val selectedFile = chooser.showOpenDialog(Controllers.stage)
        if (selectedFile == null) Platform.runLater { controller.onFinish() }
        else {
            // TODO: original HMCL modpack support.
            controller.settings[MODPACK_FILE] = selectedFile
            lblModpackLocation.text = selectedFile.absolutePath
            txtModpackName.text = selectedFile.nameWithoutExtension
            txtModpackName.validators += Validator { !profile.repository.hasVersion(it) }
            txtModpackName.textProperty().addListener { _ ->
                btnInstall.isDisabled = !txtModpackName.validate()
            }

            try {
                val manifest = readCurseForgeModpackManifest(selectedFile)
                controller.settings[MODPACK_CURSEFORGE_MANIFEST] = manifest
                lblName.text = manifest.name
                lblVersion.text = manifest.version
                lblAuthor.text = manifest.author
            } catch (e: IOException) {
                // TODO
            } catch (e: JsonParseException) {
                // TODO
            }
        }
    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        settings.remove(MODPACK_FILE)
    }

    fun onInstall() {
        if (!txtModpackName.validate()) return
        controller.settings[MODPACK_NAME] = txtModpackName.text
        controller.onFinish()
    }

    companion object {
        val MODPACK_FILE = "MODPACK_FILE"
        val MODPACK_NAME = "MODPACK_NAME"
        val MODPACK_CURSEFORGE_MANIFEST = "CURSEFORGE_MANIFEST"
    }
}