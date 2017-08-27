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
package org.jackhuang.hmcl.ui.export

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextArea
import com.jfoenix.controls.JFXTextField
import com.jfoenix.controls.JFXToggleButton
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.StackPane
import javafx.stage.FileChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.Controllers
import org.jackhuang.hmcl.ui.loadFXML
import org.jackhuang.hmcl.ui.smoothScrolling
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage
import org.jackhuang.hmcl.util.onInvalidated

class ModpackInfoPage(private val controller: WizardController, version: String): StackPane(), WizardPage {
    override val title: String = i18n("modpack.wizard.step.1.title")
    @FXML lateinit var lblVersionName: Label
    @FXML lateinit var txtModpackName: JFXTextField
    @FXML lateinit var txtModpackAuthor: JFXTextField
    @FXML lateinit var txtModpackVersion: JFXTextField
    @FXML lateinit var txtModpackDescription: JFXTextArea
    @FXML lateinit var chkIncludeLauncher: JFXToggleButton
    @FXML lateinit var btnNext: JFXButton
    @FXML lateinit var scroll: ScrollPane

    init {
        loadFXML("/assets/fxml/modpack/info.fxml")
        scroll.smoothScrolling()
        txtModpackName.text = version
        txtModpackName.textProperty().onInvalidated(this::checkValidation)
        txtModpackAuthor.textProperty().onInvalidated(this::checkValidation)
        txtModpackVersion.textProperty().onInvalidated(this::checkValidation)
        txtModpackAuthor.text = Settings.selectedAccount?.username ?: ""
        lblVersionName.text = version
    }

    private fun checkValidation() {
        btnNext.isDisable = !txtModpackName.validate() || !txtModpackVersion.validate() || !txtModpackAuthor.validate()
    }

    fun onNext() {
        val fileChooser = FileChooser()
        fileChooser.title = i18n("modpack.wizard.step.initialization.save")
        fileChooser.extensionFilters += FileChooser.ExtensionFilter(i18n("modpack"), "*.zip")
        val file = fileChooser.showSaveDialog(Controllers.stage)
        if (file == null) {
            Controllers.navigate(null)
            return
        }
        controller.settings[MODPACK_NAME] = txtModpackName.text
        controller.settings[MODPACK_VERSION] = txtModpackVersion.text
        controller.settings[MODPACK_AUTHOR] = txtModpackAuthor.text
        controller.settings[MODPACK_FILE] = file
        controller.settings[MODPACK_DESCRIPTION] = txtModpackDescription.text
        controller.settings[MODPACK_INCLUDE_LAUNCHER] = chkIncludeLauncher.isSelected
        controller.onNext()
    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        controller.settings.remove(MODPACK_NAME)
        controller.settings.remove(MODPACK_VERSION)
        controller.settings.remove(MODPACK_AUTHOR)
        controller.settings.remove(MODPACK_DESCRIPTION)
        controller.settings.remove(MODPACK_INCLUDE_LAUNCHER)
        controller.settings.remove(MODPACK_FILE)
    }

    companion object {
        const val MODPACK_NAME = "modpack.name"
        const val MODPACK_VERSION = "modpack.version"
        const val MODPACK_AUTHOR = "modpack.author"
        const val MODPACK_DESCRIPTION = "modpack.description"
        const val MODPACK_INCLUDE_LAUNCHER = "modpack.include_launcher"
        const val MODPACK_FILE = "modpack.file"
    }
}