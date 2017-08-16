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

import com.jfoenix.controls.*
import javafx.beans.InvalidationListener
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.ui.construct.ComponentList
import org.jackhuang.hmcl.util.OS

class VersionSettingsController {
    var lastVersionSetting: VersionSetting? = null
    @FXML lateinit var rootPane: VBox
    @FXML lateinit var scroll: ScrollPane
    @FXML lateinit var settingsPane: GridPane
    @FXML lateinit var txtWidth: JFXTextField
    @FXML lateinit var txtHeight: JFXTextField
    @FXML lateinit var txtMaxMemory: JFXTextField
    @FXML lateinit var txtJVMArgs: JFXTextField
    @FXML lateinit var txtGameArgs: JFXTextField
    @FXML lateinit var txtMetaspace: JFXTextField
    @FXML lateinit var txtWrapper: JFXTextField
    @FXML lateinit var txtPrecallingCommand: JFXTextField
    @FXML lateinit var txtServerIP: JFXTextField
    @FXML lateinit var txtGameDir: JFXTextField
    @FXML lateinit var advancedSettingsPane: ComponentList
    @FXML lateinit var cboLauncherVisibility: JFXComboBox<*>
    @FXML lateinit var cboRunDirectory: JFXComboBox<*>
    @FXML lateinit var chkFullscreen: JFXCheckBox
    @FXML lateinit var lblPhysicalMemory: Label
    @FXML lateinit var chkNoJVMArgs: JFXToggleButton
    @FXML lateinit var chkNoCommon: JFXToggleButton
    @FXML lateinit var chkNoGameCheck: JFXToggleButton

    fun initialize() {
        lblPhysicalMemory.text = i18n("settings.physical_memory") + ": ${OS.TOTAL_MEMORY}MB"

        scroll.smoothScrolling()

        val limit = 300.0
        txtGameDir.limitWidth(limit)
        txtMaxMemory.limitWidth(limit)
        cboLauncherVisibility.limitWidth(limit)
        cboRunDirectory.limitWidth(limit)

        val limitHeight = 10.0
        chkNoJVMArgs.limitHeight(limitHeight)
        chkNoCommon.limitHeight(limitHeight)
        chkNoGameCheck.limitHeight(limitHeight)

        fun validation(field: JFXTextField) = InvalidationListener { field.validate() }
        fun validator(nullable: Boolean = false) = NumberValidator(nullable).apply { message = "Must be a number." }

        txtWidth.setValidators(validator())
        txtWidth.textProperty().addListener(validation(txtWidth))
        txtHeight.setValidators(validator())
        txtHeight.textProperty().addListener(validation(txtHeight))
        txtMaxMemory.setValidators(validator())
        txtMaxMemory.textProperty().addListener(validation(txtMaxMemory))
        txtMetaspace.setValidators(validator(true))
        txtMetaspace.textProperty().addListener(validation(txtMetaspace))
    }

    fun loadVersionSetting(version: VersionSetting) {
        rootPane.children -= advancedSettingsPane

        lastVersionSetting?.apply {
            widthProperty.unbind()
            heightProperty.unbind()
            maxMemoryProperty.unbind()
            javaArgsProperty.unbind()
            minecraftArgsProperty.unbind()
            permSizeProperty.unbind()
            wrapperProperty.unbind()
            precalledCommandProperty.unbind()
            serverIpProperty.unbind()
            fullscreenProperty.unbind()
            notCheckGameProperty.unbind()
            noCommonProperty.unbind()
            unbindEnum(cboLauncherVisibility)
            unbindEnum(cboRunDirectory)
        }

        bindInt(txtWidth, version.widthProperty)
        bindInt(txtHeight, version.heightProperty)
        bindInt(txtMaxMemory, version.maxMemoryProperty)
        bindString(txtJVMArgs, version.javaArgsProperty)
        bindString(txtGameArgs, version.minecraftArgsProperty)
        bindString(txtMetaspace, version.permSizeProperty)
        bindString(txtWrapper, version.wrapperProperty)
        bindString(txtPrecallingCommand, version.precalledCommandProperty)
        bindString(txtServerIP, version.serverIpProperty)
        bindEnum(cboLauncherVisibility, version.launcherVisibilityProperty)
        bindEnum(cboRunDirectory, version.gameDirTypeProperty)
        bindBoolean(chkFullscreen, version.fullscreenProperty)
        bindBoolean(chkNoGameCheck, version.notCheckGameProperty)
        bindBoolean(chkNoCommon, version.noCommonProperty)

        lastVersionSetting = version
    }

    fun onShowAdvanced() {
        if (!rootPane.children.contains(advancedSettingsPane))
            rootPane.children += advancedSettingsPane
        else
            rootPane.children.remove(advancedSettingsPane)
    }

    fun onExploreJavaDir() {
        val chooser = DirectoryChooser()
        chooser.title = i18n("settings.choose_javapath")
        val selectedDir = chooser.showDialog(Controllers.stage)
        if (selectedDir != null)
            txtGameDir.text = selectedDir.absolutePath
    }
}