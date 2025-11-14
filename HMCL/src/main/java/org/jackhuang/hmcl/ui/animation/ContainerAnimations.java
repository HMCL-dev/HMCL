/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020 huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

public enum ContainerAnimations implements TransitionPane.AnimationProducer {
    NONE {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline();
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return this;
        }
    },

    /**
     * A fade between the old and new view
     */
    FADE {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(previousNode.opacityProperty(), 1, interpolator),
                    new KeyValue(nextNode.opacityProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(previousNode.opacityProperty(), 0, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 1, interpolator)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return this;
        }
    },

    /**
     * A swipe effect
     */
    SWIPE_LEFT {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            super.init(container, previousNode, nextNode);
            nextNode.setTranslateX(container.getWidth());
        }

        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.translateXProperty(), container.getWidth(), interpolator),
                    new KeyValue(previousNode.translateXProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.translateXProperty(), -container.getWidth(), interpolator)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return SWIPE_RIGHT;
        }
    },

    /**
     * A swipe effect
     */
    SWIPE_RIGHT {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            super.init(container, previousNode, nextNode);
            nextNode.setTranslateX(-container.getWidth());
        }

        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.translateXProperty(), -container.getWidth(), interpolator),
                    new KeyValue(previousNode.translateXProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.translateXProperty(), container.getWidth(), interpolator)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return SWIPE_LEFT;
        }
    },

    /// @see <a href="https://m3.material.io/styles/motion/transitions/transition-patterns">Transitions - Material Design 3</a>
    FORWARD {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            double offset = container.getWidth() > 0 ? container.getWidth() * 0.2 : 50;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 0, interpolator)),
                    new KeyFrame(duration.multiply(0.5),
                            new KeyValue(previousNode.translateXProperty(), -offset, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 0, interpolator),

                            new KeyValue(nextNode.opacityProperty(), 0, interpolator),
                            new KeyValue(nextNode.translateXProperty(), offset, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator))
            );
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return BACKWARD;
        }
    },

    /// @see <a href="https://m3.material.io/styles/motion/transitions/transition-patterns">Transitions - Material Design 3</a>
    BACKWARD {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            double offset = container.getWidth() > 0 ? container.getWidth() * 0.2 : 50;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 0, interpolator)),
                    new KeyFrame(duration.multiply(0.5),
                            new KeyValue(previousNode.translateXProperty(), offset, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 0, interpolator),

                            new KeyValue(nextNode.opacityProperty(), 0, interpolator),
                            new KeyValue(nextNode.translateXProperty(), -offset, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator))
            );
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return FORWARD;
        }
    },

    /// Imitates the animation when switching tabs in the Windows 11 Settings interface
    SLIDE_UP_FADE_IN {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            double offset = container.getHeight() > 0 ? container.getHeight() * 0.2 : 50;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.translateYProperty(), 0, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 0, interpolator),
                            new KeyValue(nextNode.translateYProperty(), offset, interpolator)),
                    new KeyFrame(duration.multiply(0.5),
                            new KeyValue(previousNode.opacityProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.translateYProperty(), 0, interpolator))
            );
        }
    },
    ;

    protected static void reset(Node node) {
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setScaleX(1);
        node.setScaleY(1);
        node.setOpacity(1);
    }

    @Override
    public void init(TransitionPane container, Node previousNode, Node nextNode) {
        reset(previousNode);
        reset(nextNode);
    }
}
