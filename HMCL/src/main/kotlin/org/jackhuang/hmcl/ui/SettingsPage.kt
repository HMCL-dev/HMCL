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

import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXTextField
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.text.Font
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.DownloadProviders
import org.jackhuang.hmcl.setting.Locales
import org.jackhuang.hmcl.setting.Proxies
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.construct.FileItem
import org.jackhuang.hmcl.ui.construct.FontComboBox
import org.jackhuang.hmcl.ui.construct.Validator
import org.jackhuang.hmcl.ui.wizard.DecoratorPage
import org.jackhuang.hmcl.util.onChange

class SettingsPage : StackPane(), DecoratorPage {
    private val titleProperty: StringProperty = SimpleStringProperty(this, "title", i18n("launcher.title.launcher"))

    override fun titleProperty() = titleProperty

    @FXML lateinit var txtProxyHost: JFXTextField
    @FXML lateinit var txtProxyPort: JFXTextField
    @FXML lateinit var txtProxyUsername: JFXTextField
    @FXML lateinit var txtProxyPassword: JFXTextField
    @FXML lateinit var cboProxyType: JFXComboBox<*>
    @FXML lateinit var cboFont: FontComboBox
    @FXML lateinit var cboLanguage: JFXComboBox<*>
    @FXML lateinit var cboDownloadSource: JFXComboBox<*>
    @FXML lateinit var fileCommonLocation: FileItem
    @FXML lateinit var fileBackgroundLocation: FileItem
    @FXML lateinit var lblDisplay: Label
    @FXML lateinit var txtFontSize: JFXTextField

    init {
        loadFXML("/assets/fxml/setting.fxml")

        cboLanguage.limitWidth(400.0)
        cboDownloadSource.limitWidth(400.0)

        txtProxyHost.text = Settings.INSTANCE.proxyHost
        txtProxyHost.textProperty().onChange { Settings.INSTANCE.proxyHost = it }

        txtProxyPort.text = Settings.INSTANCE.proxyPort
        txtProxyPort.textProperty().onChange { Settings.INSTANCE.proxyPort = it }

        txtProxyUsername.text = Settings.INSTANCE.proxyUser
        txtProxyUsername.textProperty().onChange { Settings.INSTANCE.proxyUser = it }

        txtProxyPassword.text = Settings.INSTANCE.proxyPass
        txtProxyPassword.textProperty().onChange { Settings.INSTANCE.proxyPass = it }

        cboDownloadSource.selectionModel.select(DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(Settings.INSTANCE.downloadProvider))
        cboDownloadSource.selectionModel.selectedIndexProperty().onChange {
            Settings.INSTANCE.downloadProvider = DownloadProviders.getDownloadProvider(it)
        }

        cboFont.selectionModel.select(Settings.INSTANCE.font.family)
        cboFont.valueProperty().onChange {
            val font = Font.font(it, Settings.INSTANCE.font.size)
            Settings.INSTANCE.font = font
            lblDisplay.style = "-fx-font: ${Settings.INSTANCE.font.size} \"${font.family}\";"
        }
        txtFontSize.text = Settings.INSTANCE.font.size.toString()
        txtFontSize.validators += Validator { it.toDoubleOrNull() != null }
        txtFontSize.textProperty().onChange {
            if (txtFontSize.validate()) {
                val font = Font.font(Settings.INSTANCE.font.family, it!!.toDouble())
                Settings.INSTANCE.font = font
                lblDisplay.style = "-fx-font: ${font.size} \"${Settings.INSTANCE.font.family}\";"
            }
        }
        lblDisplay.style = "-fx-font: ${Settings.INSTANCE.font.size} \"${Settings.INSTANCE.font.family}\";"

        val list = FXCollections.observableArrayList<Label>()
        for (locale in Locales.LOCALES) {
            list += Label(locale.getName(Settings.INSTANCE.locale.resourceBundle))
        }
        cboLanguage.items = list
        cboLanguage.selectionModel.select(Locales.LOCALES.indexOf(Settings.INSTANCE.locale))
        cboLanguage.selectionModel.selectedIndexProperty().onChange {
            Settings.INSTANCE.locale = Locales.getLocale(it)
        }

        cboProxyType.selectionModel.select(Proxies.PROXIES.indexOf(Settings.INSTANCE.proxyType))
        cboProxyType.selectionModel.selectedIndexProperty().onChange {
            Settings.INSTANCE.proxyType = Proxies.getProxyType(it)
        }

        fileCommonLocation.setProperty(Settings.INSTANCE.commonPathProperty())
    }


}