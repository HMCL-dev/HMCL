// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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

    /// Marks pages that have completed a rendering preparation or entered their displayed state.
    private static final Object RENDER_INITIALIZED_KEY = new Object();

    /// Keeps the first page render active while contributing negligible visible opacity.
    private static final double PREPARATION_OPACITY = 0.00001;

    /// The requested page, or `null` when content is absent.
    private @Nullable Node currentNode;

    /// The pending or running transition, or `null` when content is settled.
    private @Nullable AnimationTimer activeTransition;

    /// The hidden preparation container, or `null` when no candidate is retained.
    private @Nullable StackPane preloadPane;

    /// Whether the candidate is waiting for its first content layout pass.
    private boolean preloadLayoutPending;

    /// Creates an empty container that clips content to its bounds.
    public TransitionPane() {
        FXUtils.setOverflowHidden(this);
    }

    /// Returns the requested page, or `null` if none has been assigned.
    public @Nullable Node getCurrentNode() {
        return currentNode;
    }

    /// Stops any transition and removes displayed and prewarmed content without animation.
    /// Resets [#getCurrentNode()] to null. Repeated calls on an empty pane have no effect.
    /// Nodes are detached even if settling the transition throws an exception.
    public final void clearContent() {
        try {
            if (activeTransition != null)
                activeTransition.stop();
        } finally {
            disposePreloadPane();
            currentNode = null;
            getChildren().clear();
        }
    }

    /// Attaches unowned content for hidden CSS and layout prewarming.
    /// Replaces any previous candidate without changing the current content. The hidden
    /// container uses a snapshot of this pane's content-area size and does not affect
    /// its size requirements. The content is detached after the layout pass.
    /// Any call to [#setContent(Node, AnimationProducer)] cancels pending prewarming
    /// and releases the hidden container.
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
        disposePreloadPane();
        preloadPane = new StackPane(content);
        preloadPane.setVisible(false);
        preloadPane.setManaged(false);
        preloadPane.setMouseTransparent(true);
        getChildren().add(0, preloadPane);
        preloadPane.resizeRelocate(snappedLeftInset(), snappedTopInset(),
                Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset()),
                Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset()));
        preloadLayoutPending = true;
        requestLayout();
        return content;
    }

    /// Releases the preparation container after its descendants have been laid out.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        @Nullable Scene scene = getScene();
        if (preloadPane != null && scene != null && preloadLayoutPending) {
            preloadLayoutPending = false;
            StackPane candidate = preloadPane;
            Platform.runLater(() -> {
                if (preloadPane == candidate) {
                    disposePreloadPane();
                }
            });
        }
    }

    /// Suppresses future prewarming after a page enters its displayed state in a showing window.
    private static void markDisplayed(Node content) {
        @Nullable Scene scene = content.getScene();
        @Nullable Window window = scene == null ? null : scene.getWindow();
        if (window != null && window.isShowing()
                && content.isVisible() && content.getOpacity() > 0) {
            content.getProperties().put(RENDER_INITIALIZED_KEY, Boolean.TRUE);
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
    /// Animated changes wait for CSS, layout, and first-display rendering preparation; mouse input is
    /// suppressed while pending or running. Detaching the container or hiding
    /// its window cancels the transition. Cancellation applies the end state
    /// of a started animation with a finite duration and restores the target page
    /// through [AnimationProducer#finish(Node)], even if playback has not started.
    /// Previously undisplayed pages initialized at zero opacity render almost transparently
    /// before playback, provided they are visible and their opacity is not bound. Preparation
    /// preserves initialized transforms, so offscreen content may still be culled.
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
            transition.finish(newView);
            replaceContentNodes(null, newView);
            markDisplayed(newView);
            return;
        }

        replaceContentNodes(previousNode, newView);

        boolean originalCache = newView.isCache();
        CacheHint originalCacheHint = newView.getCacheHint();
        boolean originalMouseTransparent = isMouseTransparent();
        @Nullable CacheHint cacheHint = transition.getCacheHint(newView);

        AnimationTimer timer = new AnimationTimer() {
            /// Queues playback after the scene has completed CSS and layout.
            private final Runnable layoutListener = this::layoutCompleted;
            /// The animation created after preparation, or `null` before playback.
            private @Nullable Animation animation;
            /// Whether the near-transparent render still needs a subsequent scene synchronization.
            private boolean renderingPending;

            /// Arms a one-shot layout notification after navigation handlers return.
            @Override
            public void start() {
                renderingPending = !newView.getProperties().containsKey(RENDER_INITIALIZED_KEY)
                        && newView.isVisible() && !newView.opacityProperty().isBound()
                        && newView.getOpacity() == 0;
                if (renderingPending) {
                    newView.setOpacity(PREPARATION_OPACITY);
                }
                if (!canContinue()) {
                    return;
                }
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
                        if (renderingPending) {
                            renderingPending = false;
                            // This dirty state forces the next scene synchronization to wait for
                            // the preceding render. Only its following runLater starts the clock.
                            newView.setOpacity(0);
                            if (canContinue()) {
                                scene.addPostLayoutPulseListener(layoutListener);
                                Platform.requestNextPulse();
                            }
                            return;
                        }
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
                transition.finish(newView);
                if (animation != null) {
                    markDisplayed(newView);
                }
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
        /// Returns the bitmap cache hint for the incoming page during preparation and playback,
        /// or `null` to leave its cache settings unchanged. Called before [#init].
        /// The original cache settings are restored on completion or cancellation when a hint is returned.
        default @Nullable CacheHint getCacheHint(Node nextNode) {
            return null;
        }

        /// Resets page transforms, making the old page opaque and the new page transparent.
        default void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, false);
        }

        /// Creates the transition between the prepared pages using the given timing.
        Animation animate(Pane container, Node previousNode, Node nextNode,
                          Duration duration, Interpolator interpolator);

        /// Restores the target page to its settled state after completion or cancellation,
        /// or when animation is skipped. May be called without [#init] or [#animate]
        /// having been called, and before CSS or layout has completed.
        ///
        /// @implSpec The default implementation sets the target page's X/Y translations
        /// to zero, X/Y scales to one, and opacity to one. Implementations that animate child nodes
        /// must also restore those nodes to their target state, including state retained
        /// from an earlier transition. Repeated calls must leave the page settled.
        /// @param nextNode the page being shown
        default void finish(Node nextNode) {
            AnimationUtils.reset(nextNode, true);
        }

        /// Returns the reverse transition, or `null` if none is specified.
        default @Nullable TransitionPane.AnimationProducer opposite() {
            return null;
        }
    }
}
