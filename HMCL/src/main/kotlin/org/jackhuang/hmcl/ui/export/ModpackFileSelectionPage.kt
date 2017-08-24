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
package org.jackhuang.hmcl.ui.export

import com.jfoenix.controls.JFXTreeView
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.CheckBoxTreeItem
import javafx.scene.control.Label
import javafx.scene.control.TreeItem
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.game.ModAdviser
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel
import org.jackhuang.hmcl.ui.loadFXML
import org.jackhuang.hmcl.ui.wizard.WizardController
import org.jackhuang.hmcl.ui.wizard.WizardPage
import java.io.File
import java.util.*

class ModpackFileSelectionPage(private val controller: WizardController, profile: Profile, private val version: String, private val adviser: ModAdviser): StackPane(), WizardPage {
    override val title: String = i18n("modpack.wizard.step.2.title")
    @FXML lateinit var treeView: JFXTreeView<String>
    private val rootNode: CheckBoxTreeItem<String>?
    init {
        loadFXML("/assets/fxml/modpack/selection.fxml")

        rootNode = getTreeItem(profile.repository.getRunDirectory(version), "minecraft")
        treeView.root = rootNode
        treeView.selectionModel = NoneMultipleSelectionModel<TreeItem<String>>()
    }

    private fun getTreeItem(file: File, basePath: String): CheckBoxTreeItem<String>? {
        var state = ModAdviser.ModSuggestion.SUGGESTED
        if (basePath.length > "minecraft/".length) {
            state = adviser.advise(basePath.substringAfter("minecraft/") + (if (file.isDirectory) "/" else ""), file.isDirectory)
            if (file.isFile && file.nameWithoutExtension == version)
                state = ModAdviser.ModSuggestion.HIDDEN
            if (file.isDirectory && file.name == version + "-natives")
                state = ModAdviser.ModSuggestion.HIDDEN
            if (state == ModAdviser.ModSuggestion.HIDDEN)
                return null
        }

        val node = CheckBoxTreeItem<String>(basePath.substringAfterLast("/"))
        if (state == ModAdviser.ModSuggestion.SUGGESTED)
            node.isSelected = true

        if (file.isDirectory) {
            file.listFiles()?.forEach {
                val subNode = getTreeItem(it, basePath + "/" + it.name)
                if (subNode != null) {
                    node.isSelected = subNode.isSelected or node.isSelected
                    if (!subNode.isSelected)
                        node.isIndeterminate = true
                    node.children += subNode
                }
            }
            if (!node.isSelected) node.isIndeterminate = false

            // Empty folder need not to be displayed.
            if (node.children.isEmpty())
                return null
        }

        return node.apply {
            graphic = HBox().apply {
                val checkbox = CheckBox()
                checkbox.selectedProperty().bindBidirectional(node.selectedProperty())
                checkbox.indeterminateProperty().bindBidirectional(node.indeterminateProperty())
                children += checkbox
                if (TRANSLATION.containsKey(basePath))
                children += Label().apply {
                    text = TRANSLATION[basePath]
                    style = "-fx-text-fill: gray;"
                    isMouseTransparent = true
                }
                isPickOnBounds = false
                isExpanded = basePath == "minecraft"
            }
        }
    }

    private fun getFilesNeeded(node: CheckBoxTreeItem<String>?, basePath: String, list: MutableList<String>) {
        if (node == null)
            return
        if (node.isSelected) {
            if (basePath.length > "minecraft/".length)
                list += basePath.substring("minecraft/".length)
            for (child in node.children)
                getFilesNeeded(child as? CheckBoxTreeItem<String>?, basePath + "/" + child.value, list)
            return
        }
    }

    override fun cleanup(settings: MutableMap<String, Any>) {
        controller.settings.remove(MODPACK_FILE_SELECTION)
    }

    fun onNext() {
        val list = LinkedList<String>()
        getFilesNeeded(rootNode, "minecraft", list)
        controller.settings[MODPACK_FILE_SELECTION] = list
        controller.onFinish()
    }

    companion object {
        val MODPACK_FILE_SELECTION = "modpack.accepted"

        private val TRANSLATION = mapOf(
                "minecraft/servers.dat" to i18n("modpack.files.servers_dat"),
                "minecraft/saves" to i18n("modpack.files.saves"),
                "minecraft/mods" to i18n("modpack.files.mods"),
                "minecraft/config" to i18n("modpack.files.config"),
                "minecraft/liteconfig" to i18n("modpack.files.liteconfig"),
                "minecraft/resourcepacks" to i18n("modpack.files.resourcepacks"),
                "minecraft/resources" to i18n("modpack.files.resourcepacks"),
                "minecraft/options.txt" to i18n("modpack.files.options_txt"),
                "minecraft/optionsshaders.txt" to i18n("modpack.files.optionsshaders_txt"),
                "minecraft/mods/VoxelMods" to i18n("modpack.files.mods.voxelmods"),
                "minecraft/dumps" to i18n("modpack.files.dumps"),
                "minecraft/blueprints" to i18n("modpack.files.blueprints"),
                "minecraft/scripts" to i18n("modpack.files.scripts")
        )
    }
}