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

import com.jfoenix.effects.JFXDepthManager
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.jackhuang.hmcl.mod.ModManager
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Task
import java.util.concurrent.Callable

class ModController {
    @FXML lateinit var scrollPane: ScrollPane
    @FXML lateinit var rootPane: VBox
    private lateinit var modManager: ModManager
    private lateinit var versionId: String

    fun initialize() {
        scrollPane.smoothScrolling()
    }

    fun loadMods(modManager: ModManager, versionId: String) {
        this.modManager = modManager
        this.versionId = versionId
        Task.of(Callable {
            modManager.refreshMods(versionId)
        }).subscribe(Scheduler.JAVAFX) {
            rootPane.children.clear()
            for (modInfo in modManager.getMods(versionId)) {
                rootPane.children += ModItem(modInfo) {
                    modManager.removeMods(versionId, modInfo)
                }.apply {
                    JFXDepthManager.setDepth(this, 1)
                    style += "-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;"

                    modInfo.activeProperty.addListener { _, _, newValue ->
                        if (newValue)
                            styleClass -= "disabled"
                        else
                            styleClass += "disabled"
                    }

                    if (!modInfo.isActive)
                        styleClass += "disabled"
                }
            }
        }
    }
}