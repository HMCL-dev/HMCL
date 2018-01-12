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

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextField
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.fxml.FXML
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.construct.FileItem
import org.jackhuang.hmcl.ui.wizard.DecoratorPage
import org.jackhuang.hmcl.util.onChangeAndOperate
import java.io.File

/**
 * @param profile null if creating a new profile.
 */
class ProfilePage(private val profile: Profile?): StackPane(), DecoratorPage {
    private val titleProperty = SimpleStringProperty(this, "title",
            if (profile == null) i18n("ui.newProfileWindow.title") else i18n("ui.label.profile") + " - " + profile.name)

    override fun titleProperty() = titleProperty

    private val locationProperty = SimpleStringProperty(this, "location",
            profile?.gameDir?.absolutePath ?: "")
    @FXML lateinit var txtProfileName: JFXTextField
    @FXML lateinit var gameDir: FileItem
    @FXML lateinit var btnSave: JFXButton
    @FXML lateinit var btnDelete: JFXButton

    init {
        loadFXML("/assets/fxml/profile.fxml")

        txtProfileName.text = profile?.name ?: ""
        txtProfileName.textProperty().onChangeAndOperate {
            btnSave.isDisable = !txtProfileName.validate() || locationProperty.get().isNullOrBlank()
        }
        gameDir.setProperty(locationProperty)
        locationProperty.onChangeAndOperate {
            btnSave.isDisable = !txtProfileName.validate() || locationProperty.get().isNullOrBlank()
        }

        if (profile == null)
            btnDelete.isVisible = false
    }

    fun onDelete() {
        if (profile != null) {
            Settings.INSTANCE.deleteProfile(profile)
            Controllers.navigate(null)
        }
    }

    fun onSave() {
        if (profile != null) { // editing a profile
            profile.name = txtProfileName.text
            if (locationProperty.get() != null)
                profile.gameDir = File(locationProperty.get())
        } else {
            if (locationProperty.get().isNullOrBlank()) {
                gameDir.onExplore()
            }
            Settings.INSTANCE.putProfile(Profile(txtProfileName.text, File(locationProperty.get())))
        }

        Settings.INSTANCE.onProfileLoading()
        Controllers.navigate(null)
    }
}