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

/// # Material Design ProgressBar Skin
///
/// @author Shadi Shaheen
/// @version 2.0
/// @since 2017-10-06
public class JFXProgressBarSkin extends SkinBase<JFXProgressBar> {

    private static final double DEFAULT_HEIGHT = 4;

    private final StackPane track;
    private final StackPane bar;
    private final Rectangle clip;
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

        bar = new StackPane();
        bar.getStyleClass().setAll("bar");

        clip = new Rectangle();
        clip.setArcWidth(DEFAULT_HEIGHT);
        clip.setArcHeight(DEFAULT_HEIGHT);
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
        return topInset + DEFAULT_HEIGHT + bottomInset;
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
        bar.resizeRelocate(x, y, w, h);

        clip.relocate(0, 0);
        clip.setWidth(0);
        clip.setHeight(h);
        clip.setTranslateX(0);

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
            clip.setTranslateX(0);
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
                clip.setWidth(computeBarWidth(progress));
            }
        }
    }

    private static final Duration INDETERMINATE_DURATION = Duration.seconds(1);

    private Transition createIndeterminateTransition() {
        double minWidth = 0;
        double maxWidth = fullWidth * 0.4;
        Transition transition = new Transition() {
            {
                setInterpolator(Interpolator.LINEAR);
                setCycleDuration(INDETERMINATE_DURATION);
            }

            @Override
            protected void interpolate(double frac) {
                double currentWidth;

                if (frac <= 0.5) {
                    currentWidth = Interpolator.EASE_IN.interpolate(minWidth, maxWidth, frac / 0.5);
                } else {
                    currentWidth = Interpolator.EASE_OUT.interpolate(maxWidth, minWidth, (frac - 0.5) / 0.5);
                }

                double targetCenter;
                if (frac <= 0.1) {
                    targetCenter = 0.0;
                } else if (frac >= 0.9) {
                    targetCenter = fullWidth;
                } else {
                    targetCenter = ((frac - 0.1) / 0.8) * fullWidth;
                }

                clip.setWidth(currentWidth);
                clip.setTranslateX(targetCenter - currentWidth / 2.0);
            }
        };

        transition.setCycleCount(Timeline.INDEFINITE);
        return transition;
    }

    private static final Duration DETERMINATE_DURATION = Duration.seconds(0.2);

    private Timeline createDeterminateTransition(double targetProgress) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(clip.widthProperty(), clip.getWidth())),
                new KeyFrame(DETERMINATE_DURATION,
                        new KeyValue(clip.widthProperty(), computeBarWidth(targetProgress)))
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
        return progress > 0 ? Math.max(barWidth, DEFAULT_HEIGHT) : barWidth;
    }
}
