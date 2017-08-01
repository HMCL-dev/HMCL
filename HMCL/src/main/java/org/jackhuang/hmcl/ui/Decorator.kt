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
import com.jfoenix.svg.SVGGlyph
import javafx.animation.*
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.util.ArrayList

class Decorator @JvmOverloads constructor(private val primaryStage: Stage, node: Node, max: Boolean = true, min: Boolean = true) : VBox() {
    private var xOffset: Double = 0.0
    private var yOffset: Double = 0.0
    private var newX: Double = 0.0
    private var newY: Double = 0.0
    private var initX: Double = 0.0
    private var initY: Double = 0.0
    private var allowMove: Boolean = false
    private var isDragging: Boolean = false
    private var windowDecoratorAnimation: Timeline? = null
    private val contentPlaceHolder: StackPane
    private val titleContainer: BorderPane
    private val onCloseButtonAction: ObjectProperty<Runnable>
    private val customMaximize: BooleanProperty
    private var maximized: Boolean = false
    private var originalBox: BoundingBox? = null
    private var maximizedBox: BoundingBox? = null
    private val btnMax: JFXButton

    init {
        this.xOffset = 0.0
        this.yOffset = 0.0
        this.allowMove = false
        this.isDragging = false
        this.contentPlaceHolder = StackPane()
        this.onCloseButtonAction = SimpleObjectProperty(Runnable { this.primaryStage.close() })
        this.customMaximize = SimpleBooleanProperty(false)
        this.maximized = false
        this.primaryStage.initStyle(StageStyle.UNDECORATED)
        this.isPickOnBounds = false
        this.styleClass.add("jfx-decorator")
        val minus = SVGGlyph(0, "MINUS", "M804.571 420.571v109.714q0 22.857-16 38.857t-38.857 16h-694.857q-22.857 0-38.857-16t-16-38.857v-109.714q0-22.857 16-38.857t38.857-16h694.857q22.857 0 38.857 16t16 38.857z", Color.WHITE)
        minus.setSize(12.0, 2.0)
        minus.translateY = 4.0
        val resizeMax = SVGGlyph(0, "RESIZE_MAX", "M726 810v-596h-428v596h428zM726 44q34 0 59 25t25 59v768q0 34-25 60t-59 26h-428q-34 0-59-26t-25-60v-768q0-34 25-60t59-26z", Color.WHITE)
        resizeMax.setSize(12.0, 12.0)
        val resizeMin = SVGGlyph(0, "RESIZE_MIN", "M80.842 943.158v-377.264h565.894v377.264h-565.894zM0 404.21v619.79h727.578v-619.79h-727.578zM377.264 161.684h565.894v377.264h-134.736v80.842h215.578v-619.79h-727.578v323.37h80.842v-161.686z", Color.WHITE)
        resizeMin.setSize(12.0, 12.0)
        val close = SVGGlyph(0, "CLOSE", "M810 274l-238 238 238 238-60 60-238-238-238 238-60-60 238-238-238-238 60-60 238 238 238-238z", Color.WHITE)
        close.setSize(12.0, 12.0)
        val btnClose = JFXButton()
        btnClose.styleClass.add("jfx-decorator-button")
        btnClose.cursor = Cursor.HAND
        btnClose.setOnAction { action -> (this.onCloseButtonAction.get() as Runnable).run() }
        btnClose.graphic = close
        btnClose.ripplerFill = Color.WHITE
        val btnMin = JFXButton()
        btnMin.styleClass.add("jfx-decorator-button")
        btnMin.cursor = Cursor.HAND
        btnMin.setOnAction { action -> this.primaryStage.isIconified = true }
        btnMin.graphic = minus
        btnMin.ripplerFill = Color.WHITE
        this.btnMax = JFXButton()
        this.btnMax.styleClass.add("jfx-decorator-button")
        this.btnMax.cursor = Cursor.HAND
        this.btnMax.ripplerFill = Color.WHITE
        this.btnMax.setOnAction { action ->
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
        this.btnMax.graphic = resizeMax
        titleContainer = BorderPane()
        titleContainer.styleClass += "jfx-decorator-buttons-container"
        titleContainer.isPickOnBounds = false
        val titleWrapper = HBox()
        titleWrapper.style += "-fx-padding: 15;"
        titleWrapper.alignment = Pos.CENTER_LEFT
        val title = Label("Hello Minecraft! Launcher")
        title.alignment = Pos.CENTER_LEFT
        title.style += "--fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 15px;"
        title.isMouseTransparent = false
        titleWrapper.children.setAll(title)
        titleContainer.left = titleWrapper

        val buttonsContainer = HBox()
        buttonsContainer.styleClass.add("jfx-decorator-buttons-container")
        buttonsContainer.background = Background(*arrayOf(BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)))
        buttonsContainer.padding = Insets(4.0)
        buttonsContainer.alignment = Pos.CENTER_RIGHT
        titleContainer.addEventHandler(MouseEvent.MOUSE_CLICKED) { mouseEvent ->
            if (mouseEvent.clickCount == 2) {
                this.btnMax.fire()
            }

        }
        val btns = ArrayList<Button>()

        if (min) {
            btns.add(btnMin)
        }

        if (max) {
            btns.add(this.btnMax)
        }

        btns.add(btnClose)
        buttonsContainer.children.addAll(btns)
        titleContainer.addEventHandler(MouseEvent.MOUSE_ENTERED) { enter -> this.allowMove = true }
        titleContainer.addEventHandler(MouseEvent.MOUSE_EXITED) { enter ->
            if (!this.isDragging) {
                this.allowMove = false
            }

        }
        buttonsContainer.minWidth = 180.0

        titleContainer.right = buttonsContainer
        this.contentPlaceHolder.styleClass.add("jfx-decorator-content-container")
        this.contentPlaceHolder.setMinSize(0.0, 0.0)
        this.contentPlaceHolder.children.add(node)
        (node as Region).setMinSize(0.0, 0.0)
        VBox.setVgrow(this.contentPlaceHolder, Priority.ALWAYS)
        this.contentPlaceHolder.styleClass.add("resize-border")
        this.contentPlaceHolder.border = Border(BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths(0.0, 4.0, 4.0, 4.0)))
        val clip = Rectangle()
        clip.widthProperty().bind(node.widthProperty())
        clip.heightProperty().bind(node.heightProperty())
        node.setClip(clip)
        this.children.addAll(titleContainer, this.contentPlaceHolder)
        this.setOnMouseMoved { mouseEvent ->
            if (!this.primaryStage.isMaximized && !this.primaryStage.isFullScreen && !this.maximized) {
                if (!this.primaryStage.isResizable) {
                    this.updateInitMouseValues(mouseEvent)
                } else {
                    val x = mouseEvent.x
                    val y = mouseEvent.y
                    val boundsInParent = this.boundsInParent
                    if (this.contentPlaceHolder.border != null && this.contentPlaceHolder.border.strokes.size > 0) {
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
        this.setOnMouseReleased { mouseEvent -> this.isDragging = false }
        this.setOnMouseDragged { mouseEvent ->
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

    fun setOnCloseButtonAction(onCloseButtonAction: Runnable) {
        this.onCloseButtonAction.set(onCloseButtonAction)
    }

    fun customMaximizeProperty(): BooleanProperty {
        return this.customMaximize
    }

    var isCustomMaximize: Boolean
        get() = this.customMaximizeProperty().get()
        set(customMaximize) = this.customMaximizeProperty().set(customMaximize)

    fun setMaximized(maximized: Boolean) {
        if (this.maximized != maximized) {
            Platform.runLater { this.btnMax.fire() }
        }

    }

    fun setContent(content: Node) {
        this.contentPlaceHolder.children.setAll(content)
    }
}
