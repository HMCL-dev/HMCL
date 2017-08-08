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
import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXListCell
import com.jfoenix.controls.JFXListView
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.event.RefreshedVersionsEvent
import org.jackhuang.hmcl.game.minecraftVersion
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.ui.animation.ContainerAnimations
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider
import org.jackhuang.hmcl.ui.animation.TransitionHandler
import org.jackhuang.hmcl.ui.wizard.HasTitle
import org.jackhuang.hmcl.ui.wizard.Wizard

/**
 * @see /assets/fxml/main.fxml
 */
class MainPage : BorderPane(), HasTitle {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", "Main Page")

    @FXML lateinit var buttonLaunch: JFXButton

    init {
        loadFXML("/assets/fxml/main.fxml")
    }

}