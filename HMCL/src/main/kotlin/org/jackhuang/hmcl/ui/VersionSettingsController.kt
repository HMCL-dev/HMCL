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
import javafx.beans.binding.Bindings
import javafx.beans.value.ChangeListener
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.ui.construct.ComponentList
import org.jackhuang.hmcl.ui.construct.NumberValidator
import org.jackhuang.hmcl.util.JavaVersion
import org.jackhuang.hmcl.util.OS

class VersionSettingsController {
    var lastVersionSetting: VersionSetting? = null
    @FXML lateinit var rootPane: VBox
    @FXML lateinit var scroll: ScrollPane
    @FXML lateinit var txtWidth: JFXTextField
    @FXML lateinit var txtHeight: JFXTextField
    @FXML lateinit var txtMaxMemory: JFXTextField
    @FXML lateinit var txtJVMArgs: JFXTextField
    @FXML lateinit var txtGameArgs: JFXTextField
    @FXML lateinit var txtMetaspace: JFXTextField
    @FXML lateinit var txtWrapper: JFXTextField
    @FXML lateinit var txtPrecallingCommand: JFXTextField
    @FXML lateinit var txtServerIP: JFXTextField
    @FXML lateinit var txtJavaDir: JFXTextField
    @FXML lateinit var advancedSettingsPane: ComponentList
    @FXML lateinit var cboLauncherVisibility: JFXComboBox<*>
    @FXML lateinit var cboRunDirectory: JFXComboBox<*>
    @FXML lateinit var chkFullscreen: JFXCheckBox
    @FXML lateinit var lblPhysicalMemory: Label
    @FXML lateinit var chkNoJVMArgs: JFXToggleButton
    @FXML lateinit var chkNoCommon: JFXToggleButton
    @FXML lateinit var chkNoGameCheck: JFXToggleButton
    @FXML lateinit var componentJava: ComponentList
    @FXML lateinit var javaPane: VBox
    @FXML lateinit var javaPaneCustom: BorderPane
    @FXML lateinit var radioCustom: JFXRadioButton
    @FXML lateinit var btnJavaSelect: JFXButton

    val javaGroup = ToggleGroup()

    fun initialize() {
        lblPhysicalMemory.text = i18n("settings.physical_memory") + ": ${OS.TOTAL_MEMORY}MB"

        scroll.smoothScrolling()

        val limit = 300.0
        //txtJavaDir.limitWidth(limit)
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

        javaPane.children.clear()
        javaPane.children += createJavaPane(JavaVersion.fromCurrentEnvironment(), javaGroup)
        JavaVersion.JAVAS.values.forEach { javaVersion ->
            javaPane.children += createJavaPane(javaVersion, javaGroup)
        }
        javaPane.children += javaPaneCustom
        javaPaneCustom.limitHeight(20.0)
        radioCustom.toggleGroup = javaGroup
        txtJavaDir.disableProperty().bind(radioCustom.selectedProperty().not())
        btnJavaSelect.disableProperty().bind(radioCustom.selectedProperty().not())
    }

    private fun createJavaPane(java: JavaVersion, group: ToggleGroup): Pane {
        return BorderPane().apply {
            style = "-fx-padding: 3;"
            limitHeight(20.0)
            left = JFXRadioButton(java.longVersion).apply {
                toggleGroup = group
                userData = java
            }
            right = Label(java.binary.absolutePath).apply {
                styleClass += "subtitle-label"
                style += "-fx-font-size: 10;"
            }
        }
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
            javaDirProperty.unbind()
            unbindEnum(cboLauncherVisibility)
            unbindEnum(cboRunDirectory)
        }

        bindInt(txtWidth, version.widthProperty)
        bindInt(txtHeight, version.heightProperty)
        bindInt(txtMaxMemory, version.maxMemoryProperty)
        bindString(txtJavaDir, version.javaDirProperty)
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

        val javaGroupKey = "java_group.listener"
        @Suppress("UNCHECKED_CAST")
        if (javaGroup.properties.containsKey(javaGroupKey))
            javaGroup.selectedToggleProperty().removeListener(javaGroup.properties[javaGroupKey] as ChangeListener<in Toggle>)

        var flag = false
        var defaultToggle: JFXRadioButton? = null
        for (toggle in javaGroup.toggles)
            if (toggle is JFXRadioButton)
                if (toggle.userData == version.javaVersion) {
                    toggle.isSelected = true
                    flag = true
                } else if (toggle.userData == JavaVersion.fromCurrentEnvironment()) {
                    defaultToggle = toggle
                }

        val listener = ChangeListener<Toggle> { _, _, newValue ->
            if (newValue == radioCustom) { // Custom
                version.java = "Custom"
            } else {
                version.java = ((newValue as JFXRadioButton).userData as JavaVersion).longVersion
            }
        }
        javaGroup.properties[javaGroupKey] = listener
        javaGroup.selectedToggleProperty().addListener(listener)

        if (!flag) {
            defaultToggle?.isSelected = true
        }

        version.javaDirProperty.setChangedListener { initJavaSubtitle(version) }
        version.javaProperty.setChangedListener { initJavaSubtitle(version)}
        initJavaSubtitle(version)

        lastVersionSetting = version
    }

    private fun initJavaSubtitle(version: VersionSetting) {
        componentJava.subtitle = version.javaVersion?.binary?.absolutePath ?: "Invalid Java Directory"
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
            txtJavaDir.text = selectedDir.absolutePath
    }
}