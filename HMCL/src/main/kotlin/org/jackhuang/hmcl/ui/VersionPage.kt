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
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.layout.*
import org.jackhuang.hmcl.download.GameAssetIndexDownloadTask
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.ui.wizard.DecoratorPage

class VersionPage : StackPane(), DecoratorPage {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", null)

    @FXML lateinit var versionSettingsController: VersionSettingsController
    @FXML lateinit var modController: ModController
    @FXML lateinit var browseList: JFXListView<*>
    @FXML lateinit var managementList: JFXListView<*>
    @FXML lateinit var btnBrowseMenu: JFXButton
    @FXML lateinit var btnManagementMenu: JFXButton
    val browsePopup: JFXPopup
    val managementPopup: JFXPopup
    lateinit var profile: Profile
    lateinit var version: String

    init {
        loadFXML("/assets/fxml/version.fxml")

        children -= browseList
        children -= managementList

        browsePopup = JFXPopup(browseList)
        managementPopup = JFXPopup(managementList)
    }

    fun load(id: String, profile: Profile) {
        this.version = id
        this.profile = profile
        titleProperty.set(i18n("launcher.title.game") + " - " + id)

        versionSettingsController.loadVersionSetting(profile.getVersionSetting(id))
        modController.loadMods(profile.modManager, id)
    }

    fun onBrowseMenu() {
        browseList.selectionModel.select(-1)
        browsePopup.show(btnBrowseMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -12.0, 15.0)
    }

    fun onManagementMenu() {
        managementList.selectionModel.select(-1)
        managementPopup.show(btnManagementMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -12.0, 15.0)
    }

    fun onBrowse() {
        openFolder(profile.repository.getRunDirectory(version).resolve(when (browseList.selectionModel.selectedIndex) {
            0 -> ""
            1 -> "mods"
            2 -> "coremods"
            3 -> "config"
            4 -> "resourcepacks"
            5 -> "screenshots"
            6 -> "saves"
            else -> throw Error()
        }))
    }

    fun onManagement() {
        when(managementList.selectionModel.selectedIndex) {
            0 -> { // rename a version
                val res = inputDialog(title = "Input", contentText = i18n("versions.manage.rename.message"), defaultValue = version)
                if (res.isPresent)
                    if (profile.repository.renameVersion(version, res.get())) {
                        profile.repository.refreshVersions()
                        Controllers.navigate(null)
                    }
            }
            1 -> { // remove a version
                if (alert(Alert.AlertType.CONFIRMATION, "Confirm", i18n("versions.manage.remove.confirm") + version))
                    if (profile.repository.removeVersionFromDisk(version)) {
                        profile.repository.refreshVersions()
                        Controllers.navigate(null)
                    }
            }
            2 -> { // redownload asset index
                GameAssetIndexDownloadTask(profile.dependency, profile.repository.getVersion(version).resolve(profile.repository)).start()
            }
            3 -> { // delete libraries
                profile.repository.baseDirectory.resolve("libraries").deleteRecursively()
            }
            else -> throw Error()
        }
    }

}