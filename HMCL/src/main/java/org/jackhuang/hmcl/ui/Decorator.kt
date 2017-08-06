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
import com.jfoenix.effects.JFXDepthManager
import com.jfoenix.svg.SVGGlyph
import javafx.animation.*
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.Insets
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.layout.BorderStroke
import org.jackhuang.hmcl.util.*

class Decorator @JvmOverloads constructor(private val primaryStage: Stage, node: Node, private val max: Boolean = true, min: Boolean = true) : GridPane() {
    private var xOffset: Double = 0.0
    private var yOffset: Double = 0.0
    private var newX: Double = 0.0
    private var newY: Double = 0.0
    private var initX: Double = 0.0
    private var initY: Double = 0.0
    private var allowMove: Boolean = false
    private var isDragging: Boolean = false
    private var windowDecoratorAnimation: Timeline? = null
    @FXML lateinit var contentPlaceHolder: StackPane
    @FXML lateinit var titleContainer: BorderPane
    @FXML lateinit var buttonsContainer: HBox
    @FXML lateinit var backNavButton: JFXButton
    @FXML lateinit var refreshNavButton: JFXButton
    @FXML lateinit var closeNavButton: JFXButton
    @FXML lateinit var refreshMenuButton: JFXButton
    @FXML lateinit var addMenuButton: JFXButton
    @FXML lateinit var titleLabel: Label
    @FXML lateinit var leftPane: VBox

    private val onCloseButtonActionProperty: ObjectProperty<Runnable> = SimpleObjectProperty(Runnable { this.primaryStage.close() })
        @JvmName("onCloseButtonActionProperty") get
    var onCloseButtonAction: Runnable by onCloseButtonActionProperty

    val customMaximizeProperty: BooleanProperty = SimpleBooleanProperty(false)
        @JvmName("customMaximizeProperty") get
    var isCustomMaximize: Boolean by customMaximizeProperty

    private var maximized: Boolean = false
    private var originalBox: BoundingBox? = null
    private var maximizedBox: BoundingBox? = null
    @FXML lateinit var btnMin: JFXButton
    @FXML lateinit var btnMax: JFXButton
    @FXML lateinit var btnClose: JFXButton
    private val minus = SVGGlyph(0, "MINUS", "M804.571 420.571v109.714q0 22.857-16 38.857t-38.857 16h-694.857q-22.857 0-38.857-16t-16-38.857v-109.714q0-22.857 16-38.857t38.857-16h694.857q22.857 0 38.857 16t16 38.857z", Color.WHITE)
            .apply { setSize(12.0, 2.0); translateY = 4.0 }
    private val resizeMax = SVGGlyph(0, "RESIZE_MAX", "M726 810v-596h-428v596h428zM726 44q34 0 59 25t25 59v768q0 34-25 60t-59 26h-428q-34 0-59-26t-25-60v-768q0-34 25-60t59-26z", Color.WHITE)
            .apply { setPrefSize(12.0, 12.0); setSize(12.0, 12.0) }
    private val resizeMin = SVGGlyph(0, "RESIZE_MIN", "M80.842 943.158v-377.264h565.894v377.264h-565.894zM0 404.21v619.79h727.578v-619.79h-727.578zM377.264 161.684h565.894v377.264h-134.736v80.842h215.578v-619.79h-727.578v323.37h80.842v-161.686z", Color.WHITE)
            .apply { setPrefSize(12.0, 12.0); setSize(12.0, 12.0) }
    private val close = SVGGlyph(0, "CLOSE", "M810 274l-238 238 238 238-60 60-238-238-238 238-60-60 238-238-238-238 60-60 238 238 238-238z", Color.WHITE)
            .apply { setPrefSize(12.0, 12.0); setSize(12.0, 12.0) }

    init {
        loadFXML("/assets/fxml/decorator.fxml")

        this.primaryStage.initStyle(StageStyle.UNDECORATED)
        btnClose.graphic = close
        btnMin.graphic = minus
        btnMax.graphic = resizeMax

        buttonsContainer.background = Background(*arrayOf(BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)))
        titleContainer.addEventHandler(MouseEvent.MOUSE_CLICKED) { mouseEvent ->
            if (mouseEvent.clickCount == 2) {
                btnMax.fire()
            }
        }

        if (!min) buttonsContainer.children.remove(btnMin)
        if (!max) buttonsContainer.children.remove(btnMax)

        JFXDepthManager.setDepth(titleContainer, 1)
        titleContainer.addEventHandler(MouseEvent.MOUSE_ENTERED) { this.allowMove = true }
        titleContainer.addEventHandler(MouseEvent.MOUSE_EXITED) { if (!this.isDragging) this.allowMove = false }

