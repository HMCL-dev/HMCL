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
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.Node
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
import org.jackhuang.hmcl.ui.wizard.Wizard

/**
 * @see /assets/fxml/main.fxml
 */
class MainController {

    /**
     * A combo box that allows user to select Minecraft directory.
     */
    @FXML lateinit var comboProfiles: JFXComboBox<String> // TODO: JFXComboBox<Profile>

    /**
     * The button that is to launch the selected game version.
     */
    @FXML lateinit var buttonLaunch: JFXButton

    /**
     * A central pane that contains popups like (global) version settings, app settings, game installations and so on.
     */
    @FXML lateinit var page: StackPane

    @FXML lateinit var listVersions: JFXListView<VersionListItem> // TODO: JFXListView<Version> including icon, title, game version(if equals to title, hidden)

    lateinit var animationHandler: TransitionHandler

    // TODO: implementing functions.
    fun initialize() {
        Controllers.mainController = this

        animationHandler = TransitionHandler(page)

        EVENT_BUS.channel<RefreshedVersionsEvent>() += this::loadVersions
        EVENT_BUS.channel<ProfileLoadingEvent>() += this::onProfilesLoading
        EVENT_BUS.channel<ProfileChangedEvent>() += this::onProfileChanged

        listVersions.setOnMouseClicked {
            if (it.clickCount == 2) {
                setContentPage(Controllers.versionPane)
                val id = listVersions.selectionModel.selectedItem.id

                Controllers.versionController.loadVersionSetting(id, Settings.getLastProfile().getVersionSetting(id))
            } else
                it.consume()
        }

        listVersions.smoothScrolling()

        Settings.onProfileLoading()
    }

    private val empty = Pane()

    fun setContentPage(node: Node?) {
        animationHandler.setContent(node ?: empty, ContainerAnimations.FADE.animationProducer)
    }

    fun installNewVersion() {
        setContentPage(Wizard.createWizard("Install New Game", DownloadWizardProvider()))
    }

    fun onProfilesLoading() {
        // TODO: Profiles
    }

    fun onProfileChanged(event: ProfileChangedEvent) {
        val profile = event.value
        profile.selectedVersionProperty.addListener { _, _, newValue ->
            versionChanged(newValue)
        }
    }

    val versionListItems = mutableMapOf<String, VersionListItem>()

    fun loadVersions() {
        val profile = Settings.getLastProfile()
        val list = mutableListOf<VersionListItem>()
        versionListItems.clear()
        profile.repository.getVersions().forEach {
            val item = VersionListItem(it.id, minecraftVersion(Settings.getLastProfile().repository.getVersionJar(it.id)) ?: "Unknown")
            list += item
            versionListItems += it.id to item
        }

        listVersions.items = FXCollections.observableList(list)
    }

    fun versionChanged(selectedVersion: String) {
        listVersions.selectionModel.select(versionListItems[selectedVersion])
    }
}