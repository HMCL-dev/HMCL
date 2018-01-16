/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;

public final class TransitionHandler implements AnimationHandler {
    private final StackPane view;
    private Timeline animation;
    private Duration duration;
    private final ImageView snapshot;

    /**
     * @param view A stack pane that contains another control that is [Parent]
     */
    public TransitionHandler(StackPane view) {
        this.view = view;

        snapshot = new ImageView();
        snapshot.setPreserveRatio(true);
        snapshot.setSmooth(true);
    }

    @Override
    public Node getSnapshot() {
        return snapshot;
    }

    @Override
    public StackPane getCurrentRoot() {
        return view;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    public void setContent(Node newView, AnimationProducer transition) {
        setContent(newView, transition, Duration.millis(320));
    }

    public void setContent(Node newView, AnimationProducer transition, Duration duration) {
        this.duration = duration;

        Timeline prev = animation;
        if (prev != null)
            prev.stop();

        updateContent(newView);

        Timeline nowAnimation = new Timeline();
        nowAnimation.getKeyFrames().addAll(transition.animate(this));
        nowAnimation.getKeyFrames().add(new KeyFrame(duration, e -> {
            snapshot.setImage(null);
            snapshot.setX(0);
            snapshot.setY(0);
            snapshot.setVisible(false);
        }));
        nowAnimation.play();
        animation = nowAnimation;
    }

    private void updateContent(Node newView) {
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            Node content = view.getChildren().stream().findFirst().orElse(null);
            WritableImage image;
            if (content != null && content instanceof Parent) {
                view.getChildren().setAll();
                image = FXUtils.takeSnapshot((Parent) content, view.getWidth(), view.getHeight());
                view.getChildren().setAll(content);
            } else
                image = view.snapshot(new SnapshotParameters(), new WritableImage((int) view.getWidth(), (int) view.getHeight()));
            snapshot.setImage(image);
            snapshot.setFitWidth(view.getWidth());
            snapshot.setFitHeight(view.getHeight());
        } else
            snapshot.setImage(null);

        snapshot.setVisible(true);
        snapshot.setOpacity(1.0);
        view.getChildren().setAll(snapshot, newView);
        snapshot.toFront();
    }
}
