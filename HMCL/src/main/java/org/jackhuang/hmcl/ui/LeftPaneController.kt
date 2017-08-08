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
import javafx.scene.Node
import javafx.scene.layout.*
import javafx.scene.paint.Paint
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.event.RefreshedVersionsEvent
import org.jackhuang.hmcl.game.LauncherHelper
import org.jackhuang.hmcl.game.minecraftVersion
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider

class LeftPaneController(val leftPane: VBox) {
    val versionsPane = VBox()
    val cboProfiles = JFXComboBox<String>().apply { items.add("Default"); prefWidthProperty().bind(leftPane.widthProperty()) }
    val accountItem = VersionListItem("mojang@mojang.com", "Yggdrasil")

    init {
        addChildren(ClassTitle("ACCOUNTS"))
        addChildren(RipplerContainer(accountItem).apply {
            accountItem.onSettingsButtonClicked {
                Controllers.navigate(AccountsPage())
            }
        })
        addChildren(ClassTitle("LAUNCHER"))
        addChildren(IconedItem(SVG.gear("black"), "Settings").apply { prefWidthProperty().bind(leftPane.widthProperty()) })
        addChildren(ClassTitle("PROFILES"))
        addChildren(cboProfiles)
        addChildren(ClassTitle("VERSIONS"))
        addChildren(versionsPane)

        EVENT_BUS.channel<RefreshedVersionsEvent>() += this::loadVersions
        EVENT_BUS.channel<ProfileLoadingEvent>() += this::onProfilesLoading
        EVENT_BUS.channel<ProfileChangedEvent>() += this::onProfileChanged

        Settings.onProfileLoading()

        Controllers.decorator.addMenuButton.setOnMouseClicked {
            Controllers.decorator.startWizard(DownloadWizardProvider(), "Install New Game")
        }
        Controllers.decorator.refreshMenuButton.setOnMouseClicked {
            Settings.selectedProfile.repository.refreshVersions()
        }
        Controllers.mainPane.buttonLaunch.setOnMouseClicked { LauncherHelper.launch() }
    }

    private fun addChildren(content: Node) {
        if (content is Pane) {
            leftPane.children += content
        } else {
            val pane = StackPane()
            pane.styleClass += "left-pane-item"
            pane.children.setAll(content)
            leftPane.children += pane
        }
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

    private fun loadAccounts() {

    }

    private fun loadVersions() {
        val profile = Settings.selectedProfile
        versionsPane.children.clear()
        profile.repository.getVersions().forEach { version ->
            val item = VersionListItem(version.id, minecraftVersion(profile.repository.getVersionJar(version.id)) ?: "Unknown")
            val ripplerContainer = RipplerContainer(item)
            item.onSettingsButtonClicked {
                Controllers.decorator.showPage(Controllers.versionPane)
                Controllers.versionPane.loadVersionSetting(item.versionName, profile.getVersionSetting(item.versionName))
            }
            ripplerContainer.ripplerFill = Paint.valueOf("#89E1F9")
            ripplerContainer.setOnMouseClicked {
                // clean selected property
                versionsPane.children.forEach { if (it is RipplerContainer) it.selected = false }
                ripplerContainer.selected = true
                profile.selectedVersion = version.id
            }
            ripplerContainer.userData = version.id to item
            versionsPane.children += ripplerContainer
        }
    }

    fun versionChanged(selectedVersion: String) {
        versionsPane.children
                .filter { it is RipplerContainer && it.userData is Pair<*, *> }
                .forEach { (it as RipplerContainer).selected = (it.userData as Pair<String, VersionListItem>).first == selectedVersion }
    }
}