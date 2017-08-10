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

import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.util.Duration

typealias AnimationProducer = (AnimationHandler) -> List<KeyFrame>

enum class ContainerAnimations(val animationProducer: AnimationProducer) {
    /**
     * None
     */
    NONE({
        emptyList()
    }),
    /**
     * A fade between the old and new view
     */
    FADE({ c ->
        listOf(
                KeyFrame(Duration.ZERO,
                        KeyValue(c.snapshot.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                KeyFrame(c.duration,
                        KeyValue(c.snapshot.opacityProperty(), 0.0, Interpolator.EASE_BOTH))
        )
    }),
    /**
     * A zoom effect
     */
    ZOOM_IN({ c ->
        listOf(
                KeyFrame(Duration.ZERO,
                        KeyValue(c.snapshot.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.scaleYProperty(), 1, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                KeyFrame(c.duration,
                        KeyValue(c.snapshot.scaleXProperty(), 4, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.scaleYProperty(), 4, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.opacityProperty(), 0, Interpolator.EASE_BOTH))
        )
    }),
    /**
     * A zoom effect
     */
    ZOOM_OUT({ c ->
        listOf(
                KeyFrame(Duration.ZERO,
                        KeyValue(c.snapshot.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.scaleYProperty(), 1, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                KeyFrame(c.duration,
                        KeyValue(c.snapshot.scaleXProperty(), 0, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.scaleYProperty(), 0, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.opacityProperty(), 0, Interpolator.EASE_BOTH))
        )
    }),
    /**
     * A swipe effect
     */
    SWIPE_LEFT({ c ->
        listOf(
                KeyFrame(Duration.ZERO,
                        KeyValue(c.view.translateXProperty(), c.view.getWidth(), Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.translateXProperty(), -c.view.getWidth(), Interpolator.EASE_BOTH)),
                KeyFrame(c.duration,
                        KeyValue(c.view.translateXProperty(), 0, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.translateXProperty(), -c.view.getWidth(), Interpolator.EASE_BOTH)
                ))
    }),
    /**
     * A swipe effect
     */
    SWIPE_RIGHT({ c ->
        listOf(
                KeyFrame(Duration.ZERO,
                        KeyValue(c.view.translateXProperty(), -c.view.getWidth(), Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.translateXProperty(), c.view.getWidth(), Interpolator.EASE_BOTH)),
                KeyFrame(c.duration,
                        KeyValue(c.view.translateXProperty(), 0, Interpolator.EASE_BOTH),
                        KeyValue(c.snapshot.translateXProperty(), c.view.getWidth(), Interpolator.EASE_BOTH))
        )
    })
}