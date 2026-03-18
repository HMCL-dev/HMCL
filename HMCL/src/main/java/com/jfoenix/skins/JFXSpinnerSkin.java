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

import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.utils.TreeShowingProperty;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.List;

/// JFXSpinner material design skin
///
/// @author Shadi Shaheen & Gerard Moubarak
/// @version 1.0
/// @since 2017-09-25
public class JFXSpinnerSkin extends SkinBase<JFXSpinner> {

    private static final double DEFAULT_STROKE_WIDTH = 4;

    private JFXSpinner control;
    private final TreeShowingProperty treeShowingProperty;
    private boolean isValid = false;

    private Timeline timeline;
    private Arc arc;
    private Arc track;
    private final StackPane arcPane;
    private final Rectangle fillRect;

    private final double startingAngle;

    public JFXSpinnerSkin(JFXSpinner control) {
        super(control);

        this.control = control;
        this.treeShowingProperty = new TreeShowingProperty(control);
        this.startingAngle = control.getStartingAngle();

        arc = new Arc();
        arc.setManaged(false);
        arc.setLength(180);
        arc.getStyleClass().setAll("arc");
        arc.setFill(Color.TRANSPARENT);
        arc.setStrokeWidth(DEFAULT_STROKE_WIDTH);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);

        track = new Arc();
        track.setManaged(false);
        track.setLength(360);
        track.setStrokeWidth(DEFAULT_STROKE_WIDTH);
        track.getStyleClass().setAll("track");
        track.setFill(Color.TRANSPARENT);

        fillRect = new Rectangle();
        fillRect.setFill(Color.TRANSPARENT);
        final Group group = new Group(fillRect, track, arc);
        group.setManaged(false);
        arcPane = new StackPane(group);
        arcPane.setPrefSize(50, 50);
        getChildren().setAll(arcPane);

        // register listeners
        registerChangeListener(control.progressProperty(), obs -> updateProgress());
        registerChangeListener(treeShowingProperty, obs -> updateProgress());
    }

    private void updateProgress() {
        double progress = Double.min(getSkinnable().getProgress(), 1.0);
        if (progress < 0) { // indeterminate
            boolean treeShowing = treeShowingProperty.get();
            if (treeShowing) {
                if (timeline == null) {
                    timeline = createTransition();
                    timeline.playFromStart();
                } else {
                    timeline.play();
                }
            } else if (timeline != null) {
                timeline.pause();
            }
        } else { // determinate
            clearAnimation();
            arc.setStartAngle(90);
            arc.setLength(-360 * progress);
        }
    }

    private double computeSize() {
        return control.getRadius() * 2 + arc.getStrokeWidth() * 2;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (Region.USE_COMPUTED_SIZE == control.getRadius()) {
            return super.computeMaxHeight(width, topInset, rightInset, bottomInset, leftInset);
        } else {
            return computeSize();
        }
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (Region.USE_COMPUTED_SIZE == control.getRadius()) {
            return super.computeMaxWidth(height, topInset, rightInset, bottomInset, leftInset);
        } else {
            return computeSize();
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (Region.USE_COMPUTED_SIZE == control.getRadius()) {
            return arcPane.prefWidth(-1);
        } else {
            return computeSize();
        }
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (Region.USE_COMPUTED_SIZE == control.getRadius()) {
            return arcPane.prefHeight(-1);
        } else {
            return computeSize();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        final double strokeWidth = arc.getStrokeWidth();
        double radius = control.getRadius();
        final double arcSize = snapSizeX(radius * 2 + strokeWidth);

        arcPane.resizeRelocate((contentWidth - arcSize) / 2 + 1, (contentHeight - arcSize) / 2 + 1, arcSize, arcSize);
        updateArcLayout(radius, arcSize);

        fillRect.setWidth(arcSize);
        fillRect.setHeight(arcSize);

        if (!isValid) {
            updateProgress();
            isValid = true;
        }
    }

    private void updateArcLayout(double radius, double arcSize) {
        arc.setRadiusX(radius);
        arc.setRadiusY(radius);
        arc.setCenterX(arcSize / 2);
        arc.setCenterY(arcSize / 2);

        track.setRadiusX(radius);
        track.setRadiusY(radius);
        track.setCenterX(arcSize / 2);
        track.setCenterY(arcSize / 2);
        track.setStrokeWidth(arc.getStrokeWidth());
    }

    private void addKeyFrames(List<KeyFrame> frames, double angle, double duration) {
        frames.add(new KeyFrame(Duration.seconds(duration),
                new KeyValue(arc.lengthProperty(), 5, Interpolator.LINEAR),
                new KeyValue(arc.startAngleProperty(),
                        angle + 45 + startingAngle,
                        Interpolator.LINEAR)));
        frames.add(new KeyFrame(Duration.seconds(duration + 0.4),
                new KeyValue(arc.lengthProperty(), 250, Interpolator.LINEAR),
                new KeyValue(arc.startAngleProperty(),
                        angle + 90 + startingAngle,
                        Interpolator.LINEAR)));
        frames.add(new KeyFrame(Duration.seconds(duration + 0.7),
                new KeyValue(arc.lengthProperty(), 250, Interpolator.LINEAR),
                new KeyValue(arc.startAngleProperty(),
                        angle + 135 + startingAngle,
                        Interpolator.LINEAR)));
        frames.add(new KeyFrame(Duration.seconds(duration + 1.1),
                new KeyValue(arc.lengthProperty(), 5, Interpolator.LINEAR),
                new KeyValue(arc.startAngleProperty(),
                        angle + 435 + startingAngle,
                        Interpolator.LINEAR)));
    }

    private Timeline createTransition() {
        if (AnimationUtils.isAnimationEnabled()) {
            var keyFrames = new ArrayList<KeyFrame>(17);
            addKeyFrames(keyFrames, 0, 0);
            addKeyFrames(keyFrames, 450, 1.4);
            addKeyFrames(keyFrames, 900, 2.8);
            addKeyFrames(keyFrames, 1350, 4.2);
            keyFrames.add(new KeyFrame(Duration.seconds(5.6),
                    new KeyValue(arc.lengthProperty(), 5, Interpolator.LINEAR),
                    new KeyValue(arc.startAngleProperty(),
                            1845 + startingAngle,
                            Interpolator.LINEAR)));

            timeline = new Timeline();
            timeline.getKeyFrames().setAll(keyFrames);
        } else {
            timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(arc.startAngleProperty(), 45 + startingAngle, Interpolator.LINEAR),
                            new KeyValue(arc.lengthProperty(), 120, Interpolator.LINEAR)),
                    new KeyFrame(Duration.seconds(1.2),
                            new KeyValue(arc.startAngleProperty(), 45 + 360 + startingAngle, Interpolator.LINEAR))
            );
        }

        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    private void clearAnimation() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        treeShowingProperty.dispose();
        clearAnimation();
        arc = null;
        track = null;
        control = null;
    }
}
