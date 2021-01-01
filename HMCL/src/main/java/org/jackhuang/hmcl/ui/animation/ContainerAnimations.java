/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public enum ContainerAnimations {
    NONE(c -> {
        c.getPreviousNode().setTranslateX(0);
        c.getPreviousNode().setTranslateY(0);
        c.getPreviousNode().setScaleX(1);
        c.getPreviousNode().setScaleY(1);
        c.getPreviousNode().setOpacity(1);
        c.getCurrentNode().setTranslateX(0);
        c.getCurrentNode().setTranslateY(0);
        c.getCurrentNode().setScaleX(1);
        c.getCurrentNode().setScaleY(1);
        c.getCurrentNode().setOpacity(1);
    }, c -> Collections.emptyList()),

    /**
     * A fade between the old and new view
     */
    FADE(c -> {
        c.getPreviousNode().setTranslateX(0);
        c.getPreviousNode().setTranslateY(0);
        c.getPreviousNode().setScaleX(1);
        c.getPreviousNode().setScaleY(1);
        c.getPreviousNode().setOpacity(1);
        c.getCurrentNode().setTranslateX(0);
        c.getCurrentNode().setTranslateY(0);
        c.getCurrentNode().setScaleX(1);
        c.getCurrentNode().setScaleY(1);
        c.getCurrentNode().setOpacity(0);
    }, c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getPreviousNode().opacityProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getCurrentNode().opacityProperty(), 0, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getCurrentNode().opacityProperty(), 1, Interpolator.EASE_BOTH)))),

    /**
     * A fade between the old and new view
     */
    FADE_IN(c -> {
        c.getCurrentNode().setTranslateX(0);
        c.getCurrentNode().setTranslateY(0);
        c.getCurrentNode().setScaleX(1);
        c.getCurrentNode().setScaleY(1);
        c.getCurrentNode().setOpacity(0);
    }, c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getCurrentNode().opacityProperty(), 0, FXUtils.SINE)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getCurrentNode().opacityProperty(), 1, FXUtils.SINE)))),

    /**
     * A fade between the old and new view
     */
    FADE_OUT(c -> {
        c.getCurrentNode().setTranslateX(0);
        c.getCurrentNode().setTranslateY(0);
        c.getCurrentNode().setScaleX(1);
        c.getCurrentNode().setScaleY(1);
        c.getCurrentNode().setOpacity(1);
    }, c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getCurrentNode().opacityProperty(), 1, FXUtils.SINE)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getCurrentNode().opacityProperty(), 0, FXUtils.SINE)))),
    /**
     * A zoom effect
     */
    ZOOM_IN(c -> {
        c.getPreviousNode().setTranslateX(0);
        c.getPreviousNode().setTranslateY(0);
        c.getPreviousNode().setScaleX(1);
        c.getPreviousNode().setScaleY(1);
        c.getPreviousNode().setOpacity(1);
        c.getCurrentNode().setTranslateX(0);
        c.getCurrentNode().setTranslateY(0);
    }, c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 1, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 4, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 4, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 0, Interpolator.EASE_BOTH)))),
    /**
     * A zoom effect
     */
    ZOOM_OUT(c -> {
        c.getPreviousNode().setTranslateX(0);
        c.getPreviousNode().setTranslateY(0);
        c.getPreviousNode().setScaleX(1);
        c.getPreviousNode().setScaleY(1);
        c.getPreviousNode().setOpacity(1);
        c.getCurrentNode().setTranslateX(0);
        c.getCurrentNode().setTranslateY(0);
    }, c ->
            (Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 1, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 1, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getPreviousNode().scaleXProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().scaleYProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().opacityProperty(), 0, Interpolator.EASE_BOTH))))),
    /**
     * A swipe effect
     */
    SWIPE_LEFT(c -> {
        c.getPreviousNode().setScaleX(1);
        c.getPreviousNode().setScaleY(1);
        c.getPreviousNode().setOpacity(0);
        c.getPreviousNode().setTranslateX(0);
        c.getCurrentNode().setScaleX(1);
        c.getCurrentNode().setScaleY(1);
        c.getCurrentNode().setOpacity(1);
        c.getCurrentNode().setTranslateX(c.getCurrentRoot().getWidth());
    }, c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getCurrentNode().translateXProperty(), c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), 0, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getCurrentNode().translateXProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), -c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH)))),

    /**
     * A swipe effect
     */
    SWIPE_RIGHT(c -> {
        c.getPreviousNode().setScaleX(1);
        c.getPreviousNode().setScaleY(1);
        c.getPreviousNode().setOpacity(0);
        c.getPreviousNode().setTranslateX(0);
        c.getCurrentNode().setScaleX(1);
        c.getCurrentNode().setScaleY(1);
        c.getCurrentNode().setOpacity(1);
        c.getCurrentNode().setTranslateX(-c.getCurrentRoot().getWidth());
    }, c ->
            Arrays.asList(new KeyFrame(Duration.ZERO,
                            new KeyValue(c.getCurrentNode().translateXProperty(), -c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), 0, Interpolator.EASE_BOTH)),
                    new KeyFrame(c.getDuration(),
                            new KeyValue(c.getCurrentNode().translateXProperty(), 0, Interpolator.EASE_BOTH),
                            new KeyValue(c.getPreviousNode().translateXProperty(), c.getCurrentRoot().getWidth(), Interpolator.EASE_BOTH))));

    private final AnimationProducer animationProducer;
    private ContainerAnimations opposite;

    ContainerAnimations(Consumer<AnimationHandler> init, Function<AnimationHandler, List<KeyFrame>> animationProducer) {
        this.animationProducer = new AnimationProducer() {
            @Override
            public void init(AnimationHandler handler) {
                init.accept(handler);
            }

            @Override
            public List<KeyFrame> animate(AnimationHandler handler) {
                return animationProducer.apply(handler);
            }

            @Override
            public @Nullable AnimationProducer opposite() {
                return opposite != null ? opposite.getAnimationProducer() : null;
            }
        };
    }

    public AnimationProducer getAnimationProducer() {
        return animationProducer;
    }

    public ContainerAnimations getOpposite() {
        return opposite;
    }

    static {
        NONE.opposite = NONE;
        FADE.opposite = FADE;
        SWIPE_LEFT.opposite = SWIPE_RIGHT;
        SWIPE_RIGHT.opposite = SWIPE_LEFT;
        FADE_IN.opposite = FADE_OUT;
        FADE_OUT.opposite = FADE_IN;
        ZOOM_IN.opposite = ZOOM_OUT;
        ZOOM_OUT.opposite = ZOOM_IN;
    }
}
