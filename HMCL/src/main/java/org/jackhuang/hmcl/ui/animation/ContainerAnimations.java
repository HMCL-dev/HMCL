// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;

/// Supplies reusable page replacement animations.
@org.jetbrains.annotations.NotNullByDefault
public enum ContainerAnimations implements TransitionPane.AnimationProducer {
    NONE {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, false);
            AnimationUtils.reset(nextNode, true);
        }

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
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
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
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
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
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
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

    /// Cross-fades both pages while moving them left by 32 pixels.
    TAB_LEFT {
        /// Creates simultaneous outgoing and incoming page motion.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode,
                                Duration duration, Interpolator interpolator) {
            return animateTab(previousNode, nextNode, duration, interpolator, true, 32);
        }

        /// Returns the reverse tab transition.
        @Override
        public TransitionPane.AnimationProducer opposite() {
            return TAB_RIGHT;
        }
    },
    /// Cross-fades both pages while moving them right by 32 pixels.
    TAB_RIGHT {
        /// Creates simultaneous outgoing and incoming page motion.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode,
                                Duration duration, Interpolator interpolator) {
            return animateTab(previousNode, nextNode, duration, interpolator, true, -32);
        }

        /// Returns the reverse tab transition.
        @Override
        public TransitionPane.AnimationProducer opposite() {
            return TAB_LEFT;
        }
    },
    /// Cross-fades both pages while moving them up by 32 pixels.
    TAB_UP {
        /// Creates simultaneous outgoing and incoming page motion.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode,
                                Duration duration, Interpolator interpolator) {
            return animateTab(previousNode, nextNode, duration, interpolator, false, 32);
        }

        /// Returns the reverse tab transition.
        @Override
        public TransitionPane.AnimationProducer opposite() {
            return TAB_DOWN;
        }
    },
    /// Cross-fades both pages while moving them down by 32 pixels.
    TAB_DOWN {
        /// Creates simultaneous outgoing and incoming page motion.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode,
                                Duration duration, Interpolator interpolator) {
            return animateTab(previousNode, nextNode, duration, interpolator, false, -32);
        }

        /// Returns the reverse tab transition.
        @Override
        public TransitionPane.AnimationProducer opposite() {
            return TAB_UP;
        }
    },
    NAVIGATION {
        /// Also clears the horizontal offsets of the target page's navigation panels.
        @Override
        public void finish(Node nextNode) {
            super.finish(nextNode);
            if (nextNode instanceof DecoratorAnimatedPage nextPage) {
                nextPage.getLeft().setTranslateX(0);
                nextPage.getCenter().setTranslateX(0);
            }
        }

        @Override
        public Animation animate(Pane container, Node previousNode, Node nextNode, Duration duration, Interpolator interpolator) {
            Timeline timeline = new Timeline();
            Duration halfDuration = duration.divide(2);

            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(previousNode.opacityProperty(), 1, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(halfDuration,
                    new KeyValue(previousNode.opacityProperty(), 0, interpolator)));
            if (previousNode instanceof DecoratorAnimatedPage prevPage) {
                Node left = prevPage.getLeft();
                Node center = prevPage.getCenter();

                timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                        new KeyValue(left.translateXProperty(), 0, interpolator),
                        new KeyValue(center.translateXProperty(), 0, interpolator)));
                timeline.getKeyFrames().add(new KeyFrame(halfDuration,
                        new KeyValue(left.translateXProperty(), -30, interpolator),
                        new KeyValue(center.translateXProperty(), 30, interpolator)));
            }

            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.opacityProperty(), 0, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(halfDuration,
                    new KeyValue(nextNode.opacityProperty(), 0, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(duration,
                    new KeyValue(nextNode.opacityProperty(), 1, interpolator)));
            if (nextNode instanceof DecoratorAnimatedPage nextPage) {
                Node left = nextPage.getLeft();
                Node center = nextPage.getCenter();

                timeline.getKeyFrames().add(new KeyFrame(halfDuration,
                        new KeyValue(left.translateXProperty(), -30, interpolator),
                        new KeyValue(center.translateXProperty(), 30, interpolator)));
                timeline.getKeyFrames().add(new KeyFrame(duration,
                        new KeyValue(left.translateXProperty(), 0, interpolator),
                        new KeyValue(center.translateXProperty(), 0, interpolator)));
            }

            return timeline;
        }
    },
    ;

    /// Cross-fades pages along the selected axis; displacement is independent of tab distance.
    private static Timeline animateTab(Node previousNode, Node nextNode, Duration duration,
                                       Interpolator interpolator, boolean horizontal, double offset) {
        var previousTranslation = horizontal ? previousNode.translateXProperty() : previousNode.translateYProperty();
        var nextTranslation = horizontal ? nextNode.translateXProperty() : nextNode.translateYProperty();
        return new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(previousTranslation, 0),
                        new KeyValue(nextTranslation, offset),
                        new KeyValue(previousNode.opacityProperty(), 1),
                        new KeyValue(nextNode.opacityProperty(), 0)),
                new KeyFrame(duration,
                        new KeyValue(previousTranslation, -offset, interpolator),
                        new KeyValue(nextTranslation, 0, interpolator),
                        new KeyValue(previousNode.opacityProperty(), 0, interpolator),
                        new KeyValue(nextNode.opacityProperty(), 1, interpolator)));
    }
}
