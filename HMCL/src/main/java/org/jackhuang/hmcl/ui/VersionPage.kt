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

import com.jfoenix.controls.JFXScrollPane
import com.jfoenix.controls.JFXTextField
import javafx.beans.InvalidationListener
import javafx.beans.property.Property
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.fxml.FXML
import javafx.scene.control.ScrollPane
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.ui.wizard.HasTitle

class VersionPage : StackPane(), HasTitle {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", null)
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
    @FXML lateinit var advancedSettingsPane: VBox

    init {
        loadFXML("/assets/fxml/version.fxml")
    }

    fun initialize() {
        JFXScrollPane.smoothScrolling(scroll)

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

    fun loadVersionSetting(id: String, version: VersionSetting) {
        titleProperty.set("Version settings - " + id)
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

        lastVersionSetting = version
    }

    private fun bindInt(textField: JFXTextField, property: Property<*>) {
        textField.textProperty().unbind()
        textField.textProperty().bindBidirectional(property as Property<Int>, SafeIntStringConverter())
    }

    private fun bindString(textField: JFXTextField, property: Property<String>) {
        textField.textProperty().unbind()
        textField.textProperty().bindBidirectional(property)
    }

    fun onShowAdvanced() {
        if (!rootPane.children.contains(advancedSettingsPane))
            rootPane.children += advancedSettingsPane
    }

    fun onExploreJavaDir() {
        val chooser = DirectoryChooser()
        chooser.title = "Selecting Java Directory"
        val selectedDir = chooser.showDialog(Controllers.stage)
        if (selectedDir != null)
            txtGameDir.text = selectedDir.absolutePath
    }
}