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
import javafx.scene.control.ScrollPane
import javafx.scene.input.TransferMode
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.mod.ModManager
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.task
import org.jackhuang.hmcl.util.onChange

class ModController {
    @FXML lateinit var scrollPane: ScrollPane
    @FXML lateinit var rootPane: StackPane
    @FXML lateinit var contentPane: VBox
    private lateinit var modManager: ModManager
    private lateinit var versionId: String

    fun initialize() {
        scrollPane.smoothScrolling()

        rootPane.setOnDragOver { event ->
            if (event.gestureSource != rootPane && event.dragboard.hasFiles())
                event.acceptTransferModes(*TransferMode.COPY_OR_MOVE)
            event.consume()
        }
        rootPane.setOnDragDropped { event ->
            val mods = event.dragboard.files
                    ?.filter { it.extension in listOf("jar", "zip", "litemod") }
            if (mods != null && mods.isNotEmpty()) {
                mods.forEach { modManager.addMod(versionId, it) }
                loadMods(modManager, versionId)
                event.isDropCompleted = true
            }
            event.consume()
        }
    }

    fun loadMods(modManager: ModManager, versionId: String) {
        this.modManager = modManager
        this.versionId = versionId
        task {
            modManager.refreshMods(versionId)
        }.subscribe(Scheduler.JAVAFX) {
            contentPane.children.clear()
            for (modInfo in modManager.getMods(versionId)) {
                contentPane.children += ModItem(modInfo) {
                    modManager.removeMods(versionId, modInfo)
                    loadMods(modManager, versionId)
                }.apply {
                    JFXDepthManager.setDepth(this, 1)
                    style += "-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;"

                    modInfo.activeProperty.onChange {
                        if (it)
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

    fun onAdd() {
        val chooser = FileChooser()
        chooser.title = i18n("mods.choose_mod")
        chooser.extensionFilters.setAll(FileChooser.ExtensionFilter("Mod", "*.jar", "*.zip", "*.litemod"))
        val res = chooser.showOpenDialog(Controllers.stage) ?: return
        try {
            modManager.addMod(versionId, res)
            loadMods(modManager, versionId)
        } catch (e: Exception) {
            Controllers.dialog(i18n("mods.failed"))
        }
    }
}