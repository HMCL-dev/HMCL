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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.skin.ProgressIndicatorSkin;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/// # Material Design 3 ProgressBar Skin
///
/// @author Shadi Shaheen
/// @version 2.0
/// @since 2017-10-06
public class JFXProgressBarSkin extends ProgressIndicatorSkin {

    private static final double INDICATOR_HEIGHT = 4;
    private static final double DETERMINATE_MIN_ACTIVE_WIDTH = 4;
    private static final double TRACK_GAP = 4;
    private static final double STOP_INDICATOR_SIZE = 4;

    private static final double INDETERMINATE_INITIAL_START_FACTOR = 0.0;
    private static final double INDETERMINATE_INITIAL_END_FACTOR = 0.18;

    private final Region leadingTrack = new Region();
    private final Region trailingTrack = new Region();
    private final Region activeIndicator = new Region();
    private final Region stopIndicator = new Region();
    private final DoubleProperty indeterminateSegmentStartFactor = new SimpleDoubleProperty(INDETERMINATE_INITIAL_START_FACTOR);
    private final DoubleProperty indeterminateSegmentEndFactor = new SimpleDoubleProperty(INDETERMINATE_INITIAL_END_FACTOR);
    private final TreeShowingProperty treeShowingProperty;

    private Timeline indeterminateTransition;

    public JFXProgressBarSkin(JFXProgressBar bar) {
        super(bar);

        this.treeShowingProperty = new TreeShowingProperty(bar);

        initializeNodes();

        indeterminateSegmentStartFactor.addListener(observable -> getSkinnable().requestLayout());
        indeterminateSegmentEndFactor.addListener(observable -> getSkinnable().requestLayout());
        bar.widthProperty().addListener(observable -> updateProgress());

        registerChangeListener(bar.progressProperty(), obs -> updateProgress());
        registerChangeListener(bar.visibleProperty(), obs -> updateAnimation());
        registerChangeListener(bar.parentProperty(), obs -> updateAnimation());
        registerChangeListener(bar.sceneProperty(), obs -> updateAnimation());

        unregisterChangeListeners(treeShowingProperty);
        unregisterChangeListeners(bar.indeterminateProperty());

        registerChangeListener(treeShowingProperty, obs -> updateAnimation());
        registerChangeListener(bar.indeterminateProperty(), obs -> initialize());

        initialize();
    }

    private void initializeNodes() {
        configureRegion(leadingTrack, "track");
        configureRegion(trailingTrack, "track");
        configureRegion(activeIndicator, "active-indicator");
        configureRegion(stopIndicator, "stop-indicator");
        getChildren().setAll(leadingTrack, trailingTrack, activeIndicator, stopIndicator);
    }

    private void configureRegion(Region region, String styleClass) {
        region.getStyleClass().setAll(styleClass);
        region.setManaged(false);
    }

    protected void initialize() {
        resetIndeterminateGeometry();
        updateAnimation();
        updateProgress();
    }

    @Override
    public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        double prefWidth = getSkinnable().getPrefWidth();
        return leftInset + (prefWidth > 0 ? prefWidth : 100) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + INDICATOR_HEIGHT + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset + INDICATOR_HEIGHT + bottomInset;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double width = Math.max(0, w);
        double height = Math.min(INDICATOR_HEIGHT, Math.max(0, h));
        double barY = y + Math.max(0, (h - height) / 2);

