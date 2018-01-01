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
import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.event.EventBus
import org.jackhuang.hmcl.event.RefreshedVersionsEvent
import org.jackhuang.hmcl.game.GameVersion.minecraftVersion
import org.jackhuang.hmcl.game.LauncherHelper
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.construct.RipplerContainer
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider
import org.jackhuang.hmcl.ui.wizard.DecoratorPage
import org.jackhuang.hmcl.util.channel
import org.jackhuang.hmcl.util.onChange
import org.jackhuang.hmcl.util.plusAssign

/**
 * @see /assets/fxml/main.fxml
 */
class MainPage : StackPane(), DecoratorPage {
    override val titleProperty = SimpleStringProperty(this, "title", i18n("launcher.title.main"))

    @FXML lateinit var btnLaunch: JFXButton
    @FXML lateinit var btnRefresh: JFXButton
    @FXML lateinit var btnAdd: JFXButton
    @FXML lateinit var masonryPane: JFXMasonryPane

    init {
        loadFXML("/assets/fxml/main.fxml")

        btnLaunch.graphic = SVG.launch("white", 15.0, 15.0)
        btnLaunch.limitWidth(40.0)
        btnLaunch.limitHeight(40.0)

        EventBus.EVENT_BUS.channel<RefreshedVersionsEvent>() += { -> loadVersions() }
        EventBus.EVENT_BUS.channel<ProfileLoadingEvent>() += this::onProfilesLoading
        EventBus.EVENT_BUS.channel<ProfileChangedEvent>() += this::onProfileChanged

        btnAdd.setOnMouseClicked { Controllers.decorator.startWizard(DownloadWizardProvider(), "Install New Game") }
        btnRefresh.setOnMouseClicked { Settings.selectedProfile.repository.refreshVersions() }
        btnLaunch.setOnMouseClicked {
            if (Settings.selectedAccount == null) {
                Controllers.dialog(i18n("login.no_Player007"))
            } else if (Settings.selectedProfile.selectedVersion == null) {
                Controllers.dialog(i18n("minecraft.no_selected_version"))
            } else
                LauncherHelper.launch()
        }
    }

    private fun buildNode(i: Int, profile: Profile, version: String, game: String, group: ToggleGroup): Node {
        return VersionItem(i, group).apply {
            chkSelected.properties["version"] = version
            chkSelected.isSelected = profile.selectedVersion == version
            lblGameVersion.text = game
            lblVersionName.text = version
            btnDelete.setOnMouseClicked {
                profile.repository.removeVersionFromDisk(version)
                Platform.runLater { loadVersions() }
            }
            btnSettings.setOnMouseClicked {
                Controllers.decorator.showPage(Controllers.versionPane)
                Controllers.versionPane.load(version, profile)
            }
            val iconFile = profile.repository.getVersionIcon(version)
            if (iconFile.exists())
                iconView.image = Image("file:" + iconFile.absolutePath)
        }
    }

    fun onProfilesLoading() {
        // TODO: Profiles
    }

    fun onProfileChanged(event: ProfileChangedEvent) = runOnUiThread {
        val profile = event.value
        profile.selectedVersionProperty.setChangedListener {
            versionChanged(profile.selectedVersion)
        }
        loadVersions(profile)
    }

    private fun loadVersions(profile: Profile = Settings.selectedProfile) {
        val group = ToggleGroup()
        val children = mutableListOf<Node>()
        var i = 0
        profile.repository.getVersions().forEach { version ->
            children += buildNode(++i, profile, version.id, minecraftVersion(profile.repository.getVersionJar(version.id)) ?: "Unknown", group)
        }
        group.selectedToggleProperty().onChange {
            if (it != null)
                profile.selectedVersion = it.properties["version"] as String
        }
        masonryPane.resetChildren(children)
    }

    @Suppress("UNCHECKED_CAST")
    fun versionChanged(selectedVersion: String?) {
        masonryPane.children
                .filter { it is RipplerContainer && it.properties["version"] is Pair<*, *> }
                .forEach { (it as RipplerContainer).selected = (it.properties["version"] as Pair<String, VersionListItem>).first == selectedVersion }
    }
}