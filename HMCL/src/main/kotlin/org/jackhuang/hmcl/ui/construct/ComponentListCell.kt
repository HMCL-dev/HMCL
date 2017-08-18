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

import com.jfoenix.controls.JFXButton
import javafx.animation.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import javafx.util.Duration
import org.jackhuang.hmcl.ui.SINE
import org.jackhuang.hmcl.ui.SVG
import org.jackhuang.hmcl.ui.limitHeight
import org.jackhuang.hmcl.util.*

class ComponentListCell(private val content: Node) : StackPane() {

    var expandAnimation: Animation? = null
    private var clipRect: Rectangle? = null
    private var animatedHeight = 0.0

    private val expandedProperty = SimpleBooleanProperty(this, "expanded", false)
    var expanded: Boolean by expandedProperty

    init {
        updateLayout()
    }

    private fun updateClip(newHeight: Double) {
        clipRect?.height = newHeight
    }

    override fun layoutChildren() {
        super.layoutChildren()

        if (clipRect == null) {
            clipRect = Rectangle(0.0, 0.0, width, height)
            //clip = clipRect
        } else {
            clipRect?.x = 0.0
            clipRect?.y = 0.0
            clipRect?.height = height
            clipRect?.width = width
        }
    }

    private fun updateLayout() {
        if (content is ComponentList) {
            content.styleClass -= "options-list"
            content.styleClass += "options-sublist"

            val groupNode = StackPane()
            groupNode.styleClass += "options-list-item-header"

            val expandIcon = SVG.expand("black", 10.0, 10.0)
            val expandButton = JFXButton()
            expandButton.graphic = expandIcon
            expandButton.styleClass += "options-list-item-expand-button"
            StackPane.setAlignment(expandButton, Pos.CENTER_RIGHT)

            val labelVBox = VBox()
            Label().apply {
                textProperty().bind(content.titleProperty)
                isMouseTransparent = true
                labelVBox.children += this
            }

            if (content.hasSubtitle)
                Label().apply {
                    textProperty().bind(content.subtitleProperty)
                    isMouseTransparent = true
                    styleClass += "subtitle-label"
                    labelVBox.children += this
                }

            StackPane.setAlignment(labelVBox, Pos.CENTER_LEFT)
            groupNode.children.setAll(
                    labelVBox,
                    expandButton)

            val container = VBox().apply {
                style += "-fx-padding: 8 0 0 0;"
                limitHeight(0.0)
                val clipRect = Rectangle()
                clipRect.widthProperty().bind(widthProperty())
                clipRect.heightProperty().bind(heightProperty())
                clip = clipRect
                children.setAll(content)
            }

            val holder = VBox()
            holder.children.setAll(groupNode, container)
            holder.styleClass += "options-list-item-container"

            expandButton.setOnMouseClicked {
                if (expandAnimation != null && expandAnimation!!.status == Animation.Status.RUNNING) {
                    expandAnimation!!.stop()
                }

                expanded = !expanded

                val newAnimatedHeight = content.prefHeight(-1.0) * (if (expanded) 1.0 else -1.0)
                val newHeight = if (expanded) height + newAnimatedHeight else prefHeight(-1.0)
                val contentHeight = if (expanded) newAnimatedHeight else 0.0

                if (expanded) {
                    updateClip(newHeight)
                }

                animatedHeight = newAnimatedHeight

                expandAnimation = Timeline(KeyFrame(Duration(320.0),
                        KeyValue(container.minHeightProperty(), contentHeight, SINE),
                        KeyValue(container.maxHeightProperty(), contentHeight, SINE)
                ))

                if (!expanded) {
                    expandAnimation?.setOnFinished {
                        updateClip(newHeight)
                        animatedHeight = 0.0
                    }
                }

                expandAnimation?.play()
            }

            expandedProperty.addListener { _, _, newValue ->
                if (newValue) {
                    expandIcon.rotate = 180.0
                } else {
                    expandIcon.rotate = 0.0
                }
            }

            children.setAll(holder)
        } else {
            children.setAll(content)
        }
    }
}