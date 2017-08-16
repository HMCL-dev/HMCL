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
import com.jfoenix.controls.JFXMasonryPane
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.event.RefreshedVersionsEvent
import org.jackhuang.hmcl.game.LauncherHelper
import org.jackhuang.hmcl.game.minecraftVersion
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.construct.RipplerContainer
import org.jackhuang.hmcl.ui.wizard.DecoratorPage

/**
 * @see /assets/fxml/main.fxml
 */
class MainPage : StackPane(), DecoratorPage {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", i18n("launcher.title.main"))

    @FXML lateinit var btnLaunch: JFXButton
    @FXML lateinit var masonryPane: JFXMasonryPane

    init {
        loadFXML("/assets/fxml/main.fxml")

        btnLaunch.graphic = SVG.launch("white", 15.0, 15.0)
        btnLaunch.limitWidth(40.0)
        btnLaunch.limitHeight(40.0)

        EVENT_BUS.channel<RefreshedVersionsEvent>() += this::loadVersions
        EVENT_BUS.channel<ProfileLoadingEvent>() += this::onProfilesLoading
        EVENT_BUS.channel<ProfileChangedEvent>() += this::onProfileChanged

        Settings.onProfileLoading()

        //    Controllers.decorator.startWizard(DownloadWizardProvider(), "Install New Game")
        //    Settings.selectedProfile.repository.refreshVersions()
        btnLaunch.setOnMouseClicked { LauncherHelper.launch() }
    }

    private fun buildNode(i: Int, profile: Profile, version: String, game: String, group: ToggleGroup): Node {
        return VersionItem(i, group).apply {
            chkSelected.properties["version"] = version
            chkSelected.isSelected = profile.selectedVersion == version
            lblGameVersion.text = game
            lblVersionName.text = version
            btnDelete.setOnMouseClicked {
                profile.repository.removeVersionFromDisk(version)
                Platform.runLater(this@MainPage::loadVersions)
            }
            btnSettings.setOnMouseClicked {
                Controllers.decorator.showPage(Controllers.versionPane)
                Controllers.versionPane.load(version, profile)
            }
        }
    }

    fun onProfilesLoading() {
        // TODO: Profiles
    }

    fun onProfileChanged(event: ProfileChangedEvent) {
        val profile = event.value
        profile.selectedVersionProperty.addListener { _ ->
            versionChanged(profile.selectedVersion)
        }
        profile.selectedVersionProperty.fireValueChangedEvent()
    }

    private fun loadVersions() {
        val profile = Settings.selectedProfile
        val group = ToggleGroup()
        val children = mutableListOf<Node>()
        var i = 0
        profile.repository.getVersions().forEach { version ->
            children += buildNode(++i, profile, version.id, minecraftVersion(profile.repository.getVersionJar(version.id)) ?: "Unknown", group)
        }
        group.selectedToggleProperty().addListener { _, _, newValue ->
            if (newValue != null)
                profile.selectedVersion = newValue.properties["version"] as String
        }
        masonryPane.children.setAll(children)
    }

    @Suppress("UNCHECKED_CAST")
    fun versionChanged(selectedVersion: String) {
        masonryPane.children
                .filter { it is RipplerContainer && it.properties["version"] is Pair<*, *> }
                .forEach { (it as RipplerContainer).selected = (it.properties["version"] as Pair<String, VersionListItem>).first == selectedVersion }
    }
}