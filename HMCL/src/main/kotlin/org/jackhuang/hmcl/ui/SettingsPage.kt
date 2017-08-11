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
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.DownloadProviders
import org.jackhuang.hmcl.setting.Locales
import org.jackhuang.hmcl.setting.Proxies
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.wizard.DecoratorPage

class SettingsPage : StackPane(), DecoratorPage {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", i18n("launcher.title.launcher"))

    @FXML lateinit var txtProxyHost: JFXTextField
    @FXML lateinit var txtProxyPort: JFXTextField
    @FXML lateinit var txtProxyUsername: JFXTextField
    @FXML lateinit var txtProxyPassword: JFXTextField
    @FXML lateinit var cboProxyType: JFXComboBox<*>
    @FXML lateinit var cboLanguage: JFXComboBox<*>
    @FXML lateinit var cboDownloadSource: JFXComboBox<*>

    init {
        loadFXML("/assets/fxml/setting.fxml")

        txtProxyHost.text = Settings.PROXY_HOST
        txtProxyHost.textProperty().addListener { _, _, newValue ->
            Settings.PROXY_HOST = newValue
        }

        txtProxyPort.text = Settings.PROXY_PORT
        txtProxyPort.textProperty().addListener { _, _, newValue ->
            Settings.PROXY_PORT = newValue
        }

        txtProxyUsername.text = Settings.PROXY_USER
        txtProxyUsername.textProperty().addListener { _, _, newValue ->
            Settings.PROXY_USER = newValue
        }

        txtProxyPassword.text = Settings.PROXY_PASS
        txtProxyPassword.textProperty().addListener { _, _, newValue ->
            Settings.PROXY_PASS = newValue
        }

        cboDownloadSource.selectionModel.select(DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(Settings.DOWNLOAD_PROVIDER))
        cboDownloadSource.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            Settings.DOWNLOAD_PROVIDER = DownloadProviders.getDownloadProvider(newValue.toInt())
        }

        val list = FXCollections.observableArrayList<Label>()
        for (locale in Locales.LOCALES) {
            list += Label(locale.getName(Settings.LANG.resourceBundle))
        }
        cboLanguage.items = list
        cboLanguage.selectionModel.select(Locales.LOCALES.indexOf(Settings.LANG))
        cboLanguage.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            Settings.LANG = Locales.getLocale(newValue.toInt())
        }

        cboProxyType.selectionModel.select(Proxies.PROXIES.indexOf(Settings.PROXY_TYPE))
        cboProxyType.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            Settings.PROXY_TYPE = Proxies.getProxyType(newValue.toInt())
        }
    }


}