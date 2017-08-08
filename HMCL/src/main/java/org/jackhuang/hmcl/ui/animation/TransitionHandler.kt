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
package org.jackhuang.hmcl.ui.animation

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle
import javafx.util.Duration
import org.jackhuang.hmcl.ui.setOverflowHidden
import org.jackhuang.hmcl.ui.takeSnapshot

/**
 * @param view A stack pane that contains another control that is [Parent]
 */
class TransitionHandler(override val view: StackPane): AnimationHandler {
    private var animation: Timeline? = null

    override val snapshot = ImageView().apply {
        isPreserveRatio = true
        isSmooth = true
    }

    override lateinit var duration: Duration
        private set

    fun setContent(newView: Node, transition: AnimationProducer, duration: Duration = Duration.millis(320.0)) {
        this.duration = duration

        val prevAnimation = animation
        if (prevAnimation != null)
            prevAnimation.stop()

        updateContent(newView)

        val nowAnimation = Timeline().apply {
            keyFrames.addAll(transition(this@TransitionHandler))
            keyFrames.add(KeyFrame(duration, EventHandler {
                snapshot.image = null
                snapshot.x = 0.0
                snapshot.y = 0.0
                snapshot.isVisible = false
            }))
        }
        nowAnimation.play()
        animation = nowAnimation
    }

    private fun updateContent(newView: Node) {
        if (view.width > 0 && view.height > 0) {
            val content = view.children.firstOrNull()
            val image: WritableImage
            if (content != null && content is Parent) {
                view.children.setAll()
                image = takeSnapshot(content, view.width, view.height)
                view.children.setAll(content)
            } else image = view.snapshot(SnapshotParameters(), WritableImage(view.width.toInt(), view.height.toInt()))
            snapshot.image = image
            snapshot.fitWidth = view.width
            snapshot.fitHeight = view.height
        } else
            snapshot.image = null

        snapshot.isVisible = true
        snapshot.opacity = 1.0
        view.children.setAll(snapshot, newView)
        snapshot.toFront()
    }
}