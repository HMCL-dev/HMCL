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
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
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

    @FXML lateinit var listVersions: JFXListView<VersionSetting> // TODO: JFXListView<Version> including icon, title, game version(if equals to title, hidden)

    lateinit var animationHandler: TransitionHandler

    // TODO: implementing functions.
    fun initialize() {
        Controllers.mainController = this

        animationHandler = TransitionHandler(page)

        listVersions.items.add(VersionSetting("1"))
        listVersions.items.add(VersionSetting("2"))
        listVersions.items.add(VersionSetting("3"))
        listVersions.items.add(VersionSetting("4"))
        listVersions.items.add(VersionSetting("5"))
        listVersions.items.add(VersionSetting("6"))
        listVersions.items.add(VersionSetting("7"))
        listVersions.items.add(VersionSetting("8"))
        listVersions.items.add(VersionSetting("9"))
        listVersions.items.add(VersionSetting("10"))
        listVersions.items.add(VersionSetting("11"))
        listVersions.items.add(VersionSetting("12"))

        listVersions.setCellFactory {
            object : JFXListCell<VersionSetting>() {
                override fun updateItem(item: VersionSetting?, empty: Boolean) {
                    super.updateItem(item, empty)

                    if (item == null || empty) return
                    val g = VersionListItem(item, item.gameVersion)
                    g.onSettingsButtonClicked {
                        setContentPage(Controllers.versionPane)
                        Controllers.versionController.loadVersionSetting(g.setting)
                    }
                    graphic = g
                }
            }
        }

        listVersions.setOnMouseClicked {
            if (it.clickCount == 2) {
                setContentPage(Controllers.versionPane)
                Controllers.versionController.loadVersionSetting(listVersions.selectionModel.selectedItem)
            } else
                it.consume()
        }

        comboProfiles.items.add("SA")
        comboProfiles.items.add("SB")
        comboProfiles.items.add("SC")
        comboProfiles.items.add("SD")
        comboProfiles.items.add("SE")
        comboProfiles.items.add("SF")
        comboProfiles.items.add("SG")
        comboProfiles.items.add("SH")
        comboProfiles.items.add("SI")
        comboProfiles.items.add("SJ")
        comboProfiles.items.add("SK")

        listVersions.smoothScrolling()
    }

    private val empty = Pane()

    fun setContentPage(node: Node?) {
        animationHandler.setContent(node ?: empty, ContainerAnimations.FADE.animationProducer)
    }

    fun installNewVersion() {
        setContentPage(Wizard.createWizard("Install New Game", DownloadWizardProvider()))
    }
}