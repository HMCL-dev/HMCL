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

class SettingsPage : StackPane(), DecoratorPage {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", i18n("launcher.title.launcher"))

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

        txtProxyHost.text = Settings.proxyHost
        txtProxyHost.textProperty().addListener { _, _, newValue ->
            Settings.proxyHost = newValue
        }

        txtProxyPort.text = Settings.proxyPort
        txtProxyPort.textProperty().addListener { _, _, newValue ->
            Settings.proxyPort = newValue
        }

        txtProxyUsername.text = Settings.proxyUser
        txtProxyUsername.textProperty().addListener { _, _, newValue ->
            Settings.proxyUser = newValue
        }

        txtProxyPassword.text = Settings.proxyPass
        txtProxyPassword.textProperty().addListener { _, _, newValue ->
            Settings.proxyPass = newValue
        }

        cboDownloadSource.selectionModel.select(DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(Settings.downloadProvider))
        cboDownloadSource.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            Settings.downloadProvider = DownloadProviders.getDownloadProvider(newValue.toInt())
        }

        cboFont.selectionModel.select(Settings.font.family)
        cboFont.valueProperty().addListener { _, _, newValue ->
            val font = Font.font(newValue, Settings.font.size)
            Settings.font = font
            lblDisplay.style = "-fx-font: ${Settings.font.size} \"${font.family}\";"
        }
        txtFontSize.text = Settings.font.size.toString()
        txtFontSize.validators += Validator { it.toDoubleOrNull() != null }
        txtFontSize.textProperty().addListener { _, _, newValue ->
            if (txtFontSize.validate()) {
                val font = Font.font(Settings.font.family, newValue.toDouble())
                Settings.font = font
                lblDisplay.style = "-fx-font: ${font.size} \"${Settings.font.family}\";"
            }
        }
        lblDisplay.style = "-fx-font: ${Settings.font.size} \"${Settings.font.family}\";"

        val list = FXCollections.observableArrayList<Label>()
        for (locale in Locales.LOCALES) {
            list += Label(locale.getName(Settings.locale.resourceBundle))
        }
        cboLanguage.items = list
        cboLanguage.selectionModel.select(Locales.LOCALES.indexOf(Settings.locale))
        cboLanguage.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            Settings.locale = Locales.getLocale(newValue.toInt())
        }

        cboProxyType.selectionModel.select(Proxies.PROXIES.indexOf(Settings.proxyType))
        cboProxyType.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            Settings.proxyType = Proxies.getProxyType(newValue.toInt())
        }

        fileCommonLocation.setProperty(Settings.commonPathProperty)
    }


}