        this.contentPlaceHolder.children.add(node)
        (node as Region).setMinSize(0.0, 0.0)
        this.border = Border(BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths(0.0, 4.0, 4.0, 4.0)))
        val clip = Rectangle()
        clip.widthProperty().bind(node.widthProperty())
        clip.heightProperty().bind(node.heightProperty())
        node.setClip(clip)
    }

    fun onMouseMoved(mouseEvent: MouseEvent) {
        if (!this.primaryStage.isMaximized && !this.primaryStage.isFullScreen && !this.maximized) {
            if (!this.primaryStage.isResizable) {
                this.updateInitMouseValues(mouseEvent)
            } else {
                val x = mouseEvent.x
                val y = mouseEvent.y
                val boundsInParent = this.boundsInParent
                if (this.border != null && this.border.strokes.size > 0) {
                    val borderWidth = this.contentPlaceHolder.snappedLeftInset()
                    if (this.isRightEdge(x, y, boundsInParent)) {
                        if (y < borderWidth) {
                            this.cursor = Cursor.NE_RESIZE
                        } else if (y > this.height - borderWidth) {
                            this.cursor = Cursor.SE_RESIZE
                        } else {
                            this.cursor = Cursor.E_RESIZE
                        }
                    } else if (this.isLeftEdge(x, y, boundsInParent)) {
                        if (y < borderWidth) {
                            this.cursor = Cursor.NW_RESIZE
                        } else if (y > this.height - borderWidth) {
                            this.cursor = Cursor.SW_RESIZE
                        } else {
                            this.cursor = Cursor.W_RESIZE
                        }
                    } else if (this.isTopEdge(x, y, boundsInParent)) {
                        this.cursor = Cursor.N_RESIZE
                    } else if (this.isBottomEdge(x, y, boundsInParent)) {
                        this.cursor = Cursor.S_RESIZE
                    } else {
                        this.cursor = Cursor.DEFAULT
                    }

                    this.updateInitMouseValues(mouseEvent)
                }

            }
        } else {
            this.cursor = Cursor.DEFAULT
        }
    }

    fun onMouseReleased() {
        this.isDragging = false
    }

    fun onMouseDragged(mouseEvent: MouseEvent) {
        this.isDragging = true
        if (mouseEvent.isPrimaryButtonDown && (this.xOffset != -1.0 || this.yOffset != -1.0)) {
            if (!this.primaryStage.isFullScreen && !mouseEvent.isStillSincePress && !this.primaryStage.isMaximized && !this.maximized) {
                this.newX = mouseEvent.screenX
                this.newY = mouseEvent.screenY
                val deltax = this.newX - this.initX
                val deltay = this.newY - this.initY
                val cursor = this.cursor
                if (Cursor.E_RESIZE == cursor) {
                    this.setStageWidth(this.primaryStage.width + deltax)
                    mouseEvent.consume()
                } else if (Cursor.NE_RESIZE == cursor) {
                    if (this.setStageHeight(this.primaryStage.height - deltay)) {
                        this.primaryStage.y = this.primaryStage.y + deltay
                    }

                    this.setStageWidth(this.primaryStage.width + deltax)
                    mouseEvent.consume()
                } else if (Cursor.SE_RESIZE == cursor) {
                    this.setStageWidth(this.primaryStage.width + deltax)
                    this.setStageHeight(this.primaryStage.height + deltay)
                    mouseEvent.consume()
                } else if (Cursor.S_RESIZE == cursor) {
                    this.setStageHeight(this.primaryStage.height + deltay)
                    mouseEvent.consume()
                } else if (Cursor.W_RESIZE == cursor) {
                    if (this.setStageWidth(this.primaryStage.width - deltax)) {
                        this.primaryStage.x = this.primaryStage.x + deltax
                    }

                    mouseEvent.consume()
                } else if (Cursor.SW_RESIZE == cursor) {
                    if (this.setStageWidth(this.primaryStage.width - deltax)) {
                        this.primaryStage.x = this.primaryStage.x + deltax
                    }

                    this.setStageHeight(this.primaryStage.height + deltay)
                    mouseEvent.consume()
                } else if (Cursor.NW_RESIZE == cursor) {
                    if (this.setStageWidth(this.primaryStage.width - deltax)) {
                        this.primaryStage.x = this.primaryStage.x + deltax
                    }

                    if (this.setStageHeight(this.primaryStage.height - deltay)) {
                        this.primaryStage.y = this.primaryStage.y + deltay
                    }

                    mouseEvent.consume()
                } else if (Cursor.N_RESIZE == cursor) {
                    if (this.setStageHeight(this.primaryStage.height - deltay)) {
                        this.primaryStage.y = this.primaryStage.y + deltay
                    }

                    mouseEvent.consume()
                } else if (this.allowMove) {
                    this.primaryStage.x = mouseEvent.screenX - this.xOffset
                    this.primaryStage.y = mouseEvent.screenY - this.yOffset
                    mouseEvent.consume()
                }

            }
        }
    }

    fun onMin() {
        this.primaryStage.isIconified = true
    }

    fun onMax() {
        if (!max) return
        if (!this.isCustomMaximize) {
            this.primaryStage.isMaximized = !this.primaryStage.isMaximized
            this.maximized = this.primaryStage.isMaximized
            if (this.primaryStage.isMaximized) {
                this.btnMax.graphic = resizeMin
                this.btnMax.tooltip = Tooltip("Restore Down")
            } else {
                this.btnMax.graphic = resizeMax
                this.btnMax.tooltip = Tooltip("Maximize")
            }
        } else {
            if (!this.maximized) {
                this.originalBox = BoundingBox(primaryStage.x, primaryStage.y, primaryStage.width, primaryStage.height)
                val screen = Screen.getScreensForRectangle(primaryStage.x, primaryStage.y, primaryStage.width, primaryStage.height)[0] as Screen
                val bounds = screen.visualBounds
                this.maximizedBox = BoundingBox(bounds.minX, bounds.minY, bounds.width, bounds.height)
                primaryStage.x = this.maximizedBox!!.minX
                primaryStage.y = this.maximizedBox!!.minY
                primaryStage.width = this.maximizedBox!!.width
                primaryStage.height = this.maximizedBox!!.height
                this.btnMax.graphic = resizeMin
                this.btnMax.tooltip = Tooltip("Restore Down")
            } else {
                primaryStage.x = this.originalBox!!.minX
                primaryStage.y = this.originalBox!!.minY
                primaryStage.width = this.originalBox!!.width
                primaryStage.height = this.originalBox!!.height
                this.originalBox = null
                this.btnMax.graphic = resizeMax
                this.btnMax.tooltip = Tooltip("Maximize")
            }

            this.maximized = !this.maximized
        }
    }

    fun onClose() {
        this.onCloseButtonAction.run()
    }

    private fun updateInitMouseValues(mouseEvent: MouseEvent) {
        this.initX = mouseEvent.screenX
        this.initY = mouseEvent.screenY
        this.xOffset = mouseEvent.sceneX
        this.yOffset = mouseEvent.sceneY
    }

    private fun isRightEdge(x: Double, y: Double, boundsInParent: Bounds): Boolean {
        return x < this.width && x > this.width - this.contentPlaceHolder.snappedLeftInset()
    }

    private fun isTopEdge(x: Double, y: Double, boundsInParent: Bounds): Boolean {
        return y >= 0.0 && y < this.contentPlaceHolder.snappedLeftInset()
    }

    private fun isBottomEdge(x: Double, y: Double, boundsInParent: Bounds): Boolean {
        return y < this.height && y > this.height - this.contentPlaceHolder.snappedLeftInset()
    }

    private fun isLeftEdge(x: Double, y: Double, boundsInParent: Bounds): Boolean {
        return x >= 0.0 && x < this.contentPlaceHolder.snappedLeftInset()
    }

    internal fun setStageWidth(width: Double): Boolean {
        if (width >= this.primaryStage.minWidth && width >= this.titleContainer.minWidth) {
            this.primaryStage.width = width
            this.initX = this.newX
            return true
        } else {
            if (width >= this.primaryStage.minWidth && width <= this.titleContainer.minWidth) {
                this.primaryStage.width = this.titleContainer.minWidth
            }

            return false
        }
    }

    internal fun setStageHeight(height: Double): Boolean {
        if (height >= this.primaryStage.minHeight && height >= this.titleContainer.height) {
            this.primaryStage.height = height
            this.initY = this.newY
            return true
        } else {
            if (height >= this.primaryStage.minHeight && height <= this.titleContainer.height) {
                this.primaryStage.height = this.titleContainer.height
            }

            return false
        }
    }

    fun setMaximized(maximized: Boolean) {
        if (this.maximized != maximized) {
            Platform.runLater { this.btnMax.fire() }
        }
    }

    fun setContent(content: Node) {
        this.contentPlaceHolder.children.setAll(content)
    }
}
