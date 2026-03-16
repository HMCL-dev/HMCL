/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.skins;

import com.jfoenix.controls.JFXProgressBar;
import com.jfoenix.utils.TreeShowingProperty;
import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.util.Lang;

/// # Material Design ProgressBar Skin
///
/// @author Shadi Shaheen
/// @version 2.0
/// @since 2017-10-06
public class JFXProgressBarSkin extends SkinBase<JFXProgressBar> {

    private final StackPane track;
    private final StackPane bar;
    private Timeline indeterminateTransition;
    private final Rectangle clip;
    private final TreeShowingProperty treeShowingProperty;

    public JFXProgressBarSkin(JFXProgressBar control) {
        super(control);

        this.treeShowingProperty = new TreeShowingProperty(control);

        control.widthProperty().addListener(observable -> updateProgress());
        registerChangeListener(control.progressProperty(), (obs) -> updateProgress());
        registerChangeListener(treeShowingProperty, obs -> this.updateAnimation());

        track = new StackPane();
        track.getStyleClass().setAll("track");

        bar = new StackPane();
        bar.getStyleClass().setAll("bar");

        clip = new Rectangle();
        clip.setManaged(false);
        clip.setArcWidth(4);
        clip.setArcHeight(4);
        bar.setClip(clip);

        getChildren().setAll(track, bar);

        getSkinnable().requestLayout();
    }

    @Override
    public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return Math.max(100, leftInset + bar.prefWidth(getSkinnable().getWidth()) + rightInset);
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + bar.prefHeight(width) + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        boolean indeterminate = getSkinnable().isIndeterminate();

        track.resizeRelocate(x, y, w, h);
        bar.resizeRelocate(x, y, w, h);
        clip.relocate(0, 0);

        if (indeterminate) {
            if (treeShowingProperty.get()) {
                createIndeterminateTimeline();
                indeterminateTransition.play();
            } else {
                clearAnimation();
            }
        } else {
            clearAnimation();

            double progress = Lang.clamp(0.0, getSkinnable().getProgress(), 1.0);
            double barWidth = ((int) w * 2 * progress) / 2.0;
            if (progress > 0) {
                barWidth = Math.max(barWidth, 4);
            }

            clip.setTranslateX(0);
            clip.setWidth(barWidth);
            clip.setHeight(h);
        }
    }

    boolean wasIndeterminate = false;

    protected void pauseTimeline(boolean pause) {
        if (getSkinnable().isIndeterminate()) {
            if (indeterminateTransition == null) {
                createIndeterminateTimeline();
            }
            if (pause) {
                indeterminateTransition.pause();
            } else {
                indeterminateTransition.play();
            }
        }
    }

    private void updateAnimation() {
        final boolean isTreeShowing = treeShowingProperty.get();
        if (indeterminateTransition != null) {
            pauseTimeline(!isTreeShowing);
        } else if (isTreeShowing) {
            createIndeterminateTimeline();
        }
    }

    private void updateProgress() {
        final ProgressIndicator control = getSkinnable();
        final boolean isIndeterminate = control.isIndeterminate();
        if (!(isIndeterminate && wasIndeterminate)) {
            control.requestLayout();
        }
        wasIndeterminate = isIndeterminate;
    }

    private static final Duration DURATION = Duration.seconds(1);

    private void createIndeterminateTimeline() {
        clearAnimation();
        ProgressIndicator control = getSkinnable();
        final double w = control.getWidth() - snappedLeftInset() - snappedRightInset();
        indeterminateTransition = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(clip.widthProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(clip.translateXProperty(), 0, Interpolator.LINEAR)
                ),
                new KeyFrame(
                        DURATION.multiply(0.5),
                        new KeyValue(clip.widthProperty(), w * 0.4, Interpolator.LINEAR)
                ),
                new KeyFrame(
                        DURATION.multiply(0.9),
                        new KeyValue(clip.translateXProperty(), w, Interpolator.LINEAR)
                ),
                new KeyFrame(
                        DURATION,
                        new KeyValue(clip.widthProperty(), 0.0, Interpolator.EASE_OUT)
                ));
        indeterminateTransition.setCycleCount(Timeline.INDEFINITE);
    }

    private void clearAnimation() {
        if (indeterminateTransition != null) {
            indeterminateTransition.stop();
            indeterminateTransition = null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        treeShowingProperty.dispose();
        clearAnimation();
    }
}
