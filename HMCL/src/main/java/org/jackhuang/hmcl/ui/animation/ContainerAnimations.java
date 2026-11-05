// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.animation.TransitionPane.DEFAULT_DURATION;
import static org.jackhuang.hmcl.ui.animation.TransitionPane.DEFAULT_DURATION_HALF;
import static org.jackhuang.hmcl.ui.animation.TransitionPane.DEFAULT_INTERPOLATOR;
import static org.jackhuang.hmcl.ui.animation.TransitionPane.isCacheAllowed;

/// Supplies reusable page replacement animations that honor each cache target's transition preference.
@NotNullByDefault
public enum ContainerAnimations implements TransitionPane.AnimationProducer {
    NONE {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, false);
            AnimationUtils.reset(nextNode, true);
        }

        /// Creates an empty transition.
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode) {
            return new Timeline();
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return this;
        }
    },

    /// A fade between the old and new view
    FADE {
        /// Fades between pages while temporarily caching the incoming page.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE),
                            new KeyValue(previousNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION,
                            new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE),
                            new KeyValue(previousNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return this;
        }
    },

    /// A swipe effect
    SWIPE_LEFT {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setTranslateX(container.getWidth());
        }

        /// Slides pages left while temporarily caching the incoming page.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE),
                    new KeyValue(nextNode.translateXProperty(), container.getWidth(), DEFAULT_INTERPOLATOR),
                    new KeyValue(previousNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION,
                            new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE),
                            new KeyValue(nextNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(previousNode.translateXProperty(), -container.getWidth(), DEFAULT_INTERPOLATOR)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return SWIPE_RIGHT;
        }
    },

    /// A swipe effect
    SWIPE_RIGHT {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setTranslateX(-container.getWidth());
        }

        /// Slides pages right while temporarily caching the incoming page.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE),
                    new KeyValue(nextNode.translateXProperty(), -container.getWidth(), DEFAULT_INTERPOLATOR),
                    new KeyValue(previousNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION,
                            new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE),
                            new KeyValue(nextNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(previousNode.translateXProperty(), container.getWidth(), DEFAULT_INTERPOLATOR)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return SWIPE_LEFT;
        }
    },

    /// @see <a href="https://m3.material.io/styles/motion/transitions/transition-patterns">Transitions - Material Design 3</a>
    FORWARD {
        /// Animates forward navigation with temporary incoming-page caching.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            double offset = container.getWidth() > 0 ? container.getWidth() * 0.2 : 50;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE),
                            new KeyValue(previousNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(previousNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION_HALF,
                            new KeyValue(previousNode.translateXProperty(), -offset, DEFAULT_INTERPOLATOR),
                            new KeyValue(previousNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR),

                            new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.translateXProperty(), offset, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION,
                            new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE),
                            new KeyValue(nextNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR))
            );
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return BACKWARD;
        }
    },

    /// @see <a href="https://m3.material.io/styles/motion/transitions/transition-patterns">Transitions - Material Design 3</a>
    BACKWARD {
        /// Animates backward navigation with temporary incoming-page caching.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            double offset = container.getWidth() > 0 ? container.getWidth() * 0.2 : 50;
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE),
                            new KeyValue(previousNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(previousNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION_HALF,
                            new KeyValue(previousNode.translateXProperty(), offset, DEFAULT_INTERPOLATOR),
                            new KeyValue(previousNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR),

                            new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.translateXProperty(), -offset, DEFAULT_INTERPOLATOR)),
                    new KeyFrame(DEFAULT_DURATION,
                            new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE),
                            new KeyValue(nextNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR),
                            new KeyValue(nextNode.translateXProperty(), 0, DEFAULT_INTERPOLATOR))
            );
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return FORWARD;
        }
    },

    /// Preserves the original slide-up and fade-in transition.
    TAB_UP {
        /// Creates a 400 ms slide-and-fade transition along the selected vertical direction.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            return animateTab(container, previousNode, nextNode, 1);
        }

        /// Returns the reverse tab transition.
        @Override
        public TransitionPane.AnimationProducer opposite() {
            return TAB_DOWN;
        }
    },
    /// Mirrors the original slide-up and fade-in transition vertically.
    TAB_DOWN {
        /// Creates a 400 ms slide-and-fade transition along the selected vertical direction.
        @Override
        public Timeline animate(Pane container, Node previousNode, Node nextNode) {
            return animateTab(container, previousNode, nextNode, -1);
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

        /// Animates navigation panels, respecting the content's cache preference for each center panel.
        /// Restores the panels' original cache states at the end.
        @Override
        public Animation animate(Pane container, Node previousNode, Node nextNode) {
            Timeline timeline = new Timeline();
            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(previousNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR)));
            timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION_HALF,
                    new KeyValue(previousNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR)));
            if (previousNode instanceof DecoratorAnimatedPage prevPage) {
                Node left = prevPage.getLeft();
                Node center = prevPage.getCenter();
                @Nullable Node content = prevPage.getContent();
                boolean cacheCenter = isCacheAllowed(center) && (content == null || isCacheAllowed(content));

                timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                        new KeyValue(left.cacheProperty(), isCacheAllowed(left), Interpolator.DISCRETE),
                        new KeyValue(center.cacheProperty(), cacheCenter, Interpolator.DISCRETE),
                        new KeyValue(left.translateXProperty(), 0, DEFAULT_INTERPOLATOR),
                        new KeyValue(center.translateXProperty(), 0, DEFAULT_INTERPOLATOR)));
                timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION_HALF,
                        new KeyValue(left.translateXProperty(), -30, DEFAULT_INTERPOLATOR),
                        new KeyValue(center.translateXProperty(), 30, DEFAULT_INTERPOLATOR)));
                timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION,
                        new KeyValue(left.cacheProperty(), left.isCache(), Interpolator.DISCRETE),
                        new KeyValue(center.cacheProperty(), center.isCache(), Interpolator.DISCRETE)));
            }

            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR)));
            timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION_HALF,
                    new KeyValue(nextNode.opacityProperty(), 0, DEFAULT_INTERPOLATOR)));
            timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION,
                    new KeyValue(nextNode.opacityProperty(), 1, DEFAULT_INTERPOLATOR)));
            if (nextNode instanceof DecoratorAnimatedPage nextPage) {
                Node left = nextPage.getLeft();
                Node center = nextPage.getCenter();
                @Nullable Node content = nextPage.getContent();
                boolean cacheCenter = isCacheAllowed(center) && (content == null || isCacheAllowed(content));

                timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                        new KeyValue(left.cacheProperty(), isCacheAllowed(left), Interpolator.DISCRETE),
                        new KeyValue(center.cacheProperty(), cacheCenter, Interpolator.DISCRETE)));
                timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION_HALF,
                        new KeyValue(left.translateXProperty(), -30, DEFAULT_INTERPOLATOR),
                        new KeyValue(center.translateXProperty(), 30, DEFAULT_INTERPOLATOR)));
                timeline.getKeyFrames().add(new KeyFrame(DEFAULT_DURATION,
                        new KeyValue(left.cacheProperty(), left.isCache(), Interpolator.DISCRETE),
                        new KeyValue(center.cacheProperty(), center.isCache(), Interpolator.DISCRETE),
                        new KeyValue(left.translateXProperty(), 0, DEFAULT_INTERPOLATOR),
                        new KeyValue(center.translateXProperty(), 0, DEFAULT_INTERPOLATOR)));
            } else {
                timeline.getKeyFrames().addAll(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE)),
                        new KeyFrame(DEFAULT_DURATION,
                                new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE)));
            }

            return timeline;
        }
    },
    ;

    /// Creates the slide-and-fade transition with emphasized easing in the selected direction.
    /// The previous page stays still and fades out halfway through the incoming page's motion.
    private static Timeline animateTab(Pane container, Node previousNode, Node nextNode, double direction) {
        Duration duration = Motion.MEDIUM4;
        double extent = container.getHeight();
        double offset = (extent > 0 ? extent * 0.2 : 50) * direction;
        var previousTranslation = previousNode.translateYProperty();
        var nextTranslation = nextNode.translateYProperty();
        return new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(nextNode.cacheProperty(), isCacheAllowed(nextNode), Interpolator.DISCRETE),
                        new KeyValue(previousTranslation, 0, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED),
                        new KeyValue(previousNode.opacityProperty(), 1, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED),
                        new KeyValue(nextNode.opacityProperty(), 0, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED),
                        new KeyValue(nextTranslation, offset, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED)),
                new KeyFrame(duration.multiply(0.5),
                        new KeyValue(previousNode.opacityProperty(), 0, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED)),
                new KeyFrame(duration,
                        new KeyValue(nextNode.cacheProperty(), nextNode.isCache(), Interpolator.DISCRETE),
                        new KeyValue(nextNode.opacityProperty(), 1, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED),
                        new KeyValue(nextTranslation, 0, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED)));
    }
}
