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

import javafx.scene.layout.VBox
import javafx.scene.paint.Paint
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.game.AccountHelper
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.ui.construct.IconedItem
import org.jackhuang.hmcl.ui.construct.RipplerContainer
import org.jackhuang.hmcl.util.onChangeAndOperate
import java.util.*

class LeftPaneController(private val leftPane: AdvancedListBox) {
    val profilePane = VBox()
    val accountItem = VersionListItem("No Account", "unknown")

    init {
        leftPane
                .startCategory("ACCOUNTS")
                .add(RipplerContainer(accountItem).apply {
                    setOnMouseClicked {
                        Controllers.navigate(AccountsPage())
                    }
                    accountItem.onSettingsButtonClicked {
                        Controllers.navigate(AccountsPage())
                    }
                })
                .startCategory("LAUNCHER")
                .add(IconedItem(SVG.gear("black"), i18n("launcher.title.launcher")).apply {
                    prefWidthProperty().bind(leftPane.widthProperty())
                    setOnMouseClicked {
                        Controllers.navigate(Controllers.settingsPane)
                    }
                })
                .startCategory(i18n("ui.label.profile"))
                .add(profilePane)

        EVENT_BUS.channel<ProfileLoadingEvent>() += this::onProfilesLoading
        EVENT_BUS.channel<ProfileChangedEvent>() += this::onProfileChanged

        Controllers.decorator.addMenuButton.setOnMouseClicked {
            Controllers.decorator.showPage(ProfilePage(null))
        }

        Settings.selectedAccountProperty.onChangeAndOperate {
            if (it == null) {
                accountItem.lblVersionName.text = "mojang@mojang.com"
                accountItem.lblGameVersion.text = "Yggdrasil"
            } else {
                accountItem.lblVersionName.text = it.username
                accountItem.lblGameVersion.text = accountType(it)
            }
            if (it is YggdrasilAccount) {
                accountItem.imageView.image = AccountHelper.getSkin(it, 4.0)
                accountItem.imageView.viewport = AccountHelper.getViewport(4.0)
            } else {
                accountItem.imageView.image = DEFAULT_ICON
                accountItem.imageView.viewport = null
            }
        }

        if (Settings.getAccounts().isEmpty())
            Controllers.navigate(AccountsPage())
    }

    fun onProfileChanged(event: ProfileChangedEvent) {
        val profile = event.value

        profilePane.children
                .filter { it is RipplerContainer && it.properties["profile"] is Pair<*, *> }
                .forEach { (it as RipplerContainer).selected = (it.properties["profile"] as Pair<*, *>).first == profile.name }
    }

    fun onProfilesLoading() {
        val list = LinkedList<RipplerContainer>()
        Settings.getProfiles().forEach { profile ->
            val item = VersionListItem(profile.name).apply {
                lblGameVersion.textProperty().bind(profile.selectedVersionProperty)
            }
            val ripplerContainer = RipplerContainer(item)
            item.onSettingsButtonClicked {
                Controllers.decorator.showPage(ProfilePage(profile))
            }
            ripplerContainer.ripplerFill = Paint.valueOf("#89E1F9")
            ripplerContainer.setOnMouseClicked {
                // clean selected property
                profilePane.children.forEach { if (it is RipplerContainer) it.selected = false }
                ripplerContainer.selected = true
                Settings.selectedProfile = profile
            }
            ripplerContainer.properties["profile"] = profile.name to item
            ripplerContainer.maxWidthProperty().bind(leftPane.widthProperty())
            list += ripplerContainer
        }
        runOnUiThread { profilePane.children.setAll(list) }
    }
}