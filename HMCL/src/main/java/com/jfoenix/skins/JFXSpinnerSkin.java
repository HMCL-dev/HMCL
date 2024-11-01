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
import com.sun.javafx.scene.NodeHelper;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * JFXSpinner material design skin
 *
 * @author Shadi Shaheen & Gerard Moubarak
 * @version 1.0
 * @since 2017-09-25
 */
public class JFXSpinnerSkin extends SkinBase<JFXSpinner> {

    private static final Color GREEN_COLOR = Color.valueOf("#0F9D58");
    private static final Color RED_COLOR = Color.valueOf("#DB4437");
    private static final Color YELLOW_COLOR = Color.valueOf("#F4B400");
    private static final Color BLUE_COLOR = Color.valueOf("#4285F4");

    private JFXSpinner control;
    private boolean isValid = false;

    private Timeline timeline;
    private Arc arc;
    private Arc track;
    private final StackPane arcPane;
    private final Rectangle fillRect;
    private double arcLength = -1;
    private final Text text;

    public JFXSpinnerSkin(JFXSpinner control) {
        super(control);

        this.control = control;

        arc = new Arc();
        arc.setManaged(false);
        arc.setStartAngle(0);
        arc.setLength(180);
        arc.getStyleClass().setAll("arc");
        arc.setFill(Color.TRANSPARENT);
        arc.setStrokeWidth(3);

        track = new Arc();
        track.setManaged(false);
        track.setStartAngle(0);
        track.setLength(360);
        track.setStrokeWidth(3);
        track.getStyleClass().setAll("track");
        track.setFill(Color.TRANSPARENT);

        fillRect = new Rectangle();
        fillRect.setFill(Color.TRANSPARENT);
        text = new Text();
        text.getStyleClass().setAll("text", "percentage");
        final Group group = new Group(fillRect, track, arc, text);
        group.setManaged(false);
        arcPane = new StackPane(group);
        arcPane.setPrefSize(50, 50);
        getChildren().setAll(arcPane);

        // register listeners
        registerChangeListener(control.indeterminateProperty(), obs -> initialize());
        registerChangeListener(control.progressProperty(), obs -> updateProgress());
        registerChangeListener(NodeHelper.treeShowingProperty(control), obs->updateAnimation());
        registerChangeListener(control.sceneProperty(), obs->updateAnimation());
    }

    private void initialize() {
        if (getSkinnable().isIndeterminate()) {
            if (timeline == null) {
                createTransition();
                if (NodeHelper.isTreeShowing(getSkinnable())) {
                    timeline.play();
                }
            }
        } else {
            clearAnimation();
            arc.setStartAngle(90);
            updateProgress();
        }
    }

    private KeyFrame[] getKeyFrames(double angle, double duration, Paint color) {
        KeyFrame[] frames = new KeyFrame[4];
        frames[0] = new KeyFrame(Duration.seconds(duration),
            new KeyValue(arc.lengthProperty(), 5, Interpolator.LINEAR),
            new KeyValue(arc.startAngleProperty(),
                angle + 45 + control.getStartingAngle(),
                Interpolator.LINEAR));
        frames[1] = new KeyFrame(Duration.seconds(duration + 0.4),
            new KeyValue(arc.lengthProperty(), 250, Interpolator.LINEAR),
            new KeyValue(arc.startAngleProperty(),
                angle + 90 + control.getStartingAngle(),
                Interpolator.LINEAR));
        frames[2] = new KeyFrame(Duration.seconds(duration + 0.7),
            new KeyValue(arc.lengthProperty(), 250, Interpolator.LINEAR),
            new KeyValue(arc.startAngleProperty(),
                angle + 135 + control.getStartingAngle(),
                Interpolator.LINEAR));
        frames[3] = new KeyFrame(Duration.seconds(duration + 1.1),
            new KeyValue(arc.lengthProperty(), 5, Interpolator.LINEAR),
            new KeyValue(arc.startAngleProperty(),
                angle + 435 + control.getStartingAngle(),
                Interpolator.LINEAR),
            new KeyValue(arc.strokeProperty(), color, Interpolator.EASE_BOTH));
        return frames;
    }

    private void pauseTimeline(boolean pause) {
        if (getSkinnable().isIndeterminate()) {
            if (timeline == null) {
                createTransition();
            }
            if (pause) {
                timeline.pause();
            } else {
                timeline.play();
            }
        }
    }

