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
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
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

    /// The running transition, or `null` while idle or preparing content.
    private @Nullable Animation animation;

    /// Restores temporary cache settings when a transition ends or is replaced.
    private @Nullable Runnable restoreCache;

    /// Identifies the latest request, including requests that do not animate.
    private long transitionId;

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

    /// Replaces the content, cancelling any pending or running transition.
    /// The first page, repeated assignments of the current page, and replacements
    /// without animations are displayed immediately. Otherwise, CSS and layout
    /// are processed in this container before the animation starts in a later
    /// event-loop task. Mouse input is disabled while the transition is pending
    /// or running.
    public void setContent(Node newView, AnimationProducer transition,
                           Duration duration, Interpolator interpolator) {
        long requestId = ++transitionId;
        finishTransition();
        @Nullable Node previousNode = currentNode != newView && getWidth() > 0 && getHeight() > 0 ? currentNode : null;
        currentNode = newView;

        if (!AnimationUtils.isAnimationEnabled() || previousNode == null || transition == ContainerAnimations.NONE) {
            AnimationUtils.reset(newView, true);
            getChildren().setAll(newView);
            return;
        }

        getChildren().setAll(previousNode, newView);

        setMouseTransparent(true);
        transition.init(this, previousNode, newView);

        // Let navigation handlers finish updating the page before preparing it.
        Platform.runLater(() -> {
            if (requestId != transitionId) {
                return;
            }

            // runLater alone does not guarantee a CSS or layout pass. Keep that
            // first-use work outside the animation's elapsed time.
            applyCss();
            layout();
            if (requestId != transitionId) {
                return;
            }

            @Nullable CacheHint cacheHint = newView instanceof Cacheable cacheable
                    ? cacheable.getCacheHint(transition)
                    : null;
            if (cacheHint != null) {
                boolean wasCached = newView.isCache();
                CacheHint previousHint = newView.getCacheHint();
                restoreCache = () -> {
                    newView.setCache(wasCached);
                    newView.setCacheHint(previousHint);
                };
                newView.setCache(true);
                newView.setCacheHint(cacheHint);
            }

            Animation newAnimation = transition.animate(
                    this,
                    previousNode,
                    newView,
                    duration, interpolator);
            newAnimation.setOnFinished(e -> {
                if (requestId == transitionId) {
                    finishTransition();
                    getChildren().remove(previousNode);
                }
            });
            animation = newAnimation;
            newAnimation.play();
        });

    }

    /// Stops the active animation and restores input and temporary cache state.
    private void finishTransition() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
        if (restoreCache != null) {
            restoreCache.run();
            restoreCache = null;
        }
        setMouseTransparent(false);
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
