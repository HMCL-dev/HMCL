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
import javafx.beans.property.Property
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.beans.value.ChangeListener
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.ui.wizard.DecoratorPage
import org.jackhuang.hmcl.util.OS

class VersionPage : StackPane(), DecoratorPage {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", null)

    @FXML lateinit var versionSettingsController: VersionSettingsController
    @FXML lateinit var modController: ModController

    init {
        loadFXML("/assets/fxml/version.fxml")
    }

    fun load(id: String, profile: Profile) {
        titleProperty.set("Version settings - " + id)

        versionSettingsController.loadVersionSetting(profile.getVersionSetting(id))
        modController.loadMods(profile.modManager, id)
    }
}