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
package org.jackhuang.hmcl.ui.construct

import javafx.beans.DefaultProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.jackhuang.hmcl.util.getValue
import org.jackhuang.hmcl.util.setValue
import kotlin.collections.plusAssign
import kotlin.collections.set

@DefaultProperty("content")
open class ComponentList: StackPane() {

    val vbox = VBox()

    val content: ObservableList<Node> = FXCollections.observableArrayList<Node>().apply {
        addListener { change: ListChangeListener.Change<out Node> ->
            while (change.next()) {
                for (i in change.from until change.to) {
                    addChildren(change.list[i])
                }
            }
        }
    }

    init {
        children.setAll(vbox)

        styleClass += "options-list"
    }

    fun addChildren(node: Node) {
        if (node is ComponentList) {
            node.properties["title"] = node.title
            node.properties["subtitle"] = node.subtitle
        }
        vbox.children += StackPane().apply {
            children += ComponentListCell(node)
            if (vbox.children.isEmpty())
                styleClass += "options-list-item-ahead"
            else {
                styleClass += "options-list-item"
            }
        }
    }

    val titleProperty = SimpleStringProperty(this, "title", "Group")
    var title: String by titleProperty

    val subtitleProperty = SimpleStringProperty(this, "subtitle", "")
    var subtitle: String by subtitleProperty

    var hasSubtitle: Boolean = false

    val depthProperty = SimpleIntegerProperty(this, "depth", 0)
    var depth: Int by depthProperty
}