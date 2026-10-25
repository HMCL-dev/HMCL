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
import javafx.scene.Parent;
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

    /// Creates an empty container that clips content to its bounds.
    public TransitionPane() {
        FXUtils.setOverflowHidden(this);
    }

    /// Returns the requested page, or `null` if none has been assigned.
    public @Nullable Node getCurrentNode() {
        return currentNode;
    }

    /// Replaces the content using [Motion#SHORT4] and [Motion#EASE].
    public final void setContent(Node newView, AnimationProducer transition) {
        setContent(newView, transition, Motion.SHORT4);
    }

    /// Replaces the content using the given duration and [Motion#EASE].
    public final void setContent(Node newView, AnimationProducer transition, Duration duration) {
        setContent(newView, transition, duration, Motion.EASE);
    }

    /// Replaces the content using the given animation and timing.
    /// The first page, repeated assignments of the current page, and replacements
    /// without animations are displayed immediately, as are replacements outside
    /// a showing window. Otherwise, playback waits for a scene post-layout
    /// notification and for layout requests on the new page root and its
    /// ancestors to be processed. This does not imply that all descendants or
    /// asynchronous content updates have finished requesting layout.
    /// Mouse input is disabled while
    /// the transition is pending or running. A subsequent replacement cancels
    /// the preceding transition. Detaching the container or hiding its window
    /// settles the requested page on the next animation pulse.
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
            getChildren().setAll(newView);
            return;
        }

        getChildren().setAll(previousNode, newView);

        boolean originalCache = newView.isCache();
        CacheHint originalCacheHint = newView.getCacheHint();
        boolean originalMouseTransparent = isMouseTransparent();
        @Nullable CacheHint cacheHint = newView instanceof Cacheable cacheable
                ? cacheable.getCacheHint(transition) : null;

        AnimationTimer timer = new AnimationTimer() {
            /// Whether the scene has reported a CSS and layout pass since registration.
            private boolean prepared;
            /// Whether the one-shot post-layout listener is registered.
            private boolean waitingForLayout;
            /// Records completion without modifying the scene graph during post-layout.
            private final Runnable layoutListener = this::layoutCompleted;
            /// The animation created after preparation, or `null` before playback.
            private @Nullable Animation animation;

            /// Records a completed scene pass and removes the one-shot listener.
            private void layoutCompleted() {
                scene.removePostLayoutPulseListener(layoutListener);
                waitingForLayout = false;
                prepared = true;
            }

            /// Waits for observed layout completion and settles detached or hidden content.
            @Override
            public void handle(long now) {
                if (activeTransition != this) {
                    return;
                }
                if (getScene() != scene || scene.getWindow() != window || !window.isShowing()) {
                    stop();
                    return;
                }
                if (animation == null) {
                    if (!prepared || needsLayout(newView)) {
                        prepared = false;
                        if (!waitingForLayout) {
                            waitingForLayout = true;
                            scene.addPostLayoutPulseListener(layoutListener);
                            Platform.requestNextPulse();
                        }
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
                }
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
                    animation.stop();
                }
                if (cacheHint != null) {
                    newView.setCache(originalCache);
                    newView.setCacheHint(originalCacheHint);
                }
                AnimationUtils.reset(newView, true);
                getChildren().removeIf(node -> node != newView);
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

    /// Tests pending layout on the page root and its ancestors.
    private static boolean needsLayout(Node page) {
        // Virtualized controls may retain dirty internal nodes even after layout.
        // Only the page root and ancestors determine whether to await another pass.
        if (page instanceof Parent parent && parent.isNeedsLayout()) {
            return true;
        }
        for (@Nullable Parent parent = page.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.isNeedsLayout()) {
                return true;
            }
        }
        return false;
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
