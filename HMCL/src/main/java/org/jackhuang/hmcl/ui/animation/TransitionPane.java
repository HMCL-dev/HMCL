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
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TransitionPane extends StackPane {

    private Node currentNode;

    /// Nodes whose bitmap cache is currently enabled by an active transition.
    private final List<Node> cachedNodes = new ArrayList<>(2);

    public TransitionPane() {
        FXUtils.setOverflowHidden(this);
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public final void setContent(Node newView, AnimationProducer transition) {
        setContent(newView, transition, Motion.SHORT4);
    }

    public final void setContent(Node newView, AnimationProducer transition, Duration duration) {
        setContent(newView, transition, duration, Motion.EASE);
    }

    public void setContent(Node newView, AnimationProducer transition,
                           Duration duration, Interpolator interpolator) {
        // Stop any running transition first, so its onFinished (which shares
        // cachedNodes) can no longer run after we replace it below.
        if (getProperties().get("hmcl.animations.transition_pane") instanceof Animation oldAnimation) {
            oldAnimation.stop();
        }

        // Drop the bitmap caches of any previous transition. Interrupted
        // transitions never reach onFinished, so without this a page could be
        // left cached and later re-shown as a frozen bitmap.
        for (Node node : cachedNodes) {
            node.setCache(false);
        }
        cachedNodes.clear();

        Node previousNode = currentNode != newView && getWidth() > 0 && getHeight() > 0 ? currentNode : null;
        currentNode = newView;

        if (!AnimationUtils.isAnimationEnabled() || previousNode == null || transition == ContainerAnimations.NONE) {
            AnimationUtils.reset(newView, true);
            getChildren().setAll(newView);
            return;
        }

        getChildren().setAll(previousNode, newView);

        setMouseTransparent(true);
        transition.init(this, previousNode, newView);

        // Cache both pages as bitmaps while the transition runs, so that the
        // animation composites pre-rendered images instead of re-rasterizing
        // the two page subtrees on every frame.
        cacheDuringTransition(previousNode, transition);
        cacheDuringTransition(newView, transition);

        // runLater or "init" will not work
        Platform.runLater(() -> {
            Animation newAnimation = transition.animate(
                    this,
                    previousNode,
                    newView,
                    duration, interpolator);
            newAnimation.setOnFinished(e -> {
                setMouseTransparent(false);
                if (previousNode != currentNode) {
                    getChildren().remove(previousNode);
                }

                for (Node node : cachedNodes) {
                    node.setCache(false);
                }
                cachedNodes.clear();
            });
            FXUtils.playAnimation(this, "transition_pane", newAnimation);
        });

    }

    /// Enables bitmap caching on `node` (or on the parts the transition actually
    /// animates) while the transition plays, recording every cached node.
    ///
    /// @param node        the page being transitioned
    /// @param transition  the active transition
    private void cacheDuringTransition(Node node, AnimationProducer transition) {
        if (transition == ContainerAnimations.NAVIGATION && node instanceof DecoratorAnimatedPage page) {
            // NAVIGATION animates the page's left and center panes themselves,
            // so caching the whole page would freeze that motion. Cache the
            // panes instead.
            cacheNode(page.getLeft());
            cacheNode(page.getCenter());
            return;
        }

        @Nullable CacheHint cacheHint = node instanceof Cacheable cacheable
                ? cacheable.getCacheHint(transition)
                : CacheHint.SPEED;
        if (cacheHint != null) {
            node.setCache(true);
            node.setCacheHint(cacheHint);
            cachedNodes.add(node);
        }
    }

    /// Enables bitmap caching on `node`.
    ///
    /// @param node the node to cache
    private void cacheNode(Node node) {
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);
        cachedNodes.add(node);
    }

    public interface AnimationProducer {
        default void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, false);
        }

        Animation animate(Pane container, Node previousNode, Node nextNode,
                          Duration duration, Interpolator interpolator);

        default @Nullable TransitionPane.AnimationProducer opposite() {
            return null;
        }
    }

    /// Marks a node as cacheable as a bitmap during animation.
    ///
    /// All pages are cached during transitions by default; this interface only
    /// exists so a page can opt out or pick a different [CacheHint].
    public interface Cacheable {
        /// @return the [cache hint][CacheHint] to use when caching this node during the given animation,
        ///         or `null` to not cache it.
        default @Nullable CacheHint getCacheHint(AnimationProducer animationProducer) {
            return CacheHint.SPEED;
        }
    }
}
