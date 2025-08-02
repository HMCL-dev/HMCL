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

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;

public class TransitionPane extends StackPane implements AnimationHandler {
    private static final Duration DEFAULT_DURATION = Duration.millis(200);

    private Duration duration;
    private Node previousNode, currentNode;

    {
        currentNode = getChildren().stream().findFirst().orElse(null);
        FXUtils.setOverflowHidden(this);
    }

    @Override
    public Node getPreviousNode() {
        return previousNode;
    }

    @Override
    public Node getCurrentNode() {
        return currentNode;
    }

    @Override
    public StackPane getCurrentRoot() {
        return this;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    public void setContent(Node newView, AnimationProducer transition) {
        setContent(newView, transition, DEFAULT_DURATION);
    }

    public void setContent(Node newView, AnimationProducer transition, Duration duration) {
        this.duration = duration;

        updateContent(newView);

        if (previousNode == EMPTY_PANE) {
            getChildren().setAll(newView);
            return;
        }

        if (AnimationUtils.isAnimationEnabled() && transition != ContainerAnimations.NONE) {
            setMouseTransparent(true);
            transition.init(this);

            // runLater or "init" will not work
            Platform.runLater(() -> {
                Timeline newAnimation = new Timeline();
                newAnimation.getKeyFrames().setAll(transition.animate(this));
                newAnimation.setOnFinished(e -> {
                    setMouseTransparent(false);
                    getChildren().remove(previousNode);
                });
                FXUtils.playAnimation(this, "transition_pane", newAnimation);
            });
        } else {
            getChildren().remove(previousNode);
        }
    }

    private void updateContent(Node newView) {
        if (getWidth() > 0 && getHeight() > 0) {
            previousNode = currentNode;
            if (previousNode == null)
                previousNode = EMPTY_PANE;
        } else
            previousNode = EMPTY_PANE;

        if (previousNode == newView)
            previousNode = EMPTY_PANE;

        currentNode = newView;

        getChildren().setAll(previousNode, currentNode);
    }

    private final EmptyPane EMPTY_PANE = new EmptyPane();

    public static class EmptyPane extends StackPane {
    }
}