        if (getSkinnable().isIndeterminate()) {
            layoutIndeterminate(x, barY, width, height);
        } else {
            layoutDeterminate(x, barY, width, height);
        }
    }

    private void layoutDeterminate(double x, double y, double width, double height) {
        double progress = clamp(getSkinnable().getProgress(), 0, 1);
        boolean showActiveIndicator = progress > 0;
        double gap = showActiveIndicator ? TRACK_GAP : 0;
        double activeWidth = !showActiveIndicator
                ? 0
                : progress >= 1
                ? width
                : Math.min(width, Math.max(DETERMINATE_MIN_ACTIVE_WIDTH, progress * width));
        double trackX = x + activeWidth + gap;
        double trackWidth = Math.max(0, width - activeWidth - gap);

        layoutRegion(leadingTrack, 0, 0, 0, 0, false);
        layoutRegion(activeIndicator, x, y, activeWidth, height, showActiveIndicator && activeWidth > 0);

        boolean showTrack = progress < 1 && trackWidth > 0;
        boolean showStopIndicator = progress > 0 && showTrack;
        layoutRegion(trailingTrack, trackX, y, trackWidth, height, showTrack);

        double stopSize = Math.min(STOP_INDICATOR_SIZE, Math.min(trackWidth, height));
        double stopX = x + width - stopSize;
        layoutRegion(stopIndicator, stopX, y, stopSize, stopSize, showStopIndicator && stopSize > 0);
    }

    private void layoutIndeterminate(double x, double y, double width, double height) {
        double activeStart = indeterminateSegmentStartFactor.get() * width;
        double activeEnd = indeterminateSegmentEndFactor.get() * width;
        double visibleStart = clamp(activeStart, 0, width);
        double visibleEnd = clamp(activeEnd, 0, width);
        double visibleWidth = Math.max(0, visibleEnd - visibleStart);

        layoutRegion(stopIndicator, 0, 0, 0, 0, false);

        if (visibleWidth <= 0) {
            layoutRegion(activeIndicator, 0, 0, 0, 0, false);
            layoutRegion(leadingTrack, x, y, width, height, width > 0);
            layoutRegion(trailingTrack, 0, 0, 0, 0, false);
            return;
        }

        double leftTrackWidth = Math.max(0, visibleStart - TRACK_GAP);
        double rightTrackX = x + visibleEnd + TRACK_GAP;
        double rightTrackWidth = Math.max(0, width - visibleEnd - TRACK_GAP);

        layoutRegion(leadingTrack, x, y, leftTrackWidth, height, leftTrackWidth > 0);
        layoutRegion(activeIndicator, x + visibleStart, y, visibleWidth, height, true);
        layoutRegion(trailingTrack, rightTrackX, y, rightTrackWidth, height, rightTrackWidth > 0);
    }

    private void layoutRegion(Region region, double x, double y, double width, double height, boolean visible) {
        region.setVisible(visible);
        if (!visible) {
            return;
        }
        region.resizeRelocate(x, y, Math.max(0, width), Math.max(0, height));
    }

    private void updateAnimation() {
        if (!getSkinnable().isIndeterminate()) {
            clearAnimation();
            return;
        }

        if (indeterminateTransition == null) {
            createIndeterminateTimeline();
        }

        if (treeShowingProperty.get()) {
            indeterminateTransition.play();
        } else {
            indeterminateTransition.pause();
        }
    }

    private void updateProgress() {
        getSkinnable().requestLayout();
    }

    private void resetIndeterminateGeometry() {
        indeterminateSegmentStartFactor.set(INDETERMINATE_INITIAL_START_FACTOR);
        indeterminateSegmentEndFactor.set(INDETERMINATE_INITIAL_END_FACTOR);
    }

    void setIndeterminateSegmentForTesting(double startFactor, double endFactor) {
        indeterminateSegmentStartFactor.set(startFactor);
        indeterminateSegmentEndFactor.set(Math.max(startFactor, endFactor));
    }

    private void createIndeterminateTimeline() {
        clearAnimation();
        resetIndeterminateGeometry();

        indeterminateTransition = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(indeterminateSegmentStartFactor, 0.0, Interpolator.LINEAR),
                        new KeyValue(indeterminateSegmentEndFactor, 0.18, Interpolator.EASE_OUT)
                ),
                new KeyFrame(
                        Duration.seconds(0.42),
                        new KeyValue(indeterminateSegmentStartFactor, 0.0, Interpolator.EASE_BOTH),
                        new KeyValue(indeterminateSegmentEndFactor, 0.46, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                        Duration.seconds(0.95),
                        new KeyValue(indeterminateSegmentStartFactor, 0.14, Interpolator.EASE_BOTH),
                        new KeyValue(indeterminateSegmentEndFactor, 0.76, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(
                        Duration.seconds(1.42),
                        new KeyValue(indeterminateSegmentStartFactor, 0.44, Interpolator.EASE_BOTH),
                        new KeyValue(indeterminateSegmentEndFactor, 0.94, Interpolator.EASE_IN)
                ),
                new KeyFrame(
                        Duration.seconds(1.78),
                        new KeyValue(indeterminateSegmentStartFactor, 0.82, Interpolator.EASE_IN),
                        new KeyValue(indeterminateSegmentEndFactor, 1.0, Interpolator.EASE_IN)
                ),
                new KeyFrame(
                        Duration.seconds(2.0),
                        new KeyValue(indeterminateSegmentStartFactor, 1.02, Interpolator.EASE_IN),
                        new KeyValue(indeterminateSegmentEndFactor, 1.02, Interpolator.EASE_IN)
                )
        );
        indeterminateTransition.setCycleCount(Timeline.INDEFINITE);
    }

    private void clearAnimation() {
        if (indeterminateTransition == null) {
            return;
        }
        indeterminateTransition.stop();
        indeterminateTransition.getKeyFrames().clear();
        indeterminateTransition = null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void dispose() {
        super.dispose();
        treeShowingProperty.dispose();
        clearAnimation();
    }
}
