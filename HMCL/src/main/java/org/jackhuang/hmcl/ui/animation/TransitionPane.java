/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Displays one page, retaining the preceding page while a transition runs.
/// Content changes must be made on the JavaFX Application Thread.
@NotNullByDefault
public class TransitionPane extends StackPane {

    /// The requested page, or `null` before content is assigned.
    private @Nullable Node currentNode;

    /// The pending or running transition, or `null` when content is settled.
    private @Nullable AnimationTimer activeTransition;

    /// The hidden preparation container, or `null` when no candidate is retained.
    private @Nullable StackPane preloadPane;

    /// Whether the candidate must survive the next content layout pass.
    private boolean preloadLayoutPending;

    /// Creates an empty container that clips content to its bounds.
    public TransitionPane() {
        FXUtils.setOverflowHidden(this);
    }

    /// Returns the requested page, or `null` if none has been assigned.
    public @Nullable Node getCurrentNode() {
        return currentNode;
    }

    /// Attaches unowned content for hidden CSS and layout prewarming during scene pulses.
    /// Replaces any previous candidate without changing the current content. The hidden
    /// container uses a snapshot of this pane's content-area size and does not affect
    /// its size requirements. It is retained through the next content layout pass
    /// and released on a later pass. Any call to [#setContent(Node, AnimationProducer)]
    /// cancels pending prewarming and releases the hidden container.
    /// The caller must retain the content for subsequent use. This method does not wait
    /// for prewarming to finish and must be called on the JavaFX Application Thread.
    ///
    /// @param content the content to prewarm
    /// @return the supplied content
    /// @throws IllegalArgumentException if the content already belongs to a parent or scene
    public final <N extends Node> N prewarm(N content) {
        if (content.getParent() != null || content.getScene() != null) {
            throw new IllegalArgumentException("Content already belongs to a parent or scene");
        }
        if (preloadPane == null) {
            preloadPane = new StackPane();
            preloadPane.setVisible(false);
            preloadPane.setManaged(false);
            preloadPane.setMouseTransparent(true);
            getChildren().add(0, preloadPane);
        }
        preloadPane.getChildren().setAll(content);
        preloadPane.resizeRelocate(snappedLeftInset(), snappedTopInset(),
                Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset()),
                Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset()));
        preloadLayoutPending = true;
        requestLayout();
        return content;
    }

    /// Lays out displayed pages and releases a candidate retained from an earlier pass.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (preloadPane != null && getScene() != null) {
            // Descendant layout follows this method; keep a new candidate attached
            // through that traversal instead of treating this callback as completion.
            if (preloadLayoutPending) {
                preloadLayoutPending = false;
            } else {
                disposePreloadPane();
            }
        }
    }

    /// Releases the preparation container and its candidate without changing displayed pages.
    private void disposePreloadPane() {
        if (preloadPane != null) {
            StackPane candidate = preloadPane;
            preloadPane = null;
            getChildren().remove(candidate);
            candidate.getChildren().clear();
        }
    }

    /// Replaces content nodes and releases any prewarming candidate.
    private void replaceContentNodes(@Nullable Node previousNode, Node newView) {
        disposePreloadPane();
        if (previousNode == null) {
            getChildren().setAll(newView);
        } else {
            getChildren().setAll(previousNode, newView);
        }
    }

    /// Replaces the content using [Motion#SHORT4] and [Motion#EASE].
    public final void setContent(Node newView, AnimationProducer transition) {
        setContent(newView, transition, Motion.SHORT4);
    }

    /// Replaces the content using the given duration and [Motion#EASE].
    public final void setContent(Node newView, AnimationProducer transition, Duration duration) {
        setContent(newView, transition, duration, Motion.EASE);
    }

    /// Replaces the current page, cancelling any preceding transition.
    /// Animated changes wait for a scene CSS and layout pass; mouse input is
    /// suppressed while pending or running. Detaching the container or hiding
    /// its window cancels the transition. Cancellation applies the end state
    /// of a started animation with a finite duration.
    public void setContent(Node newView, AnimationProducer transition,
                           Duration duration, Interpolator interpolator) {
        if (activeTransition != null) {
            activeTransition.stop();
        }
        @Nullable Node previousNode = currentNode != newView && getWidth() > 0 && getHeight() > 0 ? currentNode : null;
        currentNode = newView;

        @Nullable Scene scene = getScene();
        @Nullable Window window = scene == null ? null : scene.getWindow();

        if (!AnimationUtils.isAnimationEnabled() || previousNode == null || transition == ContainerAnimations.NONE
                || window == null || !window.isShowing()) {
            AnimationUtils.reset(newView, true);
            replaceContentNodes(null, newView);
            return;
        }

        replaceContentNodes(previousNode, newView);

        boolean originalCache = newView.isCache();
        CacheHint originalCacheHint = newView.getCacheHint();
        boolean originalMouseTransparent = isMouseTransparent();
        @Nullable CacheHint cacheHint = newView instanceof Cacheable cacheable
                ? cacheable.getCacheHint(transition) : null;

        AnimationTimer timer = new AnimationTimer() {
            /// Queues playback after the scene has completed CSS and layout.
            private final Runnable layoutListener = this::layoutCompleted;
            /// The animation created after preparation, or `null` before playback.
            private @Nullable Animation animation;

            /// Arms a one-shot layout notification after navigation handlers return.
            @Override
            public void start() {
                super.start();
                Platform.runLater(() -> {
                    if (canContinue()) {
                        scene.addPostLayoutPulseListener(layoutListener);
                        Platform.requestNextPulse();
                    }
                });
            }

            /// Leaves the layout callback before creating and playing the animation.
            private void layoutCompleted() {
                scene.removePostLayoutPulseListener(layoutListener);
                Platform.runLater(() -> {
                    if (!canContinue()) {
                        return;
                    }
                    try {
                        animation = transition.animate(TransitionPane.this, previousNode, newView, duration, interpolator);
                        if (activeTransition != this) {
                            return;
                        }
                        animation.setOnFinished(e -> stop());
                        animation.play();
                    } catch (RuntimeException | Error e) {
                        stop();
                        throw e;
                    }
                });
            }

            /// Returns whether this transition is current, settling it if detached or hidden.
            private boolean canContinue() {
                if (activeTransition != this) {
                    return false;
                }
                if (getScene() != scene || scene.getWindow() != window || !window.isShowing()) {
                    stop();
                    return false;
                }
                return true;
            }

            /// Monitors attachment and visibility without scheduling playback by frame count.
            @Override
            public void handle(long now) {
                canContinue();
            }

            /// Releases this transition and restores its page and temporary settings.
            @Override
            public void stop() {
                super.stop();
                if (activeTransition != this) {
                    return;
                }
                activeTransition = null;
                scene.removePostLayoutPulseListener(layoutListener);
                if (animation != null && animation.getStatus() != Animation.Status.STOPPED) {
                    // Apply child-node key values before stop resets the play head.
                    Duration end = animation.getTotalDuration();
                    if (!end.isIndefinite() && !end.isUnknown()) {
                        animation.jumpTo(end);
                    }
                    animation.stop();
                }
                if (cacheHint != null) {
                    newView.setCache(originalCache);
                    newView.setCacheHint(originalCacheHint);
                }
                AnimationUtils.reset(newView, true);
                getChildren().removeIf(node -> node != newView && node != preloadPane);
                setMouseTransparent(originalMouseTransparent);
            }
        };
        activeTransition = timer;
        try {
            setMouseTransparent(true);
            transition.init(this, previousNode, newView);
            if (activeTransition != timer) {
                return;
            }
            if (cacheHint != null) {
                newView.setCache(true);
                newView.setCacheHint(cacheHint);
            }
            timer.start();
        } catch (RuntimeException | Error e) {
            timer.stop();
            throw e;
        }
    }

    /// Prepares page properties and creates animations for content replacements.
    public interface AnimationProducer {
        /// Resets page transforms, making the old page opaque and the new page transparent.
        default void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, false);
        }

        /// Creates the transition between the prepared pages using the given timing.
        Animation animate(Pane container, Node previousNode, Node nextNode,
                          Duration duration, Interpolator interpolator);

        /// Returns the reverse transition, or `null` if none is specified.
        default @Nullable TransitionPane.AnimationProducer opposite() {
            return null;
        }
    }

    /// Marks a node as cacheable as a bitmap during animation.
    public interface Cacheable {
        /// @return the [cache hint][CacheHint] to use when caching this node during the given animation,
        ///         or `null` to not cache it.
        default @Nullable CacheHint getCacheHint(AnimationProducer animationProducer) {
            // https://github.com/HMCL-dev/HMCL/issues/4789
            return animationProducer == ContainerAnimations.SLIDE_UP_FADE_IN ? CacheHint.SPEED : null;
        }
    }
}
