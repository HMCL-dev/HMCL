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

import com.jfoenix.controls.JFXSpinner
import com.jfoenix.controls.JFXTabPane
import javafx.fxml.FXML
import javafx.scene.control.ScrollPane
import javafx.scene.input.TransferMode
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.mod.ModManager
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.util.onChange
import org.jackhuang.hmcl.util.onChangeAndOperateWeakly
import org.jackhuang.hmcl.util.task
import java.util.*

class ModController {
    @FXML lateinit var scrollPane: ScrollPane
    @FXML lateinit var rootPane: StackPane
    @FXML lateinit var modPane: VBox
    @FXML lateinit var contentPane: StackPane
    @FXML lateinit var spinner: JFXSpinner
    lateinit var parentTab: JFXTabPane
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
            synchronized(contentPane) {
                runOnUiThread { rootPane.children -= contentPane; spinner.isVisible = true }
                modManager.refreshMods(versionId)

                // Surprisingly, if there are a great number of mods, this processing will cause a UI pause.
                // We must do this asynchronously.
                val list = LinkedList<ModItem>()
                for (modInfo in modManager.getMods(versionId)) {
                    list += ModItem(modInfo) {
                        modManager.removeMods(versionId, modInfo)
                        loadMods(modManager, versionId)
                    }.apply {
                        modInfo.activeProperty().onChange {
                            if (it)
                                styleClass -= "disabled"
                            else
                                styleClass += "disabled"
                        }

                        if (!modInfo.isActive)
                            styleClass += "disabled"
                    }
                }
                runOnUiThread { rootPane.children += contentPane; spinner.isVisible = false }
                it["list"] = list
            }
        }.subscribe(Schedulers.javafx()) { variables ->
            parentTab.selectionModel.selectedItemProperty().onChangeAndOperateWeakly {
                if (it?.userData == this) {
                    modPane.children.setAll(variables.get<List<ModItem>>("list"))
                }
            }
        }
    }

    fun onAdd() {
        val chooser = FileChooser()
        chooser.title = i18n("mods.choose_mod")
        chooser.extensionFilters.setAll(FileChooser.ExtensionFilter("Mod", "*.jar", "*.zip", "*.litemod"))
        val res = chooser.showOpenDialog(Controllers.stage) ?: return
        task { modManager.addMod(versionId, res) }
                .subscribe(task(Schedulers.javafx()) { loadMods(modManager, versionId) })
    }
}