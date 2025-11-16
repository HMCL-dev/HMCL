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
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jetbrains.annotations.Nullable;

public class TransitionPane extends StackPane {

    private Node currentNode;

    public TransitionPane() {
        FXUtils.setOverflowHidden(this);
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public void bindTabHeader(TabHeader tabHeader) {
        this.setContent(tabHeader.getSelectionModel().getSelectedItem().getNode(), ContainerAnimations.NONE);
        FXUtils.onChange(tabHeader.getSelectionModel().selectedItemProperty(), newValue -> {
            this.setContent(newValue.getNode(),
                    ContainerAnimations.SLIDE_UP_FADE_IN,
                    Motion.MEDIUM4,
                    Motion.EASE_IN_OUT_CUBIC_EMPHASIZED
            );
        });
    }

    public final void setContent(Node newView, AnimationProducer transition) {
        setContent(newView, transition, Motion.SHORT4);
    }

    public final void setContent(Node newView, AnimationProducer transition, Duration duration) {
        setContent(newView, transition, duration, Motion.EASE);
    }

    public void setContent(Node newView, AnimationProducer transition,
                           Duration duration, Interpolator interpolator) {
        Node previousNode = currentNode != newView && getWidth() > 0 && getHeight() > 0 ? currentNode : null;
        currentNode = newView;

        if (!AnimationUtils.isAnimationEnabled() || previousNode == null || transition == ContainerAnimations.NONE) {
            getChildren().setAll(newView);
            return;
        }

        getChildren().setAll(previousNode, newView);

        setMouseTransparent(true);
        transition.init(this, previousNode, getCurrentNode());

        // runLater or "init" will not work
        Platform.runLater(() -> {
            Animation newAnimation = transition.animate(
                    this,
                    previousNode,
                    getCurrentNode(),
                    duration, interpolator);
            newAnimation.setOnFinished(e -> {
                setMouseTransparent(false);
                getChildren().remove(previousNode);
            });
            FXUtils.playAnimation(this, "transition_pane", newAnimation);
        });

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
}
