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
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

/// # Material Design ProgressBar Skin
///
/// @author Shadi Shaheen
/// @version 2.0
/// @since 2017-10-06
public class JFXProgressBarSkin extends SkinBase<JFXProgressBar> {

    private static final double HEIGHT = 4;

    private final StackPane track;
    private final Rectangle bar;
    private Animation transition;
    private final TreeShowingProperty treeShowingProperty;
    private double fullWidth;

    public JFXProgressBarSkin(JFXProgressBar control) {
        super(control);

        this.treeShowingProperty = new TreeShowingProperty(control);

        registerChangeListener(treeShowingProperty, obs -> updateProgress(false));
        registerChangeListener(control.progressProperty(), obs -> updateProgress(true));

        track = new StackPane();
        track.getStyleClass().setAll("track");

        bar = new Rectangle();
        bar.setManaged(false);
        bar.getStyleClass().setAll("bar");
        bar.setHeight(HEIGHT);
        bar.setWidth(0);
        bar.setArcWidth(HEIGHT);
        bar.setArcHeight(HEIGHT);

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
        return topInset + HEIGHT + bottomInset;
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
        track.resizeRelocate(x, y, w, h);
        bar.relocate(x, y);
        bar.setTranslateX(0);
        bar.setHeight(h);
        bar.setWidth(0);

        fullWidth = w;

        clearAnimation();
        updateProgress(false);
    }

    private boolean wasIndeterminate = false;

    private void updateProgress(boolean playProgressAnimation) {
        double progress = Math.min(getSkinnable().getProgress(), 1.0);
        boolean isIndeterminate = progress < 0.0;
        boolean isTreeShowing = treeShowingProperty.get();

        if (isIndeterminate != wasIndeterminate) {
            wasIndeterminate = isIndeterminate;
            clearAnimation();
            bar.setTranslateX(0);
        }

        if (isIndeterminate) { // indeterminate
            if (isTreeShowing) {
                if (transition == null) {
                    transition = createIndeterminateTransition();
                    transition.playFromStart();
                } else {
                    transition.play();
                }
            } else if (transition != null) {
                transition.pause();
            }
        } else { // determinate
            clearAnimation();
            if (isTreeShowing && playProgressAnimation
                    && AnimationUtils.isAnimationEnabled()
                    && getSkinnable().isSmoothProgress()) {
                transition = createDeterminateTransition(progress);
                transition.playFromStart();
            } else {
                bar.setWidth(computeBarWidth(progress));
            }
        }
    }

    private static final Duration INDETERMINATE_DURATION = Duration.seconds(1);

    private Timeline createIndeterminateTransition() {
        Timeline indeterminateTransition = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(bar.widthProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(bar.translateXProperty(), 0, Interpolator.LINEAR)
                ),
                new KeyFrame(
                        INDETERMINATE_DURATION.multiply(0.5),
                        new KeyValue(bar.widthProperty(), fullWidth * 0.4, Interpolator.LINEAR)
                ),
                new KeyFrame(
                        INDETERMINATE_DURATION.multiply(0.9),
                        new KeyValue(bar.translateXProperty(), fullWidth, Interpolator.LINEAR)
                ),
                new KeyFrame(
                        INDETERMINATE_DURATION,
                        new KeyValue(bar.widthProperty(), 0.0, Interpolator.EASE_OUT)
                ));
        indeterminateTransition.setCycleCount(Timeline.INDEFINITE);
        return indeterminateTransition;
    }

    private static final Duration DETERMINATE_DURATION = Duration.seconds(0.2);

    private Timeline createDeterminateTransition(double targetProgress) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(bar.widthProperty(), bar.getWidth())),
                new KeyFrame(DETERMINATE_DURATION,
                        new KeyValue(bar.widthProperty(), computeBarWidth(targetProgress)))
        );
        timeline.setOnFinished(e -> {
            if (transition == timeline) {
                transition = null;
            }
        });
        return timeline;
    }

    private void clearAnimation() {
        if (transition != null) {
            transition.stop();
            transition = null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        treeShowingProperty.dispose();
        clearAnimation();
    }

    private double computeBarWidth(double progress) {
        assert progress >= 0 && progress <= 1;
        double barWidth = ((int) fullWidth * 2 * progress) / 2.0;
        return progress > 0 ? Math.max(barWidth, HEIGHT) : barWidth;
    }
}