    private void updateAnimation() {
        ProgressIndicator control = getSkinnable();
        final boolean isTreeShowing = NodeHelper.isTreeShowing(control) && control.getScene() != null;
        if (timeline != null) {
            pauseTimeline(!isTreeShowing);
        } else if (isTreeShowing) {
            createTransition();
        }
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (Region.USE_COMPUTED_SIZE == control.getRadius()) {
            return super.computeMaxHeight(width, topInset, rightInset, bottomInset, leftInset);
        } else {
            return control.getRadius() * 2 + arc.getStrokeWidth() * 2;
        }
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (Region.USE_COMPUTED_SIZE == control.getRadius()) {
            return super.computeMaxWidth(height, topInset, rightInset, bottomInset, leftInset);
        } else {
            return control.getRadius() * 2 + arc.getStrokeWidth() * 2;
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return arcPane.prefWidth(-1);
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return arcPane.prefHeight(-1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        final double strokeWidth = arc.getStrokeWidth();
        final double radius = Math.min(contentWidth, contentHeight) / 2 - strokeWidth / 2;
        final double arcSize = snapSize(radius * 2 + strokeWidth);

        arcPane.resizeRelocate((contentWidth - arcSize) / 2 + 1, (contentHeight - arcSize) / 2 + 1, arcSize, arcSize);
        updateArcLayout(radius, arcSize);

        fillRect.setWidth(arcSize);
        fillRect.setHeight(arcSize);

        if (!isValid) {
            initialize();
            isValid = true;
        }

        if (!getSkinnable().isIndeterminate()) {
            arc.setLength(arcLength);
            if (text.isVisible()) {
                final double progress = control.getProgress();
                int intProgress = (int) Math.round(progress * 100.0);
                Font font = text.getFont();
                text.setFont(Font.font(font.getFamily(), radius / 1.7));
                text.setText((progress > 1 ? 100 : intProgress) + "%");
                text.relocate((arcSize - text.getLayoutBounds().getWidth()) / 2, (arcSize - text.getLayoutBounds().getHeight()) / 2);
            }
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

    boolean wasIndeterminate = false;

    protected void updateProgress() {
        final ProgressIndicator control = getSkinnable();
        final boolean isIndeterminate = control.isIndeterminate();
        if (!(isIndeterminate && wasIndeterminate)) {
            arcLength = -360 * control.getProgress();
            control.requestLayout();
        }
        wasIndeterminate = isIndeterminate;
    }

    private void createTransition() {
        if(!getSkinnable().isIndeterminate()) return;
        final Paint initialColor = arc.getStroke();
        if (initialColor == null) {
            arc.setStroke(BLUE_COLOR);
        }

        KeyFrame[] blueFrame = getKeyFrames(0, 0, initialColor == null ? BLUE_COLOR : initialColor);
        KeyFrame[] redFrame = getKeyFrames(450, 1.4, initialColor == null ? RED_COLOR : initialColor);
        KeyFrame[] yellowFrame = getKeyFrames(900, 2.8, initialColor == null ? YELLOW_COLOR : initialColor);
        KeyFrame[] greenFrame = getKeyFrames(1350, 4.2, initialColor == null ? GREEN_COLOR : initialColor);

        KeyFrame endingFrame = new KeyFrame(Duration.seconds(5.6),
            new KeyValue(arc.lengthProperty(), 5, Interpolator.LINEAR),
            new KeyValue(arc.startAngleProperty(),
                1845 + control.getStartingAngle(),
                Interpolator.LINEAR));

        if (timeline != null) {
            timeline.stop();
            timeline.getKeyFrames().clear();
        }
        timeline = new Timeline(blueFrame[0],
            blueFrame[1],
            blueFrame[2],
            blueFrame[3],
            redFrame[0],
            redFrame[1],
            redFrame[2],
            redFrame[3],
            yellowFrame[0],
            yellowFrame[1],
            yellowFrame[2],
            yellowFrame[3],
            greenFrame[0],
            greenFrame[1],
            greenFrame[2],
            greenFrame[3],
            endingFrame);
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setDelay(Duration.ZERO);
        timeline.playFromStart();
    }

    private void clearAnimation() {
        if (timeline != null) {
            timeline.stop();
            timeline.getKeyFrames().clear();
            timeline = null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        clearAnimation();
        arc = null;
        track = null;
        control = null;
    }
}
