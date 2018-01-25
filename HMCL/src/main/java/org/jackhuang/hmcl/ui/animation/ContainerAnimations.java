/*
 * Hello Minecraft! Launcher.
 * Copyright c 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.Collections;

public enum ContainerAnimations {
    NONE(c -> Collections.emptyList()),
    /**
     * A fade between the old and new view
     */
    FADE(c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getPreviousNode().opacityProperty(), 1.0D, Interpolator.EASE_BOTH),
                            new KeyValue(c.getCurrentNode().opacityProperty(), 0.0D, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 0.0D, Interpolator.EASE_BOTH),
                            new KeyValue(c.getCurrentNode().opacityProperty(), 1.0D, Interpolator.EASE_BOTH)))),
    /**
     * A zoom effect
     */
    ZOOM_IN(c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 1.0D, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 4, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 4, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 0, Interpolator.EASE_BOTH)))),
    /**
     * A zoom effect
     */
    ZOOM_OUT(c ->
            (Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 1.0D, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 0, Interpolator.EASE_BOTH))))),
    /**
     * A swipe effect
     */
    SWIPE_LEFT(c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getCurrentNode().translateXProperty(), c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), 0, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getCurrentNode().translateXProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), -c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH)))),

    /**
     * A swipe effect
     */
    SWIPE_RIGHT(c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getCurrentNode().translateXProperty(), -c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), 0, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getCurrentNode().translateXProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH))));

    private AnimationProducer animationProducer;

    ContainerAnimations(AnimationProducer animationProducer) {
        this.animationProducer = animationProducer;
    }

    public AnimationProducer getAnimationProducer() {
        return animationProducer;
    }
}
