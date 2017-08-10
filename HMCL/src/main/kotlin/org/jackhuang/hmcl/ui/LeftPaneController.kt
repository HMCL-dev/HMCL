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
import javafx.beans.value.ChangeListener
import javafx.scene.layout.*
import javafx.scene.paint.Paint
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.auth.Account
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.event.RefreshedVersionsEvent
import org.jackhuang.hmcl.game.LauncherHelper
import org.jackhuang.hmcl.game.minecraftVersion
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider

class LeftPaneController(leftPane: AdvancedListBox) {
    val versionsPane = VBox()
    val cboProfiles = JFXComboBox<String>().apply { items.add("Default"); prefWidthProperty().bind(leftPane.widthProperty()) }
    val accountItem = VersionListItem("mojang@mojang.com", "Yggdrasil")

    init {
        leftPane
                .startCategory("ACCOUNTS")
                .add(RipplerContainer(accountItem).apply {
                    accountItem.onSettingsButtonClicked {
                        Controllers.navigate(AccountsPage())
                    }
                })
                .startCategory("PROFILES")
                .add(cboProfiles)
                .startCategory("VERSIONS")
                .add(versionsPane)

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

        val listener = ChangeListener<Account?> { _, _, newValue ->
            if (newValue == null) {
                accountItem.lblVersionName.text = "mojang@mojang.com"
                accountItem.lblGameVersion.text = "Yggdrasil"
            } else {
                accountItem.lblVersionName.text = newValue.username
                accountItem.lblGameVersion.text = accountType(newValue)
            }
        }
        Settings.selectedAccountProperty.addListener(listener)
        listener.changed(null, null, Settings.selectedAccount)

        if (Settings.getAccounts().isEmpty())
            Controllers.navigate(AccountsPage())
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
        versionsPane.children.clear()
        profile.repository.getVersions().forEach { version ->
            val item = VersionListItem(version.id, minecraftVersion(profile.repository.getVersionJar(version.id)) ?: "Unknown")
            val ripplerContainer = RipplerContainer(item)
            item.onSettingsButtonClicked {
                Controllers.decorator.showPage(Controllers.versionPane)
                Controllers.versionPane.load(item.versionName, profile)
            }
            ripplerContainer.ripplerFill = Paint.valueOf("#89E1F9")
            ripplerContainer.setOnMouseClicked {
                // clean selected property
                versionsPane.children.forEach { if (it is RipplerContainer) it.selected = false }
                ripplerContainer.selected = true
                profile.selectedVersion = version.id
            }
            ripplerContainer.properties["version"] = version.id to item
            versionsPane.children += ripplerContainer
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun versionChanged(selectedVersion: String) {
        versionsPane.children
                .filter { it is RipplerContainer && it.properties["version"] is Pair<*, *> }
                .forEach { (it as RipplerContainer).selected = (it.properties["version"] as Pair<String, VersionListItem>).first == selectedVersion }
    }
}