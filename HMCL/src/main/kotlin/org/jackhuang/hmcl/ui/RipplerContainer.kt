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

import com.jfoenix.controls.JFXRippler
import javafx.animation.Transition
import javafx.beans.DefaultProperty
import javafx.beans.NamedArg
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import org.jackhuang.hmcl.util.getValue
import org.jackhuang.hmcl.util.setValue
import java.util.concurrent.Callable

@DefaultProperty("container")
open class RipplerContainer(@NamedArg("container") container: Node): StackPane() {
    val containerProperty = SimpleObjectProperty<Node>(this, "container", null)
        @JvmName("containerProperty") get
    var container: Node by containerProperty

    val ripplerFillProperty = SimpleObjectProperty<Paint>(this, "ripplerFill", null)
        @JvmName("ripplerFillProperty") get
    var ripplerFill: Paint? by ripplerFillProperty

    val selectedProperty = SimpleBooleanProperty(this, "selected", false)
        @JvmName("selectedProperty") get
    var selected: Boolean by selectedProperty

    private val buttonContainer = StackPane()
    private val buttonRippler = object : JFXRippler(StackPane()) {
        override fun getMask(): Node {
            val mask = StackPane()
            mask.shapeProperty().bind(buttonContainer.shapeProperty())
            mask.backgroundProperty().bind(Bindings.createObjectBinding<Background>(Callable<Background> { Background(BackgroundFill(Color.WHITE, if (buttonContainer.backgroundProperty().get() != null && buttonContainer.getBackground().getFills().size > 0) buttonContainer.background.fills[0].radii else defaultRadii, if (buttonContainer.backgroundProperty().get() != null && buttonContainer.background.fills.size > 0) buttonContainer.background.fills[0].insets else Insets.EMPTY)) }, buttonContainer.backgroundProperty()))
            mask.resize(buttonContainer.width - buttonContainer.snappedRightInset() - buttonContainer.snappedLeftInset(), buttonContainer.height - buttonContainer.snappedBottomInset() - buttonContainer.snappedTopInset())
            return mask
        }

        override fun initListeners() {
            this.ripplerPane.setOnMousePressed { event ->
                if (releaseManualRippler != null) {
                    releaseManualRippler!!.run()
                }

                releaseManualRippler = null
                this.createRipple(event.x, event.y)
            }
        }
    }
    private var clickedAnimation: Transition? = null
    private val defaultRadii = CornerRadii(3.0)
    private var invalid = true
    private var releaseManualRippler: Runnable? = null

    init {
        styleClass += "rippler-container"
        this.container = container
        this.buttonContainer.children.add(this.buttonRippler)
        setOnMousePressed {
            if (this.clickedAnimation != null) {
                this.clickedAnimation!!.rate = 1.0
                this.clickedAnimation!!.play()
            }

        }
        setOnMouseReleased {
            if (this.clickedAnimation != null) {
                this.clickedAnimation!!.rate = -1.0
                this.clickedAnimation!!.play()
            }

        }
        focusedProperty().addListener { _, _, newVal ->
            if (newVal) {
                if (!isPressed) {
                    this.buttonRippler.showOverlay()
                }
            } else {
                this.buttonRippler.hideOverlay()
            }

        }
        pressedProperty().addListener { _ -> this.buttonRippler.hideOverlay() }
        isPickOnBounds = false
        this.buttonContainer.isPickOnBounds = false
        this.buttonContainer.shapeProperty().bind(shapeProperty())
        this.buttonContainer.borderProperty().bind(borderProperty())
        this.buttonContainer.backgroundProperty().bind(Bindings.createObjectBinding<Background>(Callable<Background> {
            if (background == null || this.isJavaDefaultBackground(background) || this.isJavaDefaultClickedBackground(background)) {
                background = Background(BackgroundFill(Color.TRANSPARENT, defaultRadii, null))
            }

            try {
                return@Callable(
                        if (background != null && (background.fills[0] as BackgroundFill).insets == Insets(-0.2, -0.2, -0.2, -0.2))
                            Background(BackgroundFill((if (background != null) (background.fills[0] as BackgroundFill).fill else Color.TRANSPARENT) as Paint,
                                    if (backgroundProperty().get() != null) background.fills[0].radii else defaultRadii, Insets.EMPTY))
                        else
                            Background(BackgroundFill((if (background != null) background.fills[0].fill else Color.TRANSPARENT) as Paint,
                                    if (background != null) background.fills[0].radii else defaultRadii, Insets.EMPTY))
                        )
            } catch (var3: Exception) {
                return@Callable background
            }
        }, backgroundProperty()))
        ripplerFillProperty.addListener { o, oldVal, newVal -> this.buttonRippler.ripplerFill = newVal }
        if (background == null || this.isJavaDefaultBackground(background)) {
            background = Background(BackgroundFill(Color.TRANSPARENT, this.defaultRadii, null))
        }

        this.updateChildren()

        containerProperty.addListener { _ -> updateChildren() }
        selectedProperty.addListener { _ ->
            if (selected) background = Background(BackgroundFill(ripplerFill, defaultRadii, null))
            else background = Background(BackgroundFill(Color.TRANSPARENT, defaultRadii, null))
        }

        shape = Rectangle().apply {
            widthProperty().bind(this@RipplerContainer.widthProperty())
            heightProperty().bind(this@RipplerContainer.heightProperty())
        }
    }

    protected fun updateChildren() {
        children.add(container)

        if (this.buttonContainer != null) {
            this.children.add(0, this.buttonContainer)
        }

        for (i in 1..this.children.size - 1) {
            this.children[i].isPickOnBounds = false
        }

    }

    fun layoutChildren(x: Double, y: Double, w: Double, h: Double) {
        if (this.invalid) {
            if (ripplerFill == null) {
                for (i in this.children.size - 1 downTo 1) {
                    if (this.children[i] is Shape) {
                        this.buttonRippler.ripplerFill = (this.children[i] as Shape).fill
                        (this.children[i] as Shape).fillProperty().addListener { o, oldVal, newVal -> this.buttonRippler.ripplerFill = newVal }
                        break
                    }

                    if (this.children[i] is Label) {
                        this.buttonRippler.ripplerFill = (this.children[i] as Label).textFill
                        (this.children[i] as Label).textFillProperty().addListener { o, oldVal, newVal -> this.buttonRippler.ripplerFill = newVal }
                        break
                    }
                }
            } else {
                this.buttonRippler.ripplerFill = ripplerFill
            }

            this.invalid = false
        }

        val shift = 1.0
        this.buttonContainer.resizeRelocate(layoutBounds.minX - shift, layoutBounds.minY - shift, width + 2.0 * shift, height + 2.0 * shift)
        //this.layoutLabelInArea(x, y, w, h)
    }

    private fun isJavaDefaultBackground(background: Background): Boolean {
        try {
            val firstFill = (background.fills[0] as BackgroundFill).fill.toString()
            return "0xffffffba" == firstFill || "0xffffffbf" == firstFill || "0xffffffbd" == firstFill
        } catch (var3: Exception) {
            return false
        }

    }

    private fun isJavaDefaultClickedBackground(background: Background): Boolean {
        try {
            return "0x039ed3ff" == (background.fills[0] as BackgroundFill).fill.toString()
        } catch (var3: Exception) {
            return false
        }

    }
